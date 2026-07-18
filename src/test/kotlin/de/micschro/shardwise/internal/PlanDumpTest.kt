// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlanDumpTest {

    @Test
    fun `dumps one line per node, modules comma-separated`() {
        val plan = ShardPlan(
            nodeTotal = 2,
            assignments = mapOf(
                1 to listOf("mod-a", "mod-b"),
                2 to listOf("mod-c"),
            ),
        )
        assertEquals("1=mod-a,mod-b\n2=mod-c", PlanDump.render(plan))
    }

    @Test
    fun `an idle node still gets a line, so the node count is provable`() {
        val plan = ShardPlan(nodeTotal = 3, assignments = mapOf(1 to listOf("mod-a")))
        assertEquals("1=mod-a\n2=\n3=", PlanDump.render(plan))
    }

    @Test
    fun `module order is stable so two nodes produce byte-identical dumps`() {
        val a = ShardPlan(nodeTotal = 1, assignments = mapOf(1 to listOf("mod-b", "mod-a")))
        val b = ShardPlan(nodeTotal = 1, assignments = mapOf(1 to listOf("mod-a", "mod-b")))
        assertEquals(PlanDump.render(a), PlanDump.render(b))
    }
}
