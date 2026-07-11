package de.micschro.shardwise.internal

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

internal abstract class ShardBuildService : BuildService<ShardBuildService.Params> {

    interface Params : BuildServiceParameters {
        val nodeIndex: Property<Int>
        val nodeTotal: Property<Int>
        val defaultWeight: Property<Int>
        val weightsText: Property<String>                        // "" when there is no file
        val taskModulePaths: MapProperty<String, List<String>>   // task name -> module paths owning that task
    }

    private val plans: Map<String, ShardPlan> by lazy {
        val weights = TestWeights.parse(parameters.weightsText.getOrElse(""))
        val defaultWeight = parameters.defaultWeight.getOrElse(TestWeights.DEFAULT_WEIGHT)
        parameters.taskModulePaths.get().mapValues { (_, paths) ->
            TestShardPlanner().plan(
                TestWeights.toModules(paths.sorted(), weights, defaultWeight),
                parameters.nodeTotal.get()
            )
        }
    }

    val nodeTotal: Int get() = parameters.nodeTotal.get()

    /** Unknown tasks and modules default to running rather than being silently skipped. */
    fun runsOnThisNode(taskName: String, modulePath: String): Boolean {
        if (nodeTotal <= 1) return true
        return plans[taskName]?.let { plan ->
            plan.runsOn(parameters.nodeIndex.get(), modulePath)
        } ?: true
    }
}
