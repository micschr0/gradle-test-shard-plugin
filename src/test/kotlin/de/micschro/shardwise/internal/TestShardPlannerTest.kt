// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


/** Mirrors the safety-net logic of the production `onlyIf`: unassigned modules default to running. */
private fun ShardPlan.runsOn(nodeIndex: Int, modulePath: String): Boolean {
    val plannedSomewhere = assignments.values.any { modulePath in it }
    if (!plannedSomewhere) return true
    return assignments[nodeIndex]?.contains(modulePath) == true
}

class TestShardPlannerTest {

    private val planner = TestShardPlanner()

    private fun modules(vararg pathToWeight: Pair<String, Int>) =
        pathToWeight.map { (path, weight) -> TestModule(path, weight) }

    private fun allAssigned(plan: ShardPlan): List<String> =
        plan.assignments.values.flatten()

    @Test
    fun `nodeTotal below one is rejected`() {
        assertThrows<IllegalArgumentException> { planner.plan(modules("a" to 1), nodeTotal = 0) }
    }

    @Test
    fun `nodeTotal of one puts every module on the single shard`() {
        val mods = modules("common-a" to 5, "svc-b" to 100, "common-c" to 3)
        val plan = planner.plan(mods, nodeTotal = 1)
        assertEquals(1, plan.assignments.size)
        assertEquals(setOf("common-a", "svc-b", "common-c"), plan.assignments.getValue(1).toSet())
    }

    @Test
    fun `every module is assigned exactly once across all shards`() {
        val mods = modules(
            "svc-a" to 120, "svc-b" to 90, "svc-c" to 60,
            "common-1" to 5, "common-2" to 4, "common-3" to 3, "common-4" to 2
        )
        val plan = planner.plan(mods, nodeTotal = 3)
        val assigned = allAssigned(plan)
        assertEquals(mods.map { it.path }.sorted(), assigned.sorted(), "coverage: union must equal input")
        assertEquals(assigned.size, assigned.toSet().size, "disjointness: no module twice")
    }

    @Test
    fun `shards are numbered one through nodeTotal`() {
        val plan = planner.plan(modules("a" to 1, "b" to 1, "c" to 1), nodeTotal = 3)
        assertEquals(setOf(1, 2, 3), plan.assignments.keys)
    }

    @Test
    fun `more shards than modules leaves some shards empty without error`() {
        val plan = planner.plan(modules("a" to 10, "b" to 20), nodeTotal = 5)
        assertEquals(setOf(1, 2, 3, 4, 5), plan.assignments.keys)
        assertEquals(listOf("a", "b").sorted(), allAssigned(plan).sorted())
        assertEquals(3, plan.assignments.values.count { it.isEmpty() })
    }

    @Test
    fun `greedy LPT balances heavy modules across shards`() {
        val mods = modules("heavy-1" to 100, "heavy-2" to 100, "med-1" to 50, "med-2" to 50)
        val plan = planner.plan(mods, nodeTotal = 2)
        val loads = plan.assignments.values.map { shard ->
            shard.sumOf { path -> mods.first { it.path == path }.weight }
        }
        assertEquals(listOf(150, 150), loads.sorted(), "both shards carry equal load")
    }

    @Test
    fun `greedy LPT keeps makespan within four thirds of optimum`() {
        val mods = modules(
            "a" to 130, "b" to 90, "c" to 80, "d" to 60,
            "e" to 40, "f" to 30, "g" to 20, "h" to 10
        )
        val nodeTotal = 3
        val plan = planner.plan(mods, nodeTotal)
        val total = mods.sumOf { it.weight }
        val optimalLowerBound = maxOf(total / nodeTotal, mods.maxOf { it.weight })
        val makespan = plan.assignments.values.maxOf { shard ->
            shard.sumOf { path -> mods.first { it.path == path }.weight }
        }
        assertTrue(makespan <= optimalLowerBound * 4 / 3, "makespan $makespan exceeds 4/3 of $optimalLowerBound")
    }

    @Test
    fun `plan is deterministic across one hundred runs`() {
        val mods = modules(
            "svc-a" to 120, "svc-b" to 120, "svc-c" to 90,
            "common-1" to 10, "common-2" to 10, "common-3" to 10
        )
        val first = planner.plan(mods, nodeTotal = 4)
        repeat(100) { i ->
            val again = planner.plan(mods, nodeTotal = 4)
            assertEquals(first.assignments, again.assignments, "assignment differs at run $i")
        }
    }

    @Test
    fun `empty module list yields empty shards`() {
        val plan = planner.plan(emptyList(), nodeTotal = 3)
        assertEquals(setOf(1, 2, 3), plan.assignments.keys)
        assertTrue(allAssigned(plan).isEmpty())
    }

    @Test
    fun `plan is invariant under input permutation`() {
        // Core invariant: module discovery order may differ between nodes;
        // all N nodes must still derive the identical plan.
        val mods = modules(
            "svc-a" to 120, "svc-b" to 120, "svc-c" to 90, "svc-d" to 90,
            "common-1" to 10, "common-2" to 10, "common-3" to 10, "common-4" to 0
        )
        val reference = planner.plan(mods, nodeTotal = 3)
        val random = java.util.Random(42)
        repeat(100) { i ->
            val shuffled = mods.shuffled(random)
            assertEquals(
                reference.assignments, planner.plan(shuffled, nodeTotal = 3).assignments,
                "permutation $i must yield the identical plan"
            )
        }
    }

    @Test
    fun `all-zero weights still partition every module exactly once`() {
        val mods = modules("a" to 0, "b" to 0, "c" to 0, "d" to 0, "e" to 0)
        val plan = planner.plan(mods, nodeTotal = 3)
        val assigned = allAssigned(plan)
        assertEquals(mods.map { it.path }.sorted(), assigned.sorted())
        assertEquals(assigned.size, assigned.toSet().size)
    }

    @Test
    fun `duplicate paths never lose coverage`() {
        // The glue supplies unique paths; if duplicates arrive anyway, at worst
        // work is duplicated — coverage must never be lost.
        val mods = modules("dup" to 50, "dup" to 50, "other" to 10)
        val plan = planner.plan(mods, nodeTotal = 3)
        assertTrue((1..3).any { plan.runsOn(it, "dup") }, "dup must run somewhere")
        assertTrue((1..3).any { plan.runsOn(it, "other") }, "other must run somewhere")
    }

    @Test
    fun `weights summing beyond Int MAX do not overflow bucket loads`() {
        val mods = modules(
            "huge-1" to Int.MAX_VALUE, "huge-2" to Int.MAX_VALUE,
            "huge-3" to Int.MAX_VALUE, "huge-4" to Int.MAX_VALUE
        )
        val plan = planner.plan(mods, nodeTotal = 2)
        assertEquals(mods.map { it.path }.sorted(), allAssigned(plan).sorted())
        assertEquals(listOf(2, 2), plan.assignments.values.map { it.size }, "overflow would unbalance the buckets")
    }

}
