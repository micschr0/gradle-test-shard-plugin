package de.micschro.shardwise

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

/**
 * Functional tests for the `generateTestWeights` aggregator task.
 *
 * The task is an aggregator only — it never starts a test suite. Each test
 * plants known JUnit XML files in the build directory of a subproject and
 * asserts the generated `test-weights.properties` shape and content.
 */
class GenerateTestWeightsFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val modules = listOf("mod-a", "mod-b", "mod-c")

    private fun writeProject(applyJava: Boolean = false) {
        projectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"example\"\n" +
                modules.joinToString("\n") { "include(\"$it\")" }
        )
        val rootPlugins = if (applyJava) {
            "java\n                id(\"de.micschro.shardwise\")"
        } else {
            "id(\"de.micschro.shardwise\")"
        }
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                $rootPlugins
            }
            """.trimIndent()
        )
        modules.forEach { m ->
            projectDir.resolve(m).mkdirs()
            projectDir.resolve("$m/build.gradle.kts").writeText("")
        }
    }

    private fun runner(extraArgs: List<String> = emptyList()): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(listOf("generateTestWeights", "--no-build-cache", "--no-configuration-cache") + extraArgs)
        .withEnvironment(System.getenv().filterKeys { it == "JAVA_HOME" || it == "PATH" })
        .forwardOutput()

    private fun writeJUnitXml(
        moduleDir: String,
        taskName: String = "test",
        timeSeconds: Double = 1.234
    ): File {
        val dir = projectDir.resolve(moduleDir).resolve("build/test-results/$taskName")
        dir.mkdirs()
        val file = dir.resolve("TEST-ExampleTest.xml")
        file.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="$moduleDir.ExampleTest" tests="1" failures="0" errors="0" time="$timeSeconds">
  <testcase name="passes" classname="$moduleDir.ExampleTest" time="$timeSeconds"/>
</testsuite>
""".trimIndent()
        )
        return file
    }

    private fun readWeights(): Properties {
        val file = projectDir.resolve("test-weights.properties")
        require(file.exists()) { "weights file not generated at $file" }
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    @Test
    fun `task is registered on the root project`() {
        writeProject()
        val result = runner().build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateTestWeights")?.outcome)
    }

    @Test
    fun `aggregates module paths from per-module JUnit XML files`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 1.0)
        writeJUnitXml("mod-b", taskName = "test", timeSeconds = 2.0)
        writeJUnitXml("mod-c", taskName = "test", timeSeconds = 0.5)

        runner().build()
        val props = readWeights()
        assertEquals("1000", props.getProperty("mod-a"))
        assertEquals("2000", props.getProperty("mod-b"))
        assertEquals("500", props.getProperty("mod-c"))
    }

    @Test
    fun `succeeds with no XMLs and does not overwrite an existing file`() {
        writeProject()
        val weightsFile = projectDir.resolve("test-weights.properties")
        weightsFile.writeText("existing=42\n")
        val beforeContent = weightsFile.readText()

        runner().build()
        assertEquals(beforeContent, weightsFile.readText())
    }

    @Test
    fun `skips malformed XML without failing the build`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 0.5)
        val corrupt = projectDir.resolve("mod-a/build/test-results/test/TEST-Corrupt.xml")
        corrupt.writeText("<not-valid-xml")

        runner().build()
        val props = readWeights()
        assertEquals("500", props.getProperty("mod-a"))
        assertEquals(1, props.size)
    }

    @Test
    fun `coerces zero-time module to at least one millisecond`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 0.0)

        runner().build()
        val props = readWeights()
        assertEquals("1", props.getProperty("mod-a"))
    }

    @Test
    fun `aggregates multiple task names in one run`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 1.0)
        writeJUnitXml("mod-a", taskName = "integrationTest", timeSeconds = 0.5)
        writeJUnitXml("mod-b", taskName = "test", timeSeconds = 2.0)

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("de.micschro.shardwise") }
            shardwise { taskNames.set(setOf("test", "integrationTest")) }
            """.trimIndent()
        )

        runner().build()
        val props = readWeights()
        assertEquals("1500", props.getProperty("mod-a"))
        assertEquals("2000", props.getProperty("mod-b"))
    }

    @Test
    fun `writes to the path supplied by the shardwise weightsFile extension`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 0.5)

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("de.micschro.shardwise") }
            shardwise {
                weightsFile.set(layout.projectDirectory.file("ci/custom-weights.properties"))
            }
            """.trimIndent()
        )

        runner().build()

        val custom = projectDir.resolve("ci/custom-weights.properties")
        val default = projectDir.resolve("test-weights.properties")
        assertTrue(custom.exists(), "custom weights file should be created at $custom")
        assertTrue(!default.exists(), "default file should not be created when extension overrides")
        val props = Properties().apply { custom.inputStream().use { load(it) } }
        assertEquals("500", props.getProperty("mod-a"))
    }

    @Test
    fun `succeeds when no testResults directories exist at all`() {
        writeProject()
        val weightsFile = projectDir.resolve("test-weights.properties")
        assertTrue(!weightsFile.exists())

        runner().build()
        if (weightsFile.exists()) {
            val props = Properties().apply { weightsFile.inputStream().use { load(it) } }
            assertTrue(props.isEmpty(), "no modules with XMLs should yield an empty properties file")
        }
    }

    // ----- moduleKeyFor & parseSuiteMillis edge cases -----

    @Test
    fun `module key roundtrips UTF-8 module names through Properties`() {
        writeProject()
        projectDir.resolve("mod-ü-ä-ö").mkdirs()
        projectDir.resolve("mod-ü-ä-ö/build.gradle.kts").writeText("")
        projectDir.resolve("settings.gradle.kts").appendText("\ninclude(\"mod-ü-ä-ö\")\n")
        writeJUnitXml("mod-ü-ä-ö", taskName = "test", timeSeconds = 1.5)

        runner().build()
        val props = readWeights()
        assertEquals("1500", props.getProperty("mod-ü-ä-ö"))
    }

    @Test
    fun `module key derives deep nesting with slash separators`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"example\"\ninclude(\"a:b:c:d:e\")\n"
        )
        listOf("a", "a/b", "a/b/c", "a/b/c/d", "a/b/c/d/e").forEach { dir ->
            projectDir.resolve(dir).mkdirs()
            projectDir.resolve(dir).resolve("build.gradle.kts").writeText("")
        }
        projectDir.resolve("build.gradle.kts").writeText(
            "plugins { id(\"de.micschro.shardwise\") }\n"
        )
        projectDir.resolve("a/b/c/d/e/build/test-results/test").mkdirs()
        projectDir.resolve("a/b/c/d/e/build/test-results/test/TEST-T.xml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Example" tests="1" time="1.0">
  <testcase name="t" time="1.0"/>
</testsuite>
""".trimIndent()
        )

        runner().build()
        val props = readWeights()
        assertEquals("1000", props.getProperty("a/b/c/d/e"))
    }

    @Test
    fun `weights key follows the Gradle path, not the directory, when they differ`() {
        // The planner keys modules by shardPath, derived from the GRADLE PROJECT
        // PATH (":services:checkout" -> "services/checkout"), independent of where
        // the module sits on disk. Here the project's directory is deliberately
        // NOT its Gradle path: :services:checkout lives at modules/checkout.
        // The generated key must be the runtime key "services/checkout" — if the
        // generator reverse-engineers the key from the filesystem it would emit
        // "modules/checkout" and the planner lookup would silently miss.
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "example"
            include(":services:checkout")
            project(":services:checkout").projectDir = file("modules/checkout")
            """.trimIndent()
        )
        // :services is an implicit intermediate project; Gradle requires its
        // default directory to exist even though only :services:checkout is relocated.
        projectDir.resolve("services").mkdirs()
        projectDir.resolve("modules/checkout").mkdirs()
        projectDir.resolve("modules/checkout/build.gradle.kts").writeText("")
        projectDir.resolve("build.gradle.kts").writeText(
            "plugins { id(\"de.micschro.shardwise\") }\n"
        )
        // Plant the XML under the real (relocated) build directory.
        val results = projectDir.resolve("modules/checkout/build/test-results/test")
        results.mkdirs()
        results.resolve("TEST-T.xml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Example" tests="1" time="1.0">
  <testcase name="t" time="1.0"/>
</testsuite>
""".trimIndent()
        )

        runner().build()

        val props = readWeights()
        // Runtime key = Project(":services:checkout").shardPath == "services/checkout".
        assertEquals(
            "1000",
            props.getProperty("services/checkout"),
            "weights must be keyed by the Gradle-path shardPath the planner looks up; " +
                "got keys ${props.stringPropertyNames()}"
        )
    }

    @Test
    fun `module key handles dotted module names without escaping`() {
        writeProject()
        projectDir.resolve("mod.test").mkdirs()
        projectDir.resolve("mod.test/build.gradle.kts").writeText("")
        projectDir.resolve("settings.gradle.kts").appendText("\ninclude(\"mod.test\")\n")
        writeJUnitXml("mod.test", taskName = "test", timeSeconds = 2.0)

        runner().build()
        val props = readWeights()
        assertEquals("2000", props.getProperty("mod.test"))
    }

    @Test
    fun `skips XML with negative time attribute`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 0.5)
        projectDir.resolve("mod-a/build/test-results/test/TEST-Negative.xml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Negative" tests="1" time="-1.0">
  <testcase name="t" time="-1.0"/>
</testsuite>
""".trimIndent()
        )

        runner().build()
        val props = readWeights()
        assertEquals("500", props.getProperty("mod-a"))
        assertEquals(1, props.size)
    }

    @Test
    fun `skips XML with NaN time attribute`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 0.5)
        projectDir.resolve("mod-a/build/test-results/test/TEST-NaN.xml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="NaN" tests="1" time="NaN">
  <testcase name="t" time="NaN"/>
</testsuite>
""".trimIndent()
        )

        runner().build()
        val props = readWeights()
        assertEquals("500", props.getProperty("mod-a"))
        assertEquals(1, props.size)
    }

    @Test
    fun `skips XML with missing time attribute`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 0.5)
        projectDir.resolve("mod-a/build/test-results/test/TEST-Missing.xml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Missing" tests="1">
  <testcase name="t"/>
</testsuite>
""".trimIndent()
        )

        runner().build()
        val props = readWeights()
        assertEquals("500", props.getProperty("mod-a"))
        assertEquals(1, props.size)
    }

    @Test
    fun `skips XML with empty testsuite`() {
        writeProject()
        writeJUnitXml("mod-a", taskName = "test", timeSeconds = 0.5)
        projectDir.resolve("mod-a/build/test-results/test/TEST-Empty.xml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Empty" tests="0" time="0"/>
""".trimIndent()
        )

        runner().build()
        val props = readWeights()
        assertEquals("500", props.getProperty("mod-a"))
        assertEquals(1, props.size)
    }
}
