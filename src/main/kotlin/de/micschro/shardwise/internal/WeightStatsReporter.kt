// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import org.gradle.api.logging.Logger

/**
 * Formats [WeightStats] for the build log. Pure: takes a [Logger] but holds no
 * Gradle state. Section order: header → counters → top-10 → warnings.
 *
 * @since 0.3.0
 */
internal object WeightStatsReporter {

    private const val PERCENT_MULTIPLIER = 100.0

    fun log(stats: WeightStats, logger: Logger) {
        logger.lifecycle("[shardwise] WEIGHTS ANALYSIS")
        if (stats.modules == 0) {
            logger.lifecycle("[shardwise]   (no modules)")
            return
        }
        logger.lifecycle("[shardwise]   modules:   ${stats.modules}")
        logger.lifecycle("[shardwise]   total:     ${stats.total}ms")
        logger.lifecycle("[shardwise]   mean:      ${stats.mean}ms")
        logger.lifecycle("[shardwise]   median:    ${stats.median}ms")
        logger.lifecycle("[shardwise]   p95:       ${stats.p95}ms")
        logger.lifecycle("[shardwise]   p99:       ${stats.p99}ms")
        logger.lifecycle("[shardwise]   imbalance: ${"%.2f".format(stats.imbalance)}x")
        if (stats.top10.isNotEmpty()) {
            logger.lifecycle("[shardwise]")
            logger.lifecycle("[shardwise] TOP ${stats.top10.size} HEAVIEST")
            stats.top10.forEachIndexed { i, (name, weight) ->
                val rank = i + 1
                val pct = if (stats.total > 0) weight * PERCENT_MULTIPLIER / stats.total else 0.0
                logger.lifecycle(
                    "[shardwise]   $rank. :$name ${weight}ms (${"%.1f".format(pct)}%)"
                )
            }
        }
        if (stats.defaultWeighted.isNotEmpty()) {
            logger.lifecycle("[shardwise]")
            logger.lifecycle("[shardwise] WARNINGS")
            logger.lifecycle(
                "[shardwise]   ${stats.defaultWeighted.size} module(s) have no weight " +
                    "(using defaultWeight): ${stats.defaultWeighted.joinToString(", ") { ":$it" }}"
            )
        }
    }
}
