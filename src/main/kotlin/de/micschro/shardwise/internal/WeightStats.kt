package de.micschro.shardwise.internal

/**
 * Statistics over a `test-weights.properties` map. Pure data, no logger, no Gradle types.
 *
 * Conventions:
 * - `imbalance` is `max / mean` (1.0 means perfectly balanced).
 * - `p95` and `p99` are nearest-rank: index = ceil(count * q) - 1, clamped.
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
) {
    companion object {
        val EMPTY = WeightStats(0, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, null, emptyList())

        private const val MEDIAN_Q = 0.50
        private const val P95_Q = 0.95
        private const val P99_Q = 0.99
        private const val TOP_N = 10

        fun compute(parsed: Map<String, Int>): WeightStats {
            if (parsed.isEmpty()) return EMPTY
            val sorted = parsed.entries.sortedBy { it.value }
            val total = parsed.values.sum().toLong()
            val max = sorted.last().value.toLong()
            val mean = total / parsed.size
            val median = percentile(sorted, MEDIAN_Q)
            val p95 = percentile(sorted, P95_Q)
            val p99 = percentile(sorted, P99_Q)
            val imbalance = if (mean == 0L) 0.0 else max.toDouble() / mean.toDouble()
            val top10 = parsed.entries.sortedByDescending { it.value }.take(TOP_N)
                .map { it.key to it.value.toLong() }
            val heaviest = top10.firstOrNull()
            return WeightStats(
                modules = parsed.size,
                total = total,
                mean = mean,
                median = median,
                p95 = p95,
                p99 = p99,
                max = max,
                imbalance = imbalance,
                heaviest = heaviest,
                top10 = top10,
            )
        }

        /** Nearest-rank percentile: index = ceil(count * q) - 1, clamped to [0, last]. */
        private fun percentile(sorted: List<Map.Entry<String, Int>>, q: Double): Long {
            if (sorted.isEmpty()) return 0L
            val raw = kotlin.math.ceil(sorted.size * q).toInt()
            val idx = (raw - 1).coerceAtLeast(0).coerceAtMost(sorted.size - 1)
            return sorted[idx].value.toLong()
        }
    }
}
