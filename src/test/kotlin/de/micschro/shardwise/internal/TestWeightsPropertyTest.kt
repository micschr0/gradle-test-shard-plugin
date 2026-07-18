// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Property-based contract for [TestWeights.parse]: parsing must never throw (the plugin
 * degrades to default weights instead of failing the build) and well-formed properties
 * files round-trip unchanged.
 */
class TestWeightsPropertyTest {

    @Property
    internal fun `parse never throws on arbitrary input`(@ForAll text: String) {
        TestWeights.parse(text)
    }

    @Property
    internal fun `parse round-trips generated properties files`(
        @ForAll("weightEntries") entries: Map<String, Int>
    ) {
        val text = entries.entries.joinToString("\n") { (key, value) -> "$key=$value" }
        assertEquals(entries, TestWeights.parse(text))
    }

    @Provide
    fun weightEntries(): Arbitrary<Map<String, Int>> =
        Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.integers().between(0, Int.MAX_VALUE)
        ).ofMaxSize(50)
}
