package de.micschro.shardwise

import de.micschro.shardwise.internal.ShardBuildService
import de.micschro.shardwise.internal.TestWeights
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
            .getByName("de.micschro.shardwise.plan").parameters as ShardBuildService.Params
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
            .getByName("de.micschro.shardwise.plan").parameters as ShardBuildService.Params
        assertEquals(mapOf("integrtionTest" to emptyList<String>()), params.taskModulePaths.get())
    }

    @Test
    fun `lifecycle task in taskNames does not crash the build`() {
        val root = root()
        root.pluginManager.apply(ShardwisePlugin::class.java)
        root.extensions.getByType(ShardwiseExtension::class.java)
            .taskNames.set(setOf("build"))
        val params = root.gradle.sharedServices.registrations
            .getByName("de.micschro.shardwise.plan").parameters as ShardBuildService.Params
        // Lifecycle tasks like "build" have no Test tasks beneath them in subprojects.
        // The plugin maps them to empty modules — same as any unmatched task name.
        assertEquals(mapOf("build" to emptyList<String>()), params.taskModulePaths.get())
    }
}
