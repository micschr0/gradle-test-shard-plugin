# Gradle Version Catalog Coverage Specification

## Purpose

Ensure all Gradle plugin dependencies and their transitive versions are defined in `gradle/libs.versions.toml` (the Gradle version catalog), eliminating inline dependency version references in `build.gradle.kts`. This centralizes dependency management and enables automated version validation.

## Requirements

### Requirement: All Plugins Defined in libs.versions.toml

The system MUST define every Gradle plugin used by this project in the `[plugins]` section of `gradle/libs.versions.toml`, with pinned SHA versions.

#### Scenario: Kotlin plugin defined in catalog

- GIVEN `build.gradle.kts` contains `kotlin("jvm")`
- WHEN the plugin is inspected
- THEN `gradle/libs.versions.toml` has `[plugins.kotlin]` with version pinned

#### Scenario: All three Gradle plugins defined in catalog

- GIVEN the project uses Kotlin plugin, detekt plugin, and plugin-publish plugin
- WHEN `gradle/libs.versions.toml` is examined
- THEN all three plugins are defined with pinned SHA versions in `[plugins]` section

#### Scenario: Plugin version follows pinned SHA pattern

- GIVEN a plugin defined in `gradle/libs.versions.toml`
- WHEN the version string is checked
- THEN it follows the format `groupId:artifactId:sha` (e.g., `com.gradle.plugin-publish:com.gradle.plugin-publish:0.22.0`)

### Requirement: No Inline Dependency Versions in build.gradle.kts

The system MUST NOT contain inline version strings for any Gradle plugin dependencies in `build.gradle.kts`.

#### Scenario: No version strings in plugin dependencies

- GIVEN `build.gradle.kts` file
- WHEN the file is examined for `implementation`/`pluginManagement` blocks
- THEN there are no `version = "..."` strings attached to plugin IDs

#### Scenario: Catalog references used instead of inline versions

- GIVEN `build.gradle.kts` references plugins
- WHEN each reference is checked
- THEN it uses `libs.plugins.kotlin` syntax (catalog alias) instead of inline version

### Requirement: Catalog-Defined Libraries in libs.versions.toml

The system MUST define dependency libraries in the `[libraries]` section of `gradle/libs.versions.toml`, not just plugins.

#### Scenario: Kover library defined in catalog

- GIVEN the project uses Kover for test coverage
- WHEN `gradle/libs.versions.toml` is examined
- THEN `[libraries.kover]` exists with version definition

#### Scenario: Dependencies reference catalog aliases

- GIVEN `build.gradle.kts` uses Kover
- WHEN the dependency is referenced
- THEN it uses `libs.plugins.kover` or `libs.libraries.kover` syntax (depending on configuration)

### Requirement: Catalog-Only Dependency Management

The system MUST ensure that no dependency versions are managed via inline `build.gradle.kts` configurations; all versions are centralized in `gradle/libs.versions.toml`.

#### Scenario: Renovate can discover all version pins

- GIVEN `gradle/libs.versions.toml` exists with all plugins and libraries defined
- WHEN Renovate scans the project
- THEN it finds all version pins in one file, not scattered across `build.gradle.kts`

#### Scenario: Version updates flow through catalog

- GIVEN a new version of a plugin becomes available
- WHEN a human updates it
- THEN the update happens in `gradle/libs.versions.toml` only
