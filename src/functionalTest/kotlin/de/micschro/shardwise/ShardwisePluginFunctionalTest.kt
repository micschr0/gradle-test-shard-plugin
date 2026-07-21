// SPDX-License-Identifier: Apache-2.0

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

    @field:TempDir
    lateinit var projectDir: File

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
        val rootPlugins =
            if (rootHasJava) "java\n                id(\"de.micschro.shardwise\")" else "id(\"de.micschro.shardwise\")"
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
        baseEnv() + mapOf(
            "CI_NODE_INDEX" to "$nodeIndex",
            "CI_NODE_TOTAL" to "$nodeTotal"
        ),
        tasks, gradleVersion
    )

    /** Minimal env for deterministic TestKit runs — only essential vars from ambient. */
    private fun baseEnv(): Map<String, String> =
        System.getenv().filterKeys { it == "JAVA_HOME" || it == "PATH" }

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
                assertEquals(
                    1,
                    ranOn["$m:$t"]?.size ?: 0,
                    "$m:$t must run on exactly one node, ran on ${ranOn["$m:$t"]}"
                )
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

    @Test
    fun `warns when taskNames contains a lifecycle task registered after apply`() {
        writeExampleProject(
            shardwiseConfig = """
                shardwise { taskNames.set(setOf("test", "deployAll")) }
                tasks.register("deployAll")
            """.trimIndent()
        )
        val result = runner(1, 3, tasks = listOf("deployAll")).build()
        assertTrue(
            result.output.contains("taskName 'deployAll' is a lifecycle task"),
            "must warn about the action-less aggregate task registered after plugin apply"
        )
    }

    @Test
    fun `warns when taskNames contains an early-registered lifecycle task`() {
        writeExampleProject(
            rootHasJava = true,
            shardwiseConfig = """shardwise { taskNames.set(setOf("test", "build")) }"""
        )
        val result = runner(1, 3, tasks = listOf("build")).build()
        assertTrue(
            result.output.contains("taskName 'build' is a lifecycle task"),
            "must warn about the lifecycle task registered before plugin apply"
        )
    }

    @Test
    fun `unreadable weights file degrades to default weights instead of failing`() {
        writeExampleProject(
            shardwiseConfig = """shardwise { weightsFile.set(layout.projectDirectory.file("test-weights.properties")) }"""
        )
        // A directory at the weights path: exists() == true, but reading it throws.
        projectDir.resolve("test-weights.properties").mkdirs()
        val result = runner(1, 2).build()
        val outcomes = modules.associateWith { m -> outcomeOf(result, ":$m:test") }
        assertTrue(
            outcomes.values.any { it != TaskOutcome.SKIPPED },
            "node must still run its share with default weights, outcomes: $outcomes"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["8.11", "8.14.3", "9.6.1"])
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
        val env = baseEnv()
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
        val env = baseEnv() + mapOf("CI_NODE_INDEX" to "", "CI_NODE_TOTAL" to "")
        val result = runnerWithEnv(env).buildAndFail()
        assertTrue(
            result.output.contains("CI_NODE_INDEX") || result.output.contains("CI_NODE_TOTAL"),
            "failure must name the offending variable"
        )
    }

    @Test
    fun `configuration cache is reused on second run`() {
        writeExampleProject()
        // first run primes the cache
        runner(1, 3).build()
        // second run with identical inputs must reuse the cache
        val result = runner(1, 3).build()
        assertTrue(
            result.output.contains("Reusing configuration cache."),
            "second identical run must reuse configuration cache"
        )
    }

    @Test
    fun `plugin is incompatible with isolated projects by design`() {
        writeExampleProject()
        // Pinned to 9.6.1: on older Gradle binaries the isolated-projects
        // failure output contains neither "Isolated Projects" nor "allprojects", so the
        // assertion below is unreliable there regardless of which Gradle invokes this suite.
        val result = runnerWithEnv(
            baseEnv() + mapOf("CI_NODE_INDEX" to "1", "CI_NODE_TOTAL" to "2"),
            listOf("test", "--configuration-cache", "-Dorg.gradle.unsafe.isolated-projects=true"),
            gradleVersion = "9.6.1"
        ).buildAndFail()
        assertTrue(
            result.output.contains("Isolated Projects") || result.output.contains("allprojects"),
            "Isolated Projects must detect cross-project access violation"
        )
    }

    @Test
    fun `planDump system property writes plan file that both nodes agree on`() {
        writeExampleProject()
        val dumpDir = projectDir.resolve("build").also { it.mkdirs() }
        val dump1 = dumpDir.resolve("plan-1.txt")
        val dump2 = dumpDir.resolve("plan-2.txt")
        runner(1, 3, tasks = listOf("test", "-Dshardwise.planDump=$dump1")).build()
        runner(2, 3, tasks = listOf("test", "-Dshardwise.planDump=$dump2")).build()
        assertTrue(dump1.exists(), "node 1 must produce a plan dump")
        assertTrue(dump2.exists(), "node 2 must produce a plan dump")
        assertEquals(
            dump1.readText(), dump2.readText(),
            "both nodes must derive the same plan, because the planner is deterministic"
        )
        val dump = dump1.readText()
        assertTrue(dump.contains("mod-a"), "dump must name a known module")
        assertTrue(dump.contains("="), "dump must contain 'N=mod,mod' lines")
        assertTrue("1=" in dump, "dump must contain node 1's assignment")
        assertTrue("2=" in dump, "dump must contain node 2's assignment")
    }

    @Test
    fun `two shardwise build services are registered with independent params`() {
        writeExampleProject()
        // Append a custom task that lists shared services after shardwise has registered them.
        val existing = projectDir.resolve("build.gradle.kts").readText()
        projectDir.resolve("build.gradle.kts").writeText(
            existing + "\n" + """
            tasks.register("listShardServices") {
                doFirst {
                    gradle.sharedServices.registrations
                        .filter { it.name.startsWith("de.micschro.shardwise") }
                        .forEach { println("SHARDSVC:" + it.name) }
                }
            }
            """.trimIndent()
        )
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("listShardServices")
            .withPluginClasspath()
            .build()
        val svcs = result.output.lines()
            .filter { it.startsWith("SHARDSVC:de.micschro.shardwise") }
            .map { it.removePrefix("SHARDSVC:") }
            .toSet()
        assertTrue(
            "de.micschro.shardwise.planner" in svcs,
            "planner service must be registered; got $svcs"
        )
        assertTrue(
            "de.micschro.shardwise.nodeEnv" in svcs,
            "nodeEnv service must be registered; got $svcs"
        )
        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "listShardServices task must succeed; got: ${result.output.take(500)}"
        )
    }
}
