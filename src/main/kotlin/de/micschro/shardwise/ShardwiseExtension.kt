// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * Configuration for the `shardwise` extension.
 *
 * @since 0.1.0
 */
public interface ShardwiseExtension {
    /**
     * Path to the committed `modulePath=millis` weights file. Optional; missing → all default weight.
     *
     * @since 0.1.0
     */
    public val weightsFile: RegularFileProperty

    /**
     * Fallback weight for modules absent from the weights file.
     *
     * @since 0.1.0
     */
    public val defaultWeight: Property<Int>

    /**
     * Names of the Test tasks to shard; each task name gets its own independent plan. Default: `["test"]`.
     *
     * @since 0.1.0
     */
    public val taskNames: SetProperty<String>

    /**
     * How much shard-plan detail to log at build start. Default: `FULL`.
     *
     * @since 0.1.0
     */
    public val planDetail: Property<PlanDetail>

    /**
     * When true, all Test tasks are SKIPPED and the shard plan is logged per-module instead.
     * Override via system property `-Dshardwise.planOnly=true` or env `SHARDWISE_PLAN_ONLY=true`.
     * Default: `false`.
     *
     * @since 0.3.0
     */
    public val planOnly: Property<Boolean>
}
