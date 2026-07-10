package de.micschro.shardwise

import de.micschro.shardwise.internal.NodeEnv
import de.micschro.shardwise.internal.NodeEnvValueSource
import de.micschro.shardwise.internal.ShardBuildService
import de.micschro.shardwise.internal.TestWeights
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Root plugin that shards a multi-module build's [Test] tasks across parallel CI nodes
 * (`CI_NODE_INDEX`/`CI_NODE_TOTAL`, 1-based) via Greedy-LPT bin-packing. Configure through
 * the [ShardwiseExtension] (`shardwise { }`). See the README for usage and guarantees.
 */
public class ShardwisePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        require(project == project.rootProject) {
            "de.micschro.shardwise must be applied to the root project"
        }

        val ext = project.extensions.create("shardwise", ShardwiseExtension::class.java)
        ext.defaultWeight.convention(TestWeights.DEFAULT_WEIGHT)
        ext.taskNames.convention(setOf("test"))

        val nodeEnv = project.providers.of(NodeEnvValueSource::class.java) {}

        val taskModulePaths = project.provider {
            ext.taskNames.get().associateWith { taskName ->
                project.allprojects
                    .filter { taskName in it.tasks.withType(Test::class.java).names }
                    .map { it.shardPath }
            }
        }

        val weightsText = ext.weightsFile.map { it.asFile.takeIf { f -> f.exists() }?.readText() ?: "" }
            .orElse("")

        val service = project.gradle.sharedServices.registerIfAbsent(
            "de.micschro.shardwise.plan", ShardBuildService::class.java
        ) {
            it.parameters.nodeIndex.set(nodeEnv.map(NodeEnv::index))
            it.parameters.nodeTotal.set(nodeEnv.map(NodeEnv::total))
            it.parameters.defaultWeight.set(ext.defaultWeight)
            it.parameters.weightsText.set(weightsText)
            it.parameters.taskModulePaths.set(taskModulePaths)
        }

        val taskNames = ext.taskNames
        project.allprojects { p ->
            val modulePath = p.shardPath
            p.tasks.withType(Test::class.java).configureEach { test ->
                val taskName = test.name
                test.usesService(service)
                test.onlyIf("assigned to this shard") {
                    taskName !in taskNames.get() || service.get().runsOnThisNode(taskName, modulePath)
                }
            }
        }
    }
}

/** Module identity shared with the weights-file keys: project path, ':'→'/', root = ".". */
private val Project.shardPath: String
    get() = path.removePrefix(":").replace(':', '/').ifEmpty { "." }
