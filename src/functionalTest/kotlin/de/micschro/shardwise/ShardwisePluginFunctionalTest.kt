package de.micschro.shardwise

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class ShardwisePluginFunctionalTest {

    @field:TempDir lateinit var projectDir: File

    private val modules = listOf("mod-a", "mod-b", "mod-c", "mod-d")

    private fun writeExampleProject(
        withIntegrationTests: Boolean = false,
        rootHasJava: Boolean = false,
        shardwiseConfig: String = "shardwise { }"
    ) {
        projectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"example\"\n" +
                modules.joinToString("\n") { "include(\"$it\")" }
        )
        val rootPlugins = if (rootHasJava) "java\n                id(\"de.micschro.shardwise\")" else "id(\"de.micschro.shardwise\")"
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                $rootPlugins
            }
            $shardwiseConfig
            """.trimIndent()
        )
        modules.forEach { m ->
            val dir = projectDir.resolve(m).apply { mkdirs() }
            // the 'java' plugin provides a no-op test task without test sources
            val integrationTask = if (withIntegrationTests) {
                """
                tasks.register<Test>("integrationTest") {
                    testClassesDirs = files()
                    classpath = files()
                }
                """.trimIndent()
            } else {
                ""
            }
            dir.resolve("build.gradle.kts").writeText("plugins { java }\n$integrationTask\n")
        }
    }

    private fun runner(
        nodeIndex: Int,
        nodeTotal: Int,
        tasks: List<String> = listOf("test"),
        gradleVersion: String? = null
    ): GradleRunner = runnerWithEnv(
        System.getenv() + mapOf(
            "CI_NODE_INDEX" to "$nodeIndex",
            "CI_NODE_TOTAL" to "$nodeTotal"
        ),
        tasks, gradleVersion
    )

    private fun runnerWithEnv(
        env: Map<String, String>,
        tasks: List<String> = listOf("test"),
        gradleVersion: String? = null
    ): GradleRunner {
        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(tasks + "--configuration-cache")
            .withEnvironment(env)
            .forwardOutput()
        return if (gradleVersion != null) runner.withGradleVersion(gradleVersion) else runner
    }

    private fun outcomeOf(result: org.gradle.testkit.runner.BuildResult, taskPath: String): TaskOutcome {
        val outcome = result.task(taskPath)?.outcome
        assertNotNull(outcome, "$taskPath must be in the task graph")
        return outcome!!
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3])
    fun `each module runs on exactly one node and CC is green`(nodeIndex: Int) {
        writeExampleProject()
        val result = runner(nodeIndex, 3).build()
        assertTrue(
            result.output.contains("Configuration cache entry stored") ||
                result.output.contains("Reusing configuration cache"),
            "configuration cache must engage"
        )
        modules.forEach { m -> outcomeOf(result, ":$m:test") }
    }

    @Test
    fun `all nodes together run every module exactly once`() {
        writeExampleProject()
        val ranOn = mutableMapOf<String, MutableList<Int>>()
        (1..3).forEach { nodeIndex ->
            val result = runner(nodeIndex, 3).build()
            modules.forEach { m ->
                if (outcomeOf(result, ":$m:test") != TaskOutcome.SKIPPED) {
                    ranOn.getOrPut(m) { mutableListOf() }.add(nodeIndex)
                }
            }
        }
        modules.forEach { m ->
            assertEquals(1, ranOn[m]?.size ?: 0, "$m must run on exactly one node, ran on ${ranOn[m]}")
        }
    }

    @Test
    fun `integration test tasks are sharded independently and completely`() {
        writeExampleProject(
            withIntegrationTests = true,
            shardwiseConfig = """shardwise { taskNames.set(setOf("test", "integrationTest")) }"""
        )
        val taskNames = listOf("test", "integrationTest")
        val ranOn = mutableMapOf<String, MutableList<Int>>()
        (1..3).forEach { nodeIndex ->
            val result = runner(nodeIndex, 3, tasks = taskNames).build()
            modules.forEach { m ->
                taskNames.forEach { t ->
                    if (outcomeOf(result, ":$m:$t") != TaskOutcome.SKIPPED) {
                        ranOn.getOrPut("$m:$t") { mutableListOf() }.add(nodeIndex)
                    }
                }
            }
        }
        modules.forEach { m ->
            taskNames.forEach { t ->
                assertEquals(1, ranOn["$m:$t"]?.size ?: 0, "$m:$t must run on exactly one node, ran on ${ranOn["$m:$t"]}")
            }
        }
    }

    @Test
    fun `task type absent from taskNames is never skipped`() {
        writeExampleProject(withIntegrationTests = true)
        val result = runner(2, 3, tasks = listOf("integrationTest")).build()
        modules.forEach { m ->
            assertNotEquals(
                TaskOutcome.SKIPPED, outcomeOf(result, ":$m:integrationTest"),
                ":$m:integrationTest must run everywhere when not in taskNames"
            )
        }
    }

    @Test
    fun `root project test task is sharded like any module`() {
        writeExampleProject(rootHasJava = true)
        val ranOn = mutableListOf<Int>()
        (1..3).forEach { nodeIndex ->
            val result = runner(nodeIndex, 3).build()
            if (outcomeOf(result, ":test") != TaskOutcome.SKIPPED) ranOn.add(nodeIndex)
        }
        assertEquals(1, ranOn.size, "root :test must run on exactly one node, ran on $ranOn")
    }

    @Test
    fun `weights file changes take effect across configuration cache runs`() {
        writeExampleProject(
            shardwiseConfig = """shardwise { weightsFile.set(layout.projectDirectory.file("test-weights.properties")) }"""
        )
        val weights = projectDir.resolve("test-weights.properties")

        weights.writeText("mod-a=100\n")
        val first = runner(1, 2).build()
        assertNotEquals(TaskOutcome.SKIPPED, outcomeOf(first, ":mod-a:test"), "heavy mod-a must run on node 1")
        assertEquals(TaskOutcome.SKIPPED, outcomeOf(first, ":mod-b:test"))

        weights.writeText("mod-b=100\n")
        val second = runner(1, 2).build()
        assertNotEquals(
            TaskOutcome.SKIPPED, outcomeOf(second, ":mod-b:test"),
            "changed weights must reach the plan even with a stored configuration-cache entry"
        )
        assertEquals(TaskOutcome.SKIPPED, outcomeOf(second, ":mod-a:test"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["8.5", "8.14.3", "9.6.1"])
    fun `works across supported Gradle versions`(gradleVersion: String) {
        writeExampleProject()
        val result = runner(1, 3, gradleVersion = gradleVersion).build()
        assertTrue(
            result.output.contains("Configuration cache entry stored") ||
                result.output.contains("Reusing configuration cache"),
            "configuration cache must engage on Gradle $gradleVersion"
        )
        val outcomes = modules.associateWith { m -> outcomeOf(result, ":$m:test") }
        assertTrue(outcomes.values.any { it == TaskOutcome.SKIPPED }, "node 1/3 must skip foreign modules")
        assertTrue(outcomes.values.any { it != TaskOutcome.SKIPPED }, "node 1/3 must run its own modules")
    }

    @Test
    fun `N equals 1 skips nothing`() {
        writeExampleProject()
        val result = runner(1, 1).build()
        modules.forEach { m ->
            assertNotEquals(
                TaskOutcome.SKIPPED, outcomeOf(result, ":$m:test"),
                ":$m:test must not be skipped when N=1"
            )
        }
    }

    @Test
    fun `invalid node index fails the build instead of missharding`() {
        writeExampleProject()
        val result = runner(0, 3).buildAndFail()
        assertTrue(result.output.contains("CI_NODE_INDEX"), "failure must name the offending variable")
    }

    @Test
    fun `jvm test suites registered via the testing DSL are sharded completely`() {
        // testing { suites { ... } } registers Test tasks lazily — the plan must still capture them
        projectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"example\"\n" +
                modules.joinToString("\n") { "include(\"$it\")" }
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("de.micschro.shardwise")
            }
            shardwise { taskNames.set(setOf("test", "integrationTest")) }
            """.trimIndent()
        )
        modules.forEach { m ->
            val dir = projectDir.resolve(m).apply { mkdirs() }
            dir.resolve("build.gradle.kts").writeText(
                """
                import org.gradle.api.plugins.jvm.JvmTestSuite

                plugins { java }
                testing {
                    suites {
                        register<JvmTestSuite>("integrationTest") {
                            targets.all {
                                testTask.configure {
                                    testClassesDirs = files()
                                    classpath = files()
                                }
                            }
                        }
                    }
                }
                """.trimIndent()
            )
        }
        val ranOn = mutableMapOf<String, MutableList<Int>>()
        (1..3).forEach { nodeIndex ->
            val result = runner(nodeIndex, 3, tasks = listOf("test", "integrationTest")).build()
            modules.forEach { m ->
                if (outcomeOf(result, ":$m:integrationTest") != TaskOutcome.SKIPPED) {
                    ranOn.getOrPut(m) { mutableListOf() }.add(nodeIndex)
                }
            }
        }
        modules.forEach { m ->
            assertEquals(
                1, ranOn[m]?.size ?: 0,
                ":$m:integrationTest must run on exactly one node (not duplicated), ran on ${ranOn[m]}"
            )
        }
    }

    @Test
    fun `groovy dsl consumer shards like the kotlin dsl`() {
        projectDir.resolve("settings.gradle").writeText(
            "rootProject.name = 'example'\n" +
                modules.joinToString("\n") { "include '$it'" }
        )
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id 'de.micschro.shardwise'
            }
            shardwise { taskNames = ['test'] }
            """.trimIndent()
        )
        modules.forEach { m ->
            val dir = projectDir.resolve(m).apply { mkdirs() }
            dir.resolve("build.gradle").writeText("plugins { id 'java' }\n")
        }
        val result = runner(1, 3).build()
        assertTrue(
            result.output.contains("Configuration cache entry stored") ||
                result.output.contains("Reusing configuration cache"),
            "configuration cache must engage for a Groovy DSL consumer"
        )
        val outcomes = modules.associateWith { m -> outcomeOf(result, ":$m:test") }
        assertTrue(outcomes.values.any { it == TaskOutcome.SKIPPED }, "node 1/3 must skip foreign modules")
        assertTrue(outcomes.values.any { it != TaskOutcome.SKIPPED }, "node 1/3 must run its own modules")
    }

    @Test
    fun `without CI env nothing is skipped`() {
        writeExampleProject()
        val env = System.getenv().filterKeys { it != "CI_NODE_INDEX" && it != "CI_NODE_TOTAL" }
        val result = runnerWithEnv(env).build()
        modules.forEach { m ->
            assertNotEquals(
                TaskOutcome.SKIPPED, outcomeOf(result, ":$m:test"),
                ":$m:test must not be skipped in a local run without CI env"
            )
        }
    }

    @Test
    fun `empty env strings fail fast instead of missharding`() {
        // shell templating often exports empty strings instead of leaving the variables unset
        writeExampleProject()
        val env = System.getenv() + mapOf("CI_NODE_INDEX" to "", "CI_NODE_TOTAL" to "")
        val result = runnerWithEnv(env).buildAndFail()
        assertTrue(
            result.output.contains("CI_NODE_INDEX") || result.output.contains("CI_NODE_TOTAL"),
            "failure must name the offending variable"
        )
    }
}
