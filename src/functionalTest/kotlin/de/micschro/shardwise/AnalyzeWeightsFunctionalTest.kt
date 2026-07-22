package de.micschro.shardwise

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * End-to-end TestKit tests for the `shardwiseAnalyze` task: real Gradle build, real
 * plugin classpath, real `test-weights.properties`.
 */
class AnalyzeWeightsFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val modules = listOf("mod-a", "mod-b", "mod-c")

    private fun writeExampleProject(
        weightsContent: String? = null,
        shardwiseConfig: String = "shardwise { }",
    ) {
        projectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"example\"\n" +
                modules.joinToString("\n") { "include(\"$it\")" }
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
        if (weightsContent != null) {
            projectDir.resolve("test-weights.properties").writeText(weightsContent)
        }
    }

    private fun runner(args: List<String>): GradleRunner {
        val baseEnv = System.getenv().filterKeys { it == "JAVA_HOME" || it == "PATH" }
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(args + "--configuration-cache")
            .withEnvironment(baseEnv)
            .forwardOutput()
    }

    @Test
    fun `shardwiseAnalyze task is registered under Shardwise group`() {
        writeExampleProject()
        val result = runner(listOf("tasks", "--group", "Shardwise")).build()
        assertTrue(result.output.contains("shardwiseAnalyze"), "task not listed: ${result.output}")
    }

    @Test
    fun `missing weights file logs a friendly message and succeeds`() {
        writeExampleProject(weightsContent = null)
        val result = runner(listOf("shardwiseAnalyze")).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":shardwiseAnalyze")?.outcome)
        assertTrue(
            result.output.contains("no weights file found"),
            "missing-file message missing: ${result.output}",
        )
    }

    @Test
    fun `analyzer logs stats, top10 and warnings for a real weights file`() {
        val weights = """
            mod-a=10
            mod-b=200
            mod-c=30
        """.trimIndent()
        writeExampleProject(weightsContent = weights)
        val result = runner(listOf("shardwiseAnalyze")).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":shardwiseAnalyze")?.outcome)
        val output = result.output
        assertTrue(output.contains("WEIGHTS ANALYSIS"), "header missing: $output")
        assertTrue(output.contains("modules:"), "module count missing: $output")
        assertTrue(output.contains("total:"), "total missing: $output")
        assertTrue(output.contains("mean:"), "mean missing: $output")
        assertTrue(output.contains("imbalance:"), "imbalance missing: $output")
        assertTrue(output.contains("TOP"), "top-N section missing: $output")
        // mod-b is the heaviest (200ms)
        assertTrue(output.contains(":mod-b"), "heaviest mod-b missing: $output")
    }

    @Test
    fun `empty weights file shows no-modules message`() {
        writeExampleProject(weightsContent = "")
        val result = runner(listOf("shardwiseAnalyze")).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":shardwiseAnalyze")?.outcome)
        assertTrue(result.output.contains("WEIGHTS ANALYSIS"), "header missing")
        assertTrue(result.output.contains("(no modules)"), "empty message missing")
        assertFalse(result.output.contains("TOP"), "top-10 should be skipped when empty")
    }
}
