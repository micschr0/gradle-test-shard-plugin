package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Mirrors the safety-net logic of the production `onlyIf`: unassigned modules default to running. */
private fun ShardPlan.runsOn(nodeIndex: Int, modulePath: String): Boolean {
    val plannedSomewhere = assignments.values.any { modulePath in it }
    if (!plannedSomewhere) return true
    return assignments[nodeIndex]?.contains(modulePath) == true
}

class ShardPlanTest {

    private val planner = TestShardPlanner()
    private val modules = listOf(
        "services/checkout/checkout-service",
        "services/billing/billing-service",
        "common/common-domain",
        "common/common-events"
    )

    private fun plan(nodeTotal: Int): ShardPlan =
        planner.plan(
            TestWeights.toModules(modules, emptyMap(), TestWeights.DEFAULT_WEIGHT),
            nodeTotal,
        )

    @Test
    fun `every module runs on exactly one node across the full shard set`() {
        val nodeTotal = 3
        val plan = plan(nodeTotal)
        modules.forEach { module ->
            val nodes = (1..nodeTotal).filter { plan.runsOn(it, module) }
            assertEquals(1, nodes.size, "$module must run on exactly one node, ran on $nodes")
        }
    }

    @Test
    fun `unknown module defaults to running as a safety net`() {
        assertTrue(plan(3).runsOn(1, "services/brand-new/brand-new-service"))
    }

    @Test
    fun `planned module does not run on foreign nodes`() {
        val plan = plan(3)
        modules.forEach { module ->
            val home = (1..3).single { plan.runsOn(it, module) }
            (1..3).filter { it != home }.forEach { other ->
                assertFalse(plan.runsOn(other, module), "$module must not also run on node $other")
            }
        }
    }

    @Test
    fun `distribution is deterministic for the same node`() {
        assertEquals(plan(3).assignments[2], plan(3).assignments[2])
    }
}
