package de.micschro.shardwise.internal

/**
 * Parses committed per-module test weights (`modulePath=millis`). A module absent from the map falls
 * back to [DEFAULT_WEIGHT]. Pure: the Gradle glue reads the file and passes its text here.
 */
internal object TestWeights {

    const val DEFAULT_WEIGHT = 10

    fun parse(propertiesText: String): Map<String, Int> =
        propertiesText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim().toIntOrNull()?.takeIf { it >= 0 }
                    ?: return@mapNotNull null
                key to value
            }
            .toMap()

    fun toModules(
        paths: List<String>,
        weights: Map<String, Int>,
        defaultWeight: Int = DEFAULT_WEIGHT
    ): List<TestModule> =
        paths.map { path -> TestModule(path, weights[path] ?: defaultWeight) }
}
