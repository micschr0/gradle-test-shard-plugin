package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanOnlyHelperTest {

    @Test
    fun `resolve returns false when extension false and no system property`() {
        withSystemProperty("shardwise.planOnly", null) {
            assertFalse(PlanOnlyHelper.resolve(extensionValue = false))
        }
    }

    @Test
    fun `resolve returns true when system property is true`() {
        withSystemProperty("shardwise.planOnly", "true") {
            assertTrue(PlanOnlyHelper.resolve(extensionValue = false))
        }
    }

    @Test
    fun `resolve returns true when extension value is true regardless of overrides`() {
        withSystemProperty("shardwise.planOnly", null) {
            assertTrue(PlanOnlyHelper.resolve(extensionValue = true))
        }
    }

    @Test
    fun `resolve returns false when system property is set to false explicitly`() {
        withSystemProperty("shardwise.planOnly", "false") {
            assertFalse(PlanOnlyHelper.resolve(extensionValue = false))
        }
    }

    // Env-var coverage: PlanOnlyFunctionalTest (JDK 9+ env mutation needs reflection).

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
    fun `render is deterministic across two calls with same input`() {
        val plan = ShardPlan(
            nodeTotal = 2,
            assignments = mapOf(1 to listOf("mod-b", "mod-a"), 2 to emptyList()),
        )
        assertEquals(PlanOnlyHelper.render(plan, 100), PlanOnlyHelper.render(plan, 100))
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
}
