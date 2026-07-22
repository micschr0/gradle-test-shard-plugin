package de.micschro.shardwise.internal

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * ProjectBuilder tests for the [GenerateTestWeights] action — cheaper than TestKit.
 * Fixtures are hand-written JUnit XML files; the action is invoked directly.
 */
class GenerateTestWeightsTest {

    private fun project(): Project = ProjectBuilder.builder().build()

    private fun task(project: Project): GenerateTestWeights =
        project.tasks.register("gtw", GenerateTestWeights::class.java).get().apply {
            taskNames.set(listOf("test"))
            outputFile.set(File(project.projectDir, "test-weights.properties"))
        }

    private fun xmlDir(project: Project, module: String, vararg files: Pair<String, String>): File {
        val dir = File(project.projectDir, "results/$module").apply { mkdirs() }
        files.forEach { (name, content) -> File(dir, name).writeText(content) }
        return dir
    }

    private fun suiteXml(seconds: String) = """<testsuite name="s" time="$seconds"/>"""

    private fun wire(task: GenerateTestWeights, vararg dirs: Pair<File, String>) {
        task.junitXmlDirs.set(dirs.map { it.first })
        task.dirToModuleKey.set(dirs.associate { it.first.absolutePath.replace('\\', '/') to it.second })
    }

    private fun loadOutput(task: GenerateTestWeights): Properties =
        Properties().apply { task.outputFile.get().asFile.inputStream().use { load(it) } }

    @Test
    fun `aggregates suite seconds per module into milliseconds`() {
        val p = project()
        val t = task(p)
        wire(
            t,
            xmlDir(p, "mod-a", "TEST-1.xml" to suiteXml("1.5"), "TEST-2.xml" to suiteXml("0.25")) to "mod-a",
            xmlDir(p, "mod-b", "TEST-1.xml" to suiteXml("0.5")) to "mod-b",
        )
        t.generate()
        val props = loadOutput(t)
        assertEquals("1750", props.getProperty("mod-a"), "1.5s + 0.25s must sum to 1750 ms")
        assertEquals("500", props.getProperty("mod-b"))
    }

    @Test
    fun `zero-second suite gets the 1ms floor instead of dropping out`() {
        val p = project()
        val t = task(p)
        wire(t, xmlDir(p, "mod-a", "TEST-1.xml" to suiteXml("0")) to "mod-a")
        t.generate()
        assertEquals("1", loadOutput(t).getProperty("mod-a"))
    }

    @Test
    fun `malformed doctype and negative-time xml are skipped not defaulted`() {
        val p = project()
        val t = task(p)
        wire(
            t,
            xmlDir(
                p, "mod-a",
                "TEST-ok.xml" to suiteXml("1.0"),
                "TEST-broken.xml" to "<not-closed",
                "TEST-doctype.xml" to """<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><testsuite time="9"/>""",
                "TEST-negative.xml" to suiteXml("-3"),
                "TEST-nan.xml" to suiteXml("NaN"),
            ) to "mod-a",
        )
        t.generate()
        assertEquals("1000", loadOutput(t).getProperty("mod-a"), "only the valid suite may count")
    }

    @Test
    fun `no xml at all creates no file and leaves an existing file untouched`() {
        val p = project()
        val t = task(p)
        wire(t, File(p.projectDir, "does-not-exist") to "mod-a")
        t.generate()
        assertFalse(t.outputFile.get().asFile.exists(), "empty aggregation must not create a file")

        t.outputFile.get().asFile.writeText("mod-a=42\n")
        t.generate()
        assertEquals("42", loadOutput(t).getProperty("mod-a"), "existing weights must survive an empty run")
    }

    @Test
    fun `unchanged totals do not rewrite the output file`() {
        val p = project()
        val t = task(p)
        wire(t, xmlDir(p, "mod-a", "TEST-1.xml" to suiteXml("1.0")) to "mod-a")
        t.generate()
        val out = t.outputFile.get().asFile
        out.setLastModified(1_000_000L)
        t.generate()
        assertEquals(1_000_000L, out.lastModified(), "identical weights must leave the file untouched")
    }

    @Test
    fun `non-ascii module keys round-trip through the properties format`() {
        val p = project()
        val t = task(p)
        wire(t, xmlDir(p, "moduel", "TEST-1.xml" to suiteXml("1.0")) to "mödul/tëst")
        t.generate()
        assertEquals("1000", loadOutput(t).getProperty("mödul/tëst"))
    }

    @Test
    fun `wireInputsFrom with empty taskNames wires empty inputs and the default output`() {
        val p = project()
        val t = p.tasks.register("gtw", GenerateTestWeights::class.java).get()
        t.taskNames.set(emptyList())
        GenerateTestWeights.wireInputsFrom(t, p, p.objects.fileProperty())
        assertTrue(t.junitXmlDirs.get().isEmpty())
        assertTrue(t.dirToModuleKey.get().isEmpty())
        assertEquals(File(p.projectDir, "test-weights.properties"), t.outputFile.get().asFile)
    }

    @Test
    fun `wireInputsFrom tracks one directory per module and task name keyed by shardPath`() {
        val p = project()
        val sub = ProjectBuilder.builder().withParent(p).withName("mod").build()
        val t = p.tasks.register("gtw", GenerateTestWeights::class.java).get()
        t.taskNames.set(listOf("test"))
        GenerateTestWeights.wireInputsFrom(t, p, p.objects.fileProperty())
        val keys = t.dirToModuleKey.get().values.toSet()
        assertEquals(setOf(".", "mod"), keys, "root maps to '.', :mod to 'mod'")
        assertEquals(2, t.junitXmlDirs.get().size)
        assertTrue(
            t.junitXmlDirs.get().all { it.path.replace('\\', '/').endsWith("build/test-results/test") },
            "tracked dirs must be the JUnit XML output dirs, was: ${t.junitXmlDirs.get()}"
        )
        // silence unused warning — sub exists to contribute the second directory
        assertEquals("mod", sub.name)
    }
}
