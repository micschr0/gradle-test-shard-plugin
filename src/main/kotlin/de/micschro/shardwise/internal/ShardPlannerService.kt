// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 micschr0

package de.micschro.shardwise.internal

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build service holding the shard-plan computation: parsed weights, default weight, the
 * per-task module paths, and `nodeTotal` (required for the bin-packing step in
 * [TestShardPlanner.plan]).
 *
 * Split from [ShardNodeEnvService] for an independent configuration-cache key on `nodeIndex`.
 * `nodeTotal` is kept here because [TestShardPlanner.plan] needs it to produce a meaningful
 * `ShardPlan`; moving it to the consumer would require restructuring `ShardPlan` to drop
 * the `nodeTotal` field, which is used by `PlanDump.render` and `PlanRenderer.render`.
 *
 * Cache keys:
 * - ShardPlannerService: `[defaultWeight, weightsText, taskModulePaths, nodeTotal]`
 * - ShardNodeEnvService: `[nodeIndex]`
 * Changing CI_NODE_INDEX invalidates only the Build service cache.
 */
internal abstract class ShardPlannerService : BuildService<ShardPlannerService.Params> {

    interface Params : BuildServiceParameters {
        val defaultWeight: Property<Int>
        val weightsText: Property<String>
        val taskModulePaths: MapProperty<String, List<String>>
        val nodeTotal: Property<Int>
    }

    private val parsedWeights: Map<String, Int> by lazy {
        TestWeights.parse(parameters.weightsText.getOrElse(""))
    }

    private val plans: Map<String, ShardPlan> by lazy {
        parameters.taskModulePaths.get().mapValues { (_, paths) ->
            TestShardPlanner().plan(
                TestWeights.toModules(paths.sorted(), parsedWeights, parameters.defaultWeight.get()),
                parameters.nodeTotal.get()
            )
        }
    }

    fun planFor(taskName: String): ShardPlan? = plans[taskName]
    fun modulesFor(taskName: String): List<String> =
        parameters.taskModulePaths.get()[taskName].orEmpty()
}
