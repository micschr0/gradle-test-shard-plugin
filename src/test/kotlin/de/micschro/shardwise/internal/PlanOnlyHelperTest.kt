// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanOnlyHelperTest {

    // AC1: extension value false + no overrides → false
    @Test
    fun `resolve returns false when extension false and no overrides`() {
        withCleanOverrides { assertFalse(PlanOnlyHelper.resolve(extensionValue = false)) }
    }

    // AC3: system property wins
    @Test
    fun `resolve returns true when system property shardwise planOnly is true`() {
        withSystemProperty("shardwise.planOnly", "true") {
            assertTrue(PlanOnlyHelper.resolve(extensionValue = false))
        }
    }

    // AC4: env var also works
    @Test
    fun `resolve returns true when env var SHARDWISE_PLAN_ONLY is true`() {
        withSystemProperty("shardwise.planOnly", null) {
            withEnv("SHARDWISE_PLAN_ONLY", "true") {
                assertTrue(PlanOnlyHelper.resolve(extensionValue = false))
            }
        }
    }

    @Test
    fun `resolve returns true when extension value is true regardless of overrides`() {
        withCleanOverrides { assertTrue(PlanOnlyHelper.resolve(extensionValue = true)) }
    }

    @Test
    fun `resolve returns false when system property is set to false explicitly`() {
        withSystemProperty("shardwise.planOnly", "false") {
            assertFalse(PlanOnlyHelper.resolve(extensionValue = false))
        }
    }

    // AC5: render output format
    @Test
    fun `render produces per-module lines plus summary`() {
        val plan = ShardPlan(
            nodeTotal = 3,
            assignments = mapOf(
                1 to listOf("mod-c"),
                2 to listOf("mod-a", "mod-b"),
                3 to emptyList(),
            ),
        )
        val lines = PlanOnlyHelper.render(plan, totalWeight = 150)
        // 3 modules, distributed 1/2/0 across shards → max=2, mean=1 → imbalance=2.00x
        assertEquals(
            listOf(
                "[shardwise] PLAN-ONLY mode — no tests will run",
                "[shardwise] :mod-c → shard 1/3, weight=0ms",
                "[shardwise] :mod-a → shard 2/3, weight=0ms",
                "[shardwise] :mod-b → shard 2/3, weight=0ms",
                "[shardwise] :(idle) → shard 3/3",
                "[shardwise] Total: 3 modules, 150ms, 3 shards, imbalance=2.00x",
            ),
            lines,
        )
    }

    @Test
    fun `balanced plan with 1 module per shard has imbalance 1_00x`() {
        val plan = ShardPlan(
            nodeTotal = 3,
            assignments = mapOf(
                1 to listOf("mod-a"),
                2 to listOf("mod-b"),
                3 to listOf("mod-c"),
            ),
        )
        val lines = PlanOnlyHelper.render(plan, totalWeight = 150)
        // No idle node, 1 module per shard → max=1, mean=1 → imbalance=1.00x
        assertTrue(lines.last().endsWith("imbalance=1.00x"), "summary missing or wrong imbalance: $lines")
        assertNull(lines.find { it.contains(":(idle)") }, "balanced plan should not have idle markers: $lines")
    }

    // AC6: determinism
    @Test
    fun `render is deterministic across two calls with same input`() {
        val plan = ShardPlan(
            nodeTotal = 2,
            assignments = mapOf(1 to listOf("mod-b", "mod-a"), 2 to emptyList()),
        )
        assertEquals(PlanOnlyHelper.render(plan, 100), PlanOnlyHelper.render(plan, 100))
    }

    @Test
    fun `render handles empty plan gracefully`() {
        val plan = ShardPlan(nodeTotal = 3, assignments = emptyMap())
        val lines = PlanOnlyHelper.render(plan, totalWeight = 0)
        assertEquals(
            listOf(
                "[shardwise] PLAN-ONLY mode — no tests will run",
                "[shardwise] :(idle) → shard 1/3",
                "[shardwise] :(idle) → shard 2/3",
                "[shardwise] :(idle) → shard 3/3",
                "[shardwise] Total: 0 modules, 0ms, 3 shards, imbalance=0.00x",
            ),
            lines,
        )
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private inline fun withCleanOverrides(block: () -> Unit) {
        withSystemProperty("shardwise.planOnly", null) {
            withEnv("SHARDWISE_PLAN_ONLY", null, block)
        }
    }

    private inline fun withSystemProperty(key: String, value: String?, block: () -> Unit) {
        val previous = System.getProperty(key)
        try {
            if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            block()
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    private inline fun withEnv(key: String, value: String?, block: () -> Unit) {
        // Mutating env via reflection — stdlib offers no portable way to set env in tests.
        val envClass = System.getenv()::class.java
        val field = runCatching { envClass.getDeclaredField("m") }.getOrNull()
            ?: return block()  // Java 9+ unmodifiable map; fall back
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val writable = field.get(System.getenv()) as MutableMap<String, String?>
        val previous = writable[key]
        try {
            if (value == null) writable.remove(key) else writable[key] = value
            block()
        } finally {
            if (previous == null) writable.remove(key) else writable[key] = previous
        }
    }
}
