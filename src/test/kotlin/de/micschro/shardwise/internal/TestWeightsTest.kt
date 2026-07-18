// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestWeightsTest {

    @Test
    fun `parses simple key value pairs`() {
        val text = """
            services/checkout/checkout-service=120000
            common/common-domain=500
        """.trimIndent()
        val weights = TestWeights.parse(text)
        assertEquals(120000, weights["services/checkout/checkout-service"])
        assertEquals(500, weights["common/common-domain"])
    }

    @Test
    fun `ignores blank lines and comments`() {
        val text = """
            # generated from junit timings
            common/common-domain=500

            ! also a comment
            common/common-events=300
        """.trimIndent()
        val weights = TestWeights.parse(text)
        assertEquals(2, weights.size)
        assertEquals(300, weights["common/common-events"])
    }

    @Test
    fun `trims surrounding whitespace on keys and values`() {
        val weights = TestWeights.parse("  common/common-domain  =  500  ")
        assertEquals(500, weights["common/common-domain"])
    }

    @Test
    fun `skips malformed lines without a separator`() {
        val text = """
            common/common-domain=500
            this-line-has-no-equals
            common/common-events=300
        """.trimIndent()
        assertEquals(2, TestWeights.parse(text).size)
    }

    @Test
    fun `skips lines with negative values instead of crashing the planner later`() {
        val text = """
            common/common-domain=500
            common/common-broken=-100
        """.trimIndent()
        val weights = TestWeights.parse(text)
        assertEquals(1, weights.size)
        assertEquals(500, weights["common/common-domain"])
    }

    @Test
    fun `skips lines with non-numeric values`() {
        val text = """
            common/common-domain=500
            common/common-broken=not-a-number
        """.trimIndent()
        val weights = TestWeights.parse(text)
        assertEquals(1, weights.size)
        assertEquals(500, weights["common/common-domain"])
    }

    @Test
    fun `builds modules applying default weight for unlisted paths`() {
        val weights = mapOf("common/common-domain" to 500)
        val paths = listOf("common/common-domain", "common/common-new")
        val modules = TestWeights.toModules(paths, weights, defaultWeight = 10)
        assertEquals(TestModule("common/common-domain", 500), modules.first { it.path == "common/common-domain" })
        assertEquals(TestModule("common/common-new", 10), modules.first { it.path == "common/common-new" })
    }

    @Test
    fun `empty paths list yields empty modules`() {
        val modules = TestWeights.toModules(emptyList(), mapOf("a" to 100), defaultWeight = 10)
        assertEquals(emptyList<TestModule>(), modules)
    }

    @Test
    fun `empty weights map yields all-default modules`() {
        val paths = listOf("a", "b", "c")
        val modules = TestWeights.toModules(paths, emptyMap(), defaultWeight = 7)
        assertEquals(3, modules.size)
        modules.forEach { assertEquals(7, it.weight) }
    }

    @Test
    fun `zero defaultWeight yields zero-weight modules still assigned`() {
        val paths = listOf("a", "b")
        val modules = TestWeights.toModules(paths, emptyMap(), defaultWeight = 0)
        assertEquals(2, modules.size)
        modules.forEach { assertEquals(0, it.weight) }
    }

    @Test
    fun `duplicate keys keep the last value`() {
        val text = """
            common/common-domain=500
            common/common-domain=700
        """.trimIndent()
        assertEquals(mapOf("common/common-domain" to 700), TestWeights.parse(text))
    }

    @Test
    fun `empty text yields empty weights`() {
        assertEquals(emptyMap<String, Int>(), TestWeights.parse(""))
    }
}
