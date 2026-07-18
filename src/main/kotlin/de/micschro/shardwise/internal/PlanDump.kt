// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

/**
 * Serialises a [ShardPlan] as machine-readable text: one line per node, `N=mod,mod`.
 *
 * The e2e pipeline compares these dumps across nodes to prove two things that
 * counting task outcomes cannot: that every node derived the *same* plan, and that
 * each node ran exactly the share assigned to *it*. An off-by-one in the node index
 * leaves "every module runs exactly once" perfectly intact.
 *
 * Modules are sorted so two nodes with the same plan always produce identical bytes.
 * Idle nodes still get a line, so the node count is provable from the dump alone.
 */
internal object PlanDump {

    fun render(plan: ShardPlan): String =
        (1..plan.nodeTotal).joinToString("\n") { node ->
            "$node=" + plan.assignments[node].orEmpty().sorted().joinToString(",")
        }
}
