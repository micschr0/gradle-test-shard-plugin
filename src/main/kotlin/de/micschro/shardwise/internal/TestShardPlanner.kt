// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

internal data class TestModule(
    val path: String,
    val weight: Int
) {
    init {
        require(weight >= 0) { "Weight must be non-negative for $path" }
    }
}

internal data class ShardPlan(
    val nodeTotal: Int,
    val assignments: Map<Int, List<String>>
)

/**
 * Distributes test modules across [nodeTotal] shards via Greedy-LPT bin-packing (each module to one
 * shard, minimising the slowest shard's load). Deterministic: sorted by weight desc, ties by path asc.
 */
internal class TestShardPlanner {

    fun plan(modules: List<TestModule>, nodeTotal: Int): ShardPlan {
        require(nodeTotal >= 1) { "nodeTotal must be >= 1, was $nodeTotal" }

        val buckets = List(nodeTotal) { mutableListOf<String>() }
        val loads = LongArray(nodeTotal)

        modules.sortedWith(compareByDescending<TestModule> { it.weight }.thenBy { it.path })
            .forEach { module ->
                val target = lightestBucket(loads)
                buckets[target].add(module.path)
                loads[target] += module.weight
            }

        val assignments = buckets
            .mapIndexed { index, bucket -> (index + 1) to bucket.toList() }
            .toMap()
        return ShardPlan(nodeTotal, assignments)
    }

    private fun lightestBucket(loads: LongArray): Int {
        var best = 0
        for (i in 1 until loads.size) {
            if (loads[i] < loads[best]) best = i
        }
        return best
    }
}
