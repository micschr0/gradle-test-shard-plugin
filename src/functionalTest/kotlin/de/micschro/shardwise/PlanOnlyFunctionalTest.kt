// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests for plan-only mode (`ext.planOnly` and `-Dshardwise.planOnly`).
 * Verifies that the Test tasks are SKIPPED, the plan is logged per-module, and the
 * behaviour is consistent across invocation forms.
 */
class PlanOnlyFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val modules = listOf("mod-a", "mod-b", "mod-c", "mod-d")

    private fun writeExampleProject(shardwiseConfig: String = "shardwise { }") {
        projectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"example\"\n" + modules.joinToString("\n") { "include(\"$it\")" }
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("de.micschro.shardwise") }
            $shardwiseConfig
            """.trimIndent()
        )
        modules.forEach { m ->
            projectDir.resolve(m).apply { mkdirs() }
                .resolve("build.gradle.kts").writeText("plugins { java }\n")
        }
    }

    private fun runner(env: Map<String, String> = emptyMap(), args: List<String>): GradleRunner {
        val baseEnv = System.getenv().filterKeys { it == "JAVA_HOME" || it == "PATH" }
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(args + "--configuration-cache")
            .withEnvironment(baseEnv + env)
            .forwardOutput()
    }

    @Test
    fun `planOnly on extension skips tests and logs the plan`() {
        writeExampleProject("shardwise { planOnly.set(true) }")
        val result = runner(
            env = mapOf("CI_NODE_TOTAL" to "2", "CI_NODE_INDEX" to "1"),
            args = listOf("test"),
        ).build()
        // All test tasks should be SKIPPED.
        modules.forEach { m ->
            assertEquals(
                TaskOutcome.SKIPPED,
                result.task(":$m:test")?.outcome,
                "Expected :$m:test SKIPPED, got ${result.task(":$m:test")?.outcome}",
            )
        }
        // Plan is logged.
        val output = result.output
        assertTrue(output.contains("PLAN-ONLY mode"), "plan header missing in output: $output")
        assertTrue(output.contains(":mod-"), "per-module lines missing in output: $output")
    }

    @Test
    fun `planOnly via system property also skips and logs`() {
        writeExampleProject()
        val result = runner(
            env = mapOf("CI_NODE_TOTAL" to "2", "CI_NODE_INDEX" to "1"),
            args = listOf("test", "-Dshardwise.planOnly=true"),
        ).build()
        assertEquals(TaskOutcome.SKIPPED, result.task(":mod-a:test")?.outcome)
        assertTrue(result.output.contains("PLAN-ONLY mode"))
    }

    @Test
    fun `planOnly via env var also skips and logs`() {
        writeExampleProject()
        val result = runner(
            env = mapOf(
                "CI_NODE_TOTAL" to "2",
                "CI_NODE_INDEX" to "1",
                "SHARDWISE_PLAN_ONLY" to "true",
            ),
            args = listOf("test"),
        ).build()
        assertEquals(TaskOutcome.SKIPPED, result.task(":mod-a:test")?.outcome)
        assertTrue(result.output.contains("PLAN-ONLY mode"))
    }

    @Test
    fun `without planOnly, tests run normally`() {
        writeExampleProject()
        val result = runner(
            env = mapOf("CI_NODE_TOTAL" to "2", "CI_NODE_INDEX" to "1"),
            args = listOf("test"),
        ).build()
        // At least one module's test ran (or was up-to-date). Should NOT be SKIPPED for an actually-executed module.
        // We just check the plan-only banner is NOT present.
        assertTrue(!result.output.contains("PLAN-ONLY mode"), "plan-only should not be active")
    }
}
