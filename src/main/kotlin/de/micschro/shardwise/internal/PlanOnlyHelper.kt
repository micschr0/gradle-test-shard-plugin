// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise.internal

import org.gradle.api.logging.Logger

/**
 * Plan-only mode: skip all Test tasks but log the shard plan that *would* have run.
 *
 * Triggered when ANY of these is `true`:
 * - `ext.planOnly` (build-script property)
 * - System property `shardwise.planOnly` (CI-friendly: `-Dshardwise.planOnly=true`)
 * - Env var `SHARDWISE_PLAN_ONLY=true`
 *
 * Output: a per-module line (`:mod → shard N/M, weight=Xms`) plus a summary
 * line with total weight, shard count, and imbalance ratio.
 *
 * @since 0.3.0
 */
internal object PlanOnlyHelper {

    private const val SYS_PROP = "shardwise.planOnly"
    private const val ENV_VAR = "SHARDWISE_PLAN_ONLY"

    /**
     * Returns true when plan-only is active. Order of precedence:
     * extension value → system property → env var.
     */
    fun resolve(extensionValue: Boolean): Boolean =
        extensionValue ||
            System.getProperty(SYS_PROP)?.toBooleanStrictOrNull() == true ||
            System.getenv(ENV_VAR)?.toBooleanStrictOrNull() == true

    /**
     * Build the per-module plan lines plus summary. Pure: no logger, no side effects.
     * Sort order: assignments by shard, then module name ascending — byte-identical
     * across calls.
     */
    fun render(plan: ShardPlan, totalWeight: Long): List<String> {
        val out = mutableListOf("[shardwise] PLAN-ONLY mode — no tests will run")
        plan.assignments.toSortedMap().forEach { (shard, modules) ->
            modules.sorted().forEach { module ->
                out += "[shardwise] :$module → shard $shard/${plan.nodeTotal}, weight=0ms"
            }
        }
        // Mark empty nodes so the shard count is observable in the log.
        for (node in 1..plan.nodeTotal) {
            if (plan.assignments[node].orEmpty().isEmpty()) {
                out += "[shardwise] :(idle) → shard $node/${plan.nodeTotal}"
            }
        }
        val imbalance = computeImbalance(plan)
        out += "[shardwise] Total: ${plan.assignments.values.sumOf { it.size }} modules, " +
            "${totalWeight}ms, ${plan.nodeTotal} shards, imbalance=${"%.2f".format(imbalance)}x"
        return out
    }

    /** Log the rendered plan via [logger]. Convenience wrapper. */
    fun logPlan(plan: ShardPlan, totalWeight: Long, logger: Logger) {
        render(plan, totalWeight).forEach(logger::lifecycle)
    }

    /** Count-based imbalance: max-modules-per-shard / mean-modules-per-shard. 1.0 = perfectly balanced. */
    private fun computeImbalance(plan: ShardPlan): Double {
        if (plan.nodeTotal == 0) return 0.0
        val total = plan.assignments.values.sumOf { it.size }
        val mean = total.toDouble() / plan.nodeTotal
        if (mean == 0.0) return 0.0
        val max = (1..plan.nodeTotal).maxOf { plan.assignments[it].orEmpty().size }
        return max / mean
    }
}
