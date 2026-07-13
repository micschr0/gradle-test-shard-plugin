package de.micschro.shardwise

import de.micschro.shardwise.internal.NodeEnv
import de.micschro.shardwise.internal.NodeEnvValueSource
import de.micschro.shardwise.internal.PlanDump
import de.micschro.shardwise.internal.PlanRenderer
import de.micschro.shardwise.internal.ShardBuildService
import de.micschro.shardwise.internal.TestWeights
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.testing.Test

/**
 * Root plugin that shards a multi-module build's [Test] tasks across parallel CI nodes
 * (`CI_NODE_INDEX`/`CI_NODE_TOTAL`, 1-based) via Greedy-LPT bin-packing. Configure through
 * the [ShardwiseExtension] (`shardwise { }`). See the README for usage and guarantees.
 *
 * @since 0.1.0
 */
public class ShardwisePlugin : Plugin<Project> {

    private val log = Logging.getLogger(ShardwisePlugin::class.java)

    override fun apply(project: Project) {
        require(project == project.rootProject) {
            "de.micschro.shardwise must be applied to the root project"
        }

        val ext = project.extensions.create("shardwise", ShardwiseExtension::class.java)
        ext.defaultWeight.convention(TestWeights.DEFAULT_WEIGHT)
        ext.taskNames.convention(setOf("test"))
        ext.planDetail.convention(PlanDetail.FULL)

        val nodeEnv = project.providers.of(NodeEnvValueSource::class.java) {}
        val nodeI = nodeEnv.map(NodeEnv::index)
        val nodeT = nodeEnv.map(NodeEnv::total)

        val taskModulePaths = project.provider {
            ext.taskNames.get().associateWith { taskName ->
                project.allprojects
                    .filter { taskName in it.tasks.withType(Test::class.java).names }
                    .map { it.shardPath }
            }
        }

        val weightsText = ext.weightsFile.map { readTextOrEmpty(it.asFile) }
            .orElse("")

        val service = project.gradle.sharedServices.registerIfAbsent(
            "de.micschro.shardwise.plan", ShardBuildService::class.java
        ) {
            it.parameters.nodeIndex.set(nodeI)
            it.parameters.nodeTotal.set(nodeT)
            it.parameters.defaultWeight.set(ext.defaultWeight)
            it.parameters.weightsText.set(weightsText)
            it.parameters.taskModulePaths.set(taskModulePaths)
        }

        val taskNames = ext.taskNames

        project.gradle.taskGraph.whenReady { graph ->
            warnOnLifecycleTasks(project, taskNames, graph)
            dumpPlans(taskNames, service)
            logPlan(ext, taskNames, nodeI, nodeT, service)
        }

        project.allprojects { p ->
            val modulePath = p.shardPath
            p.tasks.withType(Test::class.java).configureEach { test ->
                val taskName = test.name
                test.usesService(service)
                test.onlyIf("Shardwise node ${nodeI.get()}/${nodeT.get()}") {
                    val inList = taskName in taskNames.get()
                    if (!inList) return@onlyIf true
                    val runs = service.get().runsOnThisNode(taskName, modulePath)
                    val decision = if (runs) "RUN" else "skip (assigned elsewhere)"
                    log.debug("Shardwise decision: $taskName:$modulePath -> $decision")
                    runs
                }
            }
        }
    }

    /**
     * Lifecycle tasks (`build`, `check`, …) are empty containers — `onlyIf` skips only the
     * container while its `dependsOn` work still runs on every node. Checked against the
     * task graph so tasks registered after plugin apply are covered too.
     */
    private fun warnOnLifecycleTasks(project: Project, taskNames: SetProperty<String>, graph: TaskExecutionGraph) {
        val sharded = taskNames.get()
        graph.allTasks.asSequence()
            .filter { it.name in sharded && it.actions.isEmpty() }
            .map { it.name }
            .distinct()
            .forEach { name ->
                project.logger.warn(
                    "Shardwise: taskName '{}' is a lifecycle task — sharding skips the container, " +
                        "but its dependsOn work still runs on every node",
                    name
                )
            }
    }

    /**
     * Writes the plan this node derived to the file named by `shardwise.planDump`,
     * if that system property is set. Used by the e2e pipeline to prove all nodes
     * agree on the plan and that each runs its own share — neither of which can be
     * seen by counting task outcomes. Off unless explicitly asked for.
     */
    private fun dumpPlans(taskNames: SetProperty<String>, service: Provider<ShardBuildService>) {
        try {
            val target = System.getProperty("shardwise.planDump") ?: return
            val svc = service.get()
            val dump = taskNames.get().sorted()
                .mapNotNull { svc.planFor(it) }
                .joinToString("\n") { PlanDump.render(it) }
            java.io.File(target).apply {
                parentFile?.mkdirs()
                writeText(dump)
            }
        } catch (e: Exception) {
            log.warn("Shardwise: failed to dump plan", e)
        }
    }

    private fun logPlan(
        ext: ShardwiseExtension,
        taskNames: SetProperty<String>,
        nodeI: Provider<Int>,
        nodeT: Provider<Int>,
        service: Provider<ShardBuildService>,
    ) {
        try {
            val detail = ext.planDetail.get()
            if (detail == PlanDetail.OFF || nodeT.get() <= 1) return

            // Colour only when stdout is a terminal — CI log viewers render escape codes as garbage.
            val renderer = PlanRenderer(ansi = System.console() != null)
            val svc = service.get()
            val nodeIndex = nodeI.get()

            taskNames.get().sorted().forEach { taskName ->
                val plan = svc.planFor(taskName) ?: return@forEach
                val lines = renderer.render(
                    taskName = taskName,
                    nodeIndex = nodeIndex,
                    plan = plan,
                    modules = svc.modulesFor(taskName),
                    detail = detail,
                )
                if (lines.isEmpty()) return@forEach
                log.lifecycle("")
                lines.forEach(log::lifecycle)
                log.lifecycle("")
            }
        } catch (e: Exception) {
            log.warn("Shardwise: failed to log plan", e)
        }
    }
}

private val Project.shardPath: String
    get() = path.removePrefix(":").replace(':', '/').ifEmpty { "." }

/** Missing or unreadable weights file ⇒ "" ⇒ default weights (coverage beats balance). */
private fun readTextOrEmpty(file: java.io.File): String = try {
    file.readText()
} catch (ignored: java.io.IOException) {
    ""
}
