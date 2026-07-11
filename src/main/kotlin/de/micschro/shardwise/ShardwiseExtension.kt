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
}
