package de.micschro.shardwise.internal

import de.micschro.shardwise.shardPath
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Aggregates JUnit `time=` attributes across a build's `Test` task outputs into
 * the canonical per-module weights file consumed by [ShardPlannerService].
 *
 * Aggregator only: it never starts a test suite. Run `test --no-build-cache`
 * first when timings need to reflect today's CI runner rather than restored
 * build-cache output.
 *
 * Inputs track the per-test-task JUnit XML output directory, not the files
 * inside. The file walk happens inside the action — a directory that doesn't
 * exist on a fresh build (zero XMLs) still counts as a real input change when
 * the test task creates it later, and Gradle's up-to-date check does not
 * silently cache an empty weights file.
 *
 * @since 0.2.0
 */
@DisableCachingByDefault(because = "Aggregating local JUnit XML timings is cheaper than caching the result")
internal abstract class GenerateTestWeights : DefaultTask() {

    /** `Test` task names whose `build/test-results/<taskName>/TEST-*.xml` feeds the weights file. */
    @get:Input
    abstract val taskNames: ListProperty<String>

    /**
     * Per-task JUnit XML output directories. Tracked so Gradle re-runs when
     * test tasks regenerate their reports. The actual XML walk happens in
     * the action.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val junitXmlDirs: ListProperty<File>

    /**
     * Maps each tracked JUnit XML directory (absolute path) to the module's
     * runtime key ([shardPath]). Built at configuration time from the Gradle
     * project model so the generated key is derived the *same way* the planner
     * looks it up — never reverse-engineered from the on-disk layout, which
     * diverges when a module's `projectDir` does not mirror its Gradle path.
     */
    @get:Input
    abstract val dirToModuleKey: MapProperty<String, String>

    /** Where to write the generated properties file. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val output = outputFile.get().asFile
        val tmp = File(output.parentFile, output.name + ".tmp")

        val totals = collectTotals(newSecureDocumentBuilder(), dirToModuleKey.get())

        if (totals.isEmpty()) {
            reportNothingToAggregate(output, tmp)
            return
        }

        val newProps = toProperties(totals)
        if (newProps == readExisting(output)) {
            tmp.delete()
            logger.lifecycle("Weights unchanged → ${output.name} not modified")
            return
        }

        writeAtomically(newProps, tmp, output)
        logger.lifecycle(
            "Generated ${output.name}: ${totals.size} module(s), total ${totals.values.sum()} ms"
        )
    }

    /**
     * Sums suite times per module across every tracked JUnit XML directory.
     * The module key comes from [dirToModuleKey] — the same [shardPath] the
     * planner uses — keyed by the directory the XML sits in, not the file path.
     */
    private fun collectTotals(builder: DocumentBuilder, dirKeys: Map<String, String>): Map<String, Int> {
        val totals = LinkedHashMap<String, Int>()
        junitXmlDirs.get().asSequence()
            .filter { it.isDirectory }
            .forEach { dir ->
                val moduleKey = dirKeys[dir.absolutePath.replace('\\', '/')] ?: return@forEach
                dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }.orEmpty().forEach { file ->
                    val millis = parseSuiteMillis(file, builder) ?: return@forEach
                    totals[moduleKey] = (totals[moduleKey] ?: 0) + millis
                }
            }
        return totals
    }

    /**
     * No XMLs found → leave any existing file alone. Generating an empty
     * file would silently switch a user's project to round-robin.
     */
    private fun reportNothingToAggregate(output: File, tmp: File) {
        tmp.delete()
        if (output.exists()) {
            logger.lifecycle(
                "No JUnit XML found — leaving existing ${output.name} unchanged"
            )
        } else {
            logger.lifecycle("No JUnit XML found — ${output.name} not created")
        }
    }

    /**
     * Use Properties.store so non-ASCII module keys round-trip via the
     * standard ISO-8859-1 + \uXXXX escape format that Properties.load
     * expects on every JDK.
     */
    private fun toProperties(totals: Map<String, Int>): Properties {
        // No sorting: Properties.store writes in hashtable order regardless,
        // and the file is machine-consumed. Deterministic for a fixed key set.
        val newProps = Properties()
        for ((module, weightMs) in totals) {
            newProps.setProperty(module, weightMs.coerceAtLeast(MIN_WEIGHT_MILLIS).toString())
        }
        return newProps
    }

    private fun readExisting(output: File): Properties? =
        if (output.exists()) {
            Properties().apply { output.inputStream().use { load(it) } }
        } else {
            null
        }

    private fun writeAtomically(newProps: Properties, tmp: File, output: File) {
        tmp.outputStream().use { newProps.store(it, null) }
        if (!tmp.renameTo(output)) {
            tmp.inputStream().use { input -> output.outputStream().use { input.copyTo(it) } }
            tmp.delete()
        }
    }

    private fun newSecureDocumentBuilder(): DocumentBuilder {
        val dbf = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        return dbf.newDocumentBuilder()
    }

    /**
     * Parses the test-suite-level `time` attribute, returning null when the
     * file is malformed or its time is missing. Callers should skip null
     * results (rather than substitute a fallback) so a corrupt JUnit XML
     * never inflates a module's weight.
     */
    private fun parseSuiteMillis(xml: File, builder: DocumentBuilder): Int? {
        return try {
            val doc = builder.parse(xml)
            val timeAttr = doc.documentElement.getAttribute("time")
            timeAttr.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                ?.let { Math.round(it * MILLIS_PER_SECOND).toInt() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** JUnit XML `time` attributes are seconds; weights are stored in milliseconds. */
        private const val MILLIS_PER_SECOND = 1000.0

        /** Floor for any per-module weight entry: a module reporting 0 s still gets at least 1 ms. */
        private const val MIN_WEIGHT_MILLIS = 1

        /**
         * Wire input tracking: each (module, taskName) JUnit XML output
         * directory is registered as a tracked input, and mapped to the
         * module's [shardPath] — the exact key the planner looks up. The
         * mapping is built here (configuration time) from the Gradle project
         * model, so the generated key can never diverge from the runtime key
         * the way a filesystem-derived key would. The action walks the
         * directories itself. The output path honours an extension-supplied
         * convention; when unset, defaults to `<root>/test-weights.properties`.
         */
        fun wireInputsFrom(
            task: GenerateTestWeights,
            project: Project,
            weightsFile: org.gradle.api.file.RegularFileProperty
        ) {
            val names = task.taskNames.get()
            if (names.isEmpty()) {
                task.junitXmlDirs.set(emptyList())
                task.dirToModuleKey.set(emptyMap())
                task.outputFile.set(weightsFile.orElse(defaultWeightsFile(project)))
                return
            }
            // Track each (module, taskName) build/test-results directory as an
            // input — even if the directory does not exist yet, Gradle
            // re-runs the task when a test task creates it later. Alongside,
            // record dir → shardPath so the action keys timings by the module's
            // runtime key rather than reverse-engineering it from the path.
            val dirs = mutableListOf<File>()
            val dirKeys = LinkedHashMap<String, String>()
            names.forEach { taskName ->
                project.rootProject.allprojects.forEach { p ->
                    val dir = p.layout.buildDirectory.dir("test-results/$taskName").get().asFile
                    dirs += dir
                    dirKeys[dir.absolutePath.replace('\\', '/')] = p.shardPath
                }
            }
            task.junitXmlDirs.set(dirs)
            task.dirToModuleKey.set(dirKeys)
            task.outputFile.set(weightsFile.orElse(defaultWeightsFile(project)))
        }

        /** Default location when the extension does not set `weightsFile`. */
        private fun defaultWeightsFile(project: Project) =
            project.layout.projectDirectory.file("test-weights.properties")
    }
}
