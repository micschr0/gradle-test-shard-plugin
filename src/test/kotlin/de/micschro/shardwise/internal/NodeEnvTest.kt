// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NodeEnvTest {

    @Test
    fun `both unset means single local node`() {
        assertEquals(NodeEnv(1, 1), NodeEnv.fromEnv(null, null))
    }

    @Test
    fun `valid pair is passed through`() {
        assertEquals(NodeEnv(2, 3), NodeEnv.fromEnv("2", "3"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(NodeEnv(2, 3), NodeEnv.fromEnv(" 2", "3 "))
    }

    @Test
    fun `zero-based index is rejected instead of being clamped`() {
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("0", "3") }
    }

    @Test
    fun `index above total is rejected instead of being clamped`() {
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("4", "3") }
    }

    @Test
    fun `garbage total is rejected instead of silently disabling sharding`() {
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("1", "\${NODE_COUNT}") }
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("1", "") }
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("1", "0") }
    }

    @Test
    fun `index equal to total is the valid upper bound`() {
        assertEquals(NodeEnv(3, 3), NodeEnv.fromEnv("3", "3"))
    }

    @Test
    fun `whitespace-only value is rejected like empty`() {
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv(" ", "3") }
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("1", "\t") }
    }

    @Test
    fun `non-integer numerics are rejected`() {
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("2.5", "3") }
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("1", "1e3") }
    }

    @Test
    fun `one variable set without the other is rejected`() {
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv("2", null) }
        assertThrows<IllegalArgumentException> { NodeEnv.fromEnv(null, "3") }
    }
}
