// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.Serializable

internal abstract class NodeEnvValueSource : ValueSource<NodeEnv, ValueSourceParameters.None> {
    override fun obtain(): NodeEnv =
        NodeEnv.fromEnv(System.getenv("CI_NODE_INDEX"), System.getenv("CI_NODE_TOTAL"))
}

@Suppress("SerialVersionUIDInSerializableClass")
internal data class NodeEnv(val index: Int, val total: Int) : Serializable {

    internal companion object {
        /**
         * Both unset → single node (local run). Anything set must be fully valid: a silently
         * mis-parsed or clamped index can skip a shard's tests on every node, so fail fast instead.
         */
        fun fromEnv(indexRaw: String?, totalRaw: String?): NodeEnv {
            if (indexRaw == null && totalRaw == null) return NodeEnv(1, 1)
            val total = parsePositive("CI_NODE_TOTAL", totalRaw)
            val index = parsePositive("CI_NODE_INDEX", indexRaw)
            require(index <= total) {
                "CI_NODE_INDEX=$index is out of range 1..$total. Shardwise expects 1-based node " +
                    "indices; for 0-based CI providers (CircleCI, Buildkite) map the index with +1."
            }
            return NodeEnv(index, total)
        }

        private fun parsePositive(name: String, raw: String?): Int {
            requireNotNull(raw) {
                "$name is not set although its counterpart is; set both CI_NODE_INDEX and CI_NODE_TOTAL."
            }
            val value = raw.trim().toIntOrNull()
            require(value != null && value >= 1) {
                "$name='$raw' is not a positive integer; refusing to shard against a broken CI environment."
            }
            return value
        }
    }
}
