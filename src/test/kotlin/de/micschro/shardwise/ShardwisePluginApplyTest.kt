package de.micschro.shardwise

import de.micschro.shardwise.internal.NodeEnvValueSource
import de.micschro.shardwise.internal.ShardNodeEnvService
import de.micschro.shardwise.internal.ShardPlannerService
import de.micschro.shardwise.internal.TestWeights
import de.micschro.shardwise.PlanDetail
import org.gradle.api.Project
import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

/**
 * ProjectBuilder tests for the glue code in [ShardwisePlugin.apply] — cheaper than TestKit,
 * covering apply validation, shardPath mapping, and extension defaults.
 */
class ShardwisePluginApplyTest {

    private fun root(): Project = ProjectBuilder.builder().build()

    private fun child(parent: Project, name: String): Project =
        ProjectBuilder.builder().withParent(parent).withName(name).build()

    @Test
    fun `applying to a subproject fails naming the root project requirement`() {
        val root = root()
        val sub = child(root, "mod")
        val ex = assertThrows<Exception> { sub.pluginManager.apply(ShardwisePlugin::class.java) }
        val messages = generateSequence<Throwable>(ex) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(
            messages.any { "must be applied to the root project" in it },
            "error must explain the root-project requirement, was: $messages"
        )
    }

    @Test
    fun `extension defaults are test task and default weight`() {
        val root = root()
        root.pluginManager.apply(ShardwisePlugin::class.java)
        val ext = root.extensions.getByType(ShardwiseExtension::class.java)
        assertEquals(TestWeights.DEFAULT_WEIGHT, ext.defaultWeight.get())
        assertEquals(setOf("test"), ext.taskNames.get())
        assertEquals(PlanDetail.FULL, ext.planDetail.get())
        assertTrue(!ext.weightsFile.isPresent, "weights file must be optional")
    }

    @Test
    fun `shardPath maps nested project paths to slashes and root to dot`() {
        val root = root()
        val a = child(root, "a")
        val b = child(a, "b")
        root.pluginManager.apply(ShardwisePlugin::class.java)
        // java applied only AFTER the plugin: plan discovery must stay lazy
        root.pluginManager.apply("java")
        b.pluginManager.apply("java")

        val params = root.gradle.sharedServices.registrations
            .getByName("de.micschro.shardwise.planner").parameters as ShardPlannerService.Params
        val paths = params.taskModulePaths.get().getValue("test")
        assertEquals(setOf(".", "a/b"), paths.toSet(), "root → '.', :a:b → 'a/b', :a has no test task")
    }

    @Test
    fun `taskName without any matching module maps to an empty module list`() {
        val root = root()
        root.pluginManager.apply(ShardwisePlugin::class.java)
        root.extensions.getByType(ShardwiseExtension::class.java)
            .taskNames.set(setOf("integrtionTest")) // typo scenario
        val params = root.gradle.sharedServices.registrations
            .getByName("de.micschro.shardwise.planner").parameters as ShardPlannerService.Params
        assertEquals(mapOf("integrtionTest" to emptyList<String>()), params.taskModulePaths.get())
    }

    @Test
    fun `lifecycle task in taskNames does not crash the build`() {
        val root = root()
        root.pluginManager.apply(ShardwisePlugin::class.java)
        root.extensions.getByType(ShardwiseExtension::class.java)
            .taskNames.set(setOf("build"))
        val params = root.gradle.sharedServices.registrations
            .getByName("de.micschro.shardwise.planner").parameters as ShardPlannerService.Params
        // Lifecycle tasks like "build" have no Test tasks beneath them in subprojects.
        // The plugin maps them to empty modules — same as any unmatched task name.
        assertEquals(mapOf("build" to emptyList<String>()), params.taskModulePaths.get())
    }

    @Test
    fun `planner service plans every module and answers null for unknown tasks`() {
        val root = root()
        val a = child(root, "a")
        root.pluginManager.apply(ShardwisePlugin::class.java)
        a.pluginManager.apply("java")

        val svc = root.gradle.sharedServices.registrations
            .getByName("de.micschro.shardwise.planner").service.get() as ShardPlannerService
        val plan = svc.planFor("test")
        assertTrue(plan != null, "the configured task name must have a plan")
        assertEquals(
            setOf("a"), plan!!.assignments.values.flatten().toSet(),
            "every test module must be assigned"
        )
        assertNull(svc.planFor("nope"), "unknown task names have no plan — coverage beats balance")
        assertEquals(emptyList<String>(), svc.modulesFor("nope"))
        assertEquals(listOf("a"), svc.modulesFor("test"))
    }

    @Test
    fun `node env service and value source resolve to a valid one-based node`() {
        val root = root()
        root.pluginManager.apply(ShardwisePlugin::class.java)

        val env = root.providers.of(NodeEnvValueSource::class.java) {}.get()
        assertTrue(env.index in 1..env.total, "node index must be 1-based and within total: $env")

        val svc = root.gradle.sharedServices.registrations
            .getByName("de.micschro.shardwise.nodeEnv").service.get() as ShardNodeEnvService
        assertEquals(env.index, svc.parameters.nodeIndex.get())
    }

    @Test
    fun `weights file content reaches the planner and a missing file degrades to empty`() {
        val root = root()
        root.pluginManager.apply(ShardwisePlugin::class.java)
        val ext = root.extensions.getByType(ShardwiseExtension::class.java)
        val params = root.gradle.sharedServices.registrations
            .getByName("de.micschro.shardwise.planner").parameters as ShardPlannerService.Params

        val weights = File(root.projectDir, "w.properties").apply { writeText("mod-a=5") }
        ext.weightsFile.set(weights)
        assertEquals("mod-a=5", params.weightsText.get())

        ext.weightsFile.set(File(root.projectDir, "missing.properties"))
        assertEquals("", params.weightsText.get(), "unreadable weights must degrade to defaults, not fail")
    }

    @Test
    fun `onlyIf runs a sharded task on a single node and ignores unlisted test tasks`() {
        val root = root()
        root.pluginManager.apply(ShardwisePlugin::class.java)
        root.pluginManager.apply("java")

        // Env unset ⇒ nodeTotal == 1 ⇒ the sharded task always runs.
        val test = root.tasks.withType(TestTask::class.java).getByName("test")
        assertTrue(
            (test as TaskInternal).onlyIf.isSatisfiedBy(test),
            "a single-node build must never skip"
        )

        // A Test task outside taskNames is not shardwise's business — always runs.
        val extra = root.tasks.register("extraTest", TestTask::class.java).get()
        assertTrue((extra as TaskInternal).onlyIf.isSatisfiedBy(extra))
    }

    @Test
    fun `generateTestWeights is registered on the root project`() {
        val root = root()
        root.pluginManager.apply(ShardwisePlugin::class.java)
        val task = root.tasks.findByName("generateTestWeights")
        assertTrue(task != null, "expected 'generateTestWeights' task on root project")
        assertEquals("Shardwise", task!!.group)
    }
}
