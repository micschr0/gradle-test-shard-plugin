// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeightStatsTest {

    // AC2: empty input
    @Test
    fun `empty weights yields zero stats`() {
        val stats = WeightStats.compute(emptyMap(), defaultWeight = 100)
        assertEquals(0, stats.modules)
        assertEquals(0L, stats.total)
        assertEquals(0.0, stats.imbalance)
        assertTrue(stats.top10.isEmpty())
    }

    // AC3: stats correctness on small input
    @Test
    fun `mean median p95 p99 on 5 sorted weights`() {
        // 10, 20, 30, 40, 50 → mean=30, median=30, p95=50, p99=50
        val stats = WeightStats.compute(
            mapOf("a" to 10, "b" to 20, "c" to 30, "d" to 40, "e" to 50),
            defaultWeight = 100,
        )
        assertEquals(5, stats.modules)
        assertEquals(150L, stats.total)
        assertEquals(30L, stats.mean)
        assertEquals(30L, stats.median)
        assertEquals(50L, stats.p95)
        assertEquals(50L, stats.p99)
        assertEquals(50L, stats.max)
    }

    // AC7: imbalance ratio
    @Test
    fun `imbalance is max over mean`() {
        // a=10, b=20, c=30, d=40, e=50 → max=50, mean=30 → 1.6666
        val stats = WeightStats.compute(
            mapOf("a" to 10, "b" to 20, "c" to 30, "d" to 40, "e" to 50),
            defaultWeight = 100,
        )
        assertTrue(stats.imbalance in 1.66..1.67, "imbalance should be ~1.67, was ${stats.imbalance}")
    }

    @Test
    fun `single module has imbalance 1_0`() {
        val stats = WeightStats.compute(mapOf("only" to 42), defaultWeight = 100)
        assertEquals(1.0, stats.imbalance, 0.001)
        assertEquals(42L, stats.total)
        assertEquals(42L, stats.mean)
        assertEquals(42L, stats.median)
        assertEquals(42L, stats.p95)
        assertEquals(42L, stats.p99)
    }

    // AC4: top-10 ordering
    @Test
    fun `top10 is descending by weight and limited to 10`() {
        val weights = (1..15).associate { "mod-$it" to it * 10 }
        val stats = WeightStats.compute(weights, defaultWeight = 100)
        assertEquals(10, stats.top10.size)
        // First should be the heaviest: mod-15 with 150
        assertEquals("mod-15" to 150L, stats.top10.first())
        // Strictly descending
        for (i in 0 until stats.top10.size - 1) {
            assertTrue(
                stats.top10[i].second >= stats.top10[i + 1].second,
                "top10 not strictly descending at index $i: ${stats.top10[i]} >= ${stats.top10[i + 1]}",
            )
        }
        // Heaviest matches first top10 entry
        assertNotNull(stats.heaviest)
        assertEquals("mod-15", stats.heaviest!!.first)
        assertEquals(150L, stats.heaviest!!.second)
    }

    @Test
    fun `less than 10 modules yields all in top10`() {
        val weights = mapOf("a" to 1, "b" to 2, "c" to 3)
        val stats = WeightStats.compute(weights, defaultWeight = 100)
        assertEquals(3, stats.top10.size)
    }

    // AC5: default-weight detection
    @Test
    fun `defaultWeighted lists modules whose weight equals defaultWeight`() {
        // We pass the full module list (modulesWithDefaults = a + b + c) and the parsed
        // weights map (a + b). The missing 'c' uses defaultWeight.
        val stats = WeightStats.compute(
            parsed = mapOf("a" to 50, "b" to 200),
            defaultWeight = 100,
            allModules = setOf("a", "b", "c"),
        )
        assertEquals(3, stats.modules)
        assertEquals(listOf("c"), stats.defaultWeighted)
        assertEquals(350L, stats.total) // 50 + 200 + 100
        assertEquals(116L, stats.mean)  // 350 / 3 = 116
    }

    @Test
    fun `defaultWeighted empty when all modules have explicit weights`() {
        val stats = WeightStats.compute(
            parsed = mapOf("a" to 50, "b" to 200),
            defaultWeight = 100,
            allModules = setOf("a", "b"),
        )
        assertTrue(stats.defaultWeighted.isEmpty())
    }

    @Test
    fun `percentile formula handles uneven counts`() {
        // 10 modules: 10,20,30,...,100 → sorted, p95 = index 9*0.95=8.55→9 → 100
        val weights = (1..10).associate { "m$it" to it * 10 }
        val stats = WeightStats.compute(weights, defaultWeight = 100)
        assertEquals(100L, stats.p95)
    }

    // AC8: empty report fields
    @Test
    fun `empty stats have safe defaults for top10 and imbalance`() {
        val stats = WeightStats.compute(emptyMap(), defaultWeight = 100)
        assertNull(stats.heaviest)
        assertTrue(stats.defaultWeighted.isEmpty())
    }
}
