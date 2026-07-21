// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise.internal

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Inspects `test-weights.properties` and logs distribution statistics.
 *
 * No outputs: read-only inspection. Caching disabled because the work is
 * "read + log" — cheaper than cache key validation. The weights file is
 * `@Internal` so a missing file is not a configuration error — the action
 * handles "no file" as a normal case and logs a friendly message.
 *
 * @since 0.3.0
 */
@DisableCachingByDefault(because = "Read-only inspection; cheaper than cache key validation")
internal abstract class AnalyzeWeights : DefaultTask() {

    /** Path to the weights file to analyze. May not exist — action handles gracefully. */
    @get:Internal
    abstract val weightsFile: RegularFileProperty

    @TaskAction
    fun analyze() {
        val file = weightsFile.orNull?.asFile
        if (file == null || !file.exists()) {
            val path = file?.path ?: "(no file configured)"
            logger.lifecycle("[shardwise] no weights file found at $path")
            return
        }
        val parsed = TestWeights.parse(file.readText())
        val stats = WeightStats.compute(parsed)
        WeightStatsReporter.log(stats, logger)
    }
}
