// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise

/**
 * Controls how much shard-plan detail is logged at build start.
 *
 * - `OFF` — no output.
 * - `SUMMARY` — task name, module count, coverage status.
 * - `FULL` — per-node module lists, loads, load bars, and imbalance ratio.
 *
 * @since 0.1.0
 */
public enum class PlanDetail {
    OFF,
    SUMMARY,
    FULL,
}
