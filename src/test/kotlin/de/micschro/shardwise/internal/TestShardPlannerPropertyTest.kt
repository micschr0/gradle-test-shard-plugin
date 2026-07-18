// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.constraints.IntRange
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Property-based invariants for [TestShardPlanner]: every input is covered exactly once and
 * the plan is identical for every input permutation — the contract that lets N CI nodes
 * compute the same plan independently. Example-based counterparts: TestShardPlannerTest.
 */
class TestShardPlannerPropertyTest {

    private val planner = TestShardPlanner()

    @Property
    internal fun `plan covers every module exactly once and is invariant under input permutation`(
        @ForAll("modules") modules: List<TestModule>,
        @ForAll @IntRange(min = 1, max = 64) nodeTotal: Int
    ) {
        val reference = planner.plan(modules, nodeTotal)

        val assigned = reference.assignments.values.flatten()
        assertEquals(modules.map { it.path }.sorted(), assigned.sorted(), "coverage")
        assertEquals(assigned.size, assigned.toSet().size, "no module assigned twice")
        assertEquals((1..nodeTotal).toSet(), reference.assignments.keys, "shard keys 1..N")

        assertEquals(
            reference, planner.plan(modules.shuffled(), nodeTotal),
            "every CI node must derive the identical plan regardless of discovery order"
        )
    }

    @Provide
    internal fun modules(): Arbitrary<List<TestModule>> {
        val paths = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
        val weights = Arbitraries.integers().between(0, Int.MAX_VALUE)
        return paths.set().ofMaxSize(50).flatMap { pathSet ->
            weights.list().ofSize(pathSet.size).map { ws ->
                pathSet.zip(ws) { path, weight -> TestModule(path, weight) }
            }
        }
    }
}
