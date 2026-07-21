// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

/**
 * Statistics over a `test-weights.properties` map. Pure data, no logger, no Gradle types.
 *
 * Conventions:
 * - `imbalance` is `max / mean` (1.0 means perfectly balanced).
 * - `p95` and `p99` are nearest-rank: index = ceil(count * q) - 1, clamped.
 * - `defaultWeighted` lists modules present in [allModules] but absent from [parsed].
 *
 * @since 0.3.0
 */
internal data class WeightStats(
    val modules: Int,
    val total: Long,
    val mean: Long,
    val median: Long,
    val p95: Long,
    val p99: Long,
    val max: Long,
    val imbalance: Double,
    val heaviest: Pair<String, Long>?,
    val top10: List<Pair<String, Long>>,
    val defaultWeighted: List<String>,
) {
    companion object {
        val EMPTY = WeightStats(0, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, null, emptyList(), emptyList())

        private const val MEDIAN_Q = 0.50
        private const val P95_Q = 0.95
        private const val P99_Q = 0.99
        private const val TOP_N = 10

        /**
         * Compute stats from the parsed weights map.
         *
         * @param parsed weights from the file (module → ms)
         * @param defaultWeight fallback ms for modules missing from [parsed]
         * @param allModules complete set of module names in the project; missing ones
         *   are treated as `defaultWeight` ms and reported in [defaultWeighted].
         *   Pass `null` when the caller doesn't know the full set.
         */
        fun compute(
            parsed: Map<String, Int>,
            defaultWeight: Int,
            allModules: Set<String>? = null,
        ): WeightStats {
            if (parsed.isEmpty() && (allModules == null || allModules.isEmpty())) return EMPTY

            // Effective weights: parsed values + defaultWeight for missing modules.
            val effective: List<Pair<String, Int>> = run {
                val all = allModules ?: emptySet()
                val present = parsed.keys + all
                present.sorted().map { name ->
                    name to (parsed[name] ?: defaultWeight)
                }
            }
            if (effective.isEmpty()) return EMPTY

            val sorted = effective.sortedBy { it.second }
            val total = effective.sumOf { it.second }.toLong()
            val max = sorted.last().second.toLong()
            val mean = total / effective.size
            val median = percentile(sorted, MEDIAN_Q)
            val p95 = percentile(sorted, P95_Q)
            val p99 = percentile(sorted, P99_Q)
            val imbalance = if (mean == 0L) 0.0 else max.toDouble() / mean.toDouble()
            val top10 = effective.sortedByDescending { it.second }.take(TOP_N)
                .map { it.first to it.second.toLong() }
            val heaviest = if (top10.isNotEmpty()) top10.first() else null
            val defaultWeighted = effective
                .filter { (name, _) -> name !in parsed }
                .map { it.first }
            return WeightStats(
                modules = effective.size,
                total = total,
                mean = mean,
                median = median,
                p95 = p95,
                p99 = p99,
                max = max,
                imbalance = imbalance,
                heaviest = heaviest,
                top10 = top10,
                defaultWeighted = defaultWeighted,
            )
        }

        /** Nearest-rank percentile: index = ceil(count * q) - 1, clamped to [0, last]. */
        private fun percentile(sorted: List<Pair<String, Int>>, q: Double): Long {
            if (sorted.isEmpty()) return 0L
            val raw = kotlin.math.ceil(sorted.size * q).toInt()
            val idx = (raw - 1).coerceAtLeast(0).coerceAtMost(sorted.size - 1)
            return sorted[idx].second.toLong()
        }
    }
}
