package de.micschro.shardwise

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

public interface ShardwiseExtension {
    /** Path to the committed `modulePath=millis` weights file. Optional; missing → all default weight. */
    public val weightsFile: RegularFileProperty
    /** Fallback weight for modules absent from the weights file. */
    public val defaultWeight: Property<Int>
    /** Names of the Test tasks to shard; each task name gets its own independent plan. Default: `["test"]`. */
    public val taskNames: SetProperty<String>
}
