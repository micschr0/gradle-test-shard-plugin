// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeightStatsTest {

    @Test
    fun `empty weights yields zero stats`() {
        val stats = WeightStats.compute(emptyMap())
        assertEquals(0, stats.modules)
        assertEquals(0L, stats.total)
        assertEquals(0.0, stats.imbalance)
        assertTrue(stats.top10.isEmpty())
        assertNull(stats.heaviest)
    }

    @Test
    fun `mean median p95 p99 on 5 sorted weights`() {
        // 10, 20, 30, 40, 50 → mean=30, median=30, p95=50, p99=50
        val stats = WeightStats.compute(
            mapOf("a" to 10, "b" to 20, "c" to 30, "d" to 40, "e" to 50)
        )
        assertEquals(5, stats.modules)
        assertEquals(150L, stats.total)
        assertEquals(30L, stats.mean)
        assertEquals(30L, stats.median)
        assertEquals(50L, stats.p95)
        assertEquals(50L, stats.p99)
        assertEquals(50L, stats.max)
    }

    @Test
    fun `imbalance is max over mean`() {
        val stats = WeightStats.compute(
            mapOf("a" to 10, "b" to 20, "c" to 30, "d" to 40, "e" to 50)
        )
        assertTrue(stats.imbalance in 1.66..1.67, "imbalance should be ~1.67, was ${stats.imbalance}")
    }

    @Test
    fun `single module has imbalance 1_0`() {
        val stats = WeightStats.compute(mapOf("only" to 42))
        assertEquals(1.0, stats.imbalance, 0.001)
        assertEquals(42L, stats.total)
    }

    @Test
    fun `top10 is descending by weight and limited to 10`() {
        val weights = (1..15).associate { "mod-$it" to it * 10 }
        val stats = WeightStats.compute(weights)
        assertEquals(10, stats.top10.size)
        assertEquals("mod-15" to 150L, stats.top10.first())
        for (i in 0 until stats.top10.size - 1) {
            assertTrue(
                stats.top10[i].second >= stats.top10[i + 1].second,
                "top10 not strictly descending at index $i",
            )
        }
        assertEquals("mod-15", stats.heaviest!!.first)
        assertEquals(150L, stats.heaviest!!.second)
    }

    @Test
    fun `less than 10 modules yields all in top10`() {
        val stats = WeightStats.compute(mapOf("a" to 1, "b" to 2, "c" to 3))
        assertEquals(3, stats.top10.size)
    }

    @Test
    fun `percentile formula handles uneven counts`() {
        val weights = (1..10).associate { "m$it" to it * 10 }
        val stats = WeightStats.compute(weights)
        assertEquals(100L, stats.p95)
    }
}
