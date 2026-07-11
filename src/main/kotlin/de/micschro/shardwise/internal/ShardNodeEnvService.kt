package de.micschro.shardwise.internal

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build service holding the NodeEnv index for the current CI node. After the PR 3 split
 * the planner state lives in [ShardPlannerService] so the two have independent
 * configuration-cache keys.
 *
 * The Test task combines both services in its `onlyIf` to make the runs-on-this-node
 * decision (coverage beats balance: unknown plan ⇒ run, never skip).
 */
internal abstract class ShardNodeEnvService : BuildService<ShardNodeEnvService.Params> {

    interface Params : BuildServiceParameters {
        val nodeIndex: Property<Int>
    }
}
