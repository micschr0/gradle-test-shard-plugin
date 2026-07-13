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
        val weightsText: Property<String>
        val taskModulePaths: MapProperty<String, List<String>>
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

    val nodeTotal: Int get() = parameters.nodeTotal.get()

    fun planFor(taskName: String): ShardPlan? = plans[taskName]
    fun modulesFor(taskName: String): List<String> =
        parameters.taskModulePaths.get()[taskName].orEmpty()

    fun runsOnThisNode(taskName: String, modulePath: String): Boolean {
        if (nodeTotal <= 1) return true
        return plans[taskName]?.let { plan ->
            plan.runsOn(parameters.nodeIndex.get(), modulePath)
        } ?: true
    }
}
