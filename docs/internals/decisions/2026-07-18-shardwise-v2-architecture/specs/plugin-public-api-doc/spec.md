# Plugin Public API Documentation Specification

## Purpose

Ensure the main plugin class `ShardwisePlugin` includes a class-level KDoc `@since` tag that indicates the version of the plugin where this API was introduced. This provides users with precise version information when referencing the public API surface.

## Requirements

### Requirement: Class-Level @since Tag on ShardwisePlugin

The `ShardwisePlugin` class MUST include a `@since` tag at the class level in its KDoc that specifies the version number when this plugin class was first introduced.

#### Scenario: Plugin class has @since 0.1.0

- GIVEN the `ShardwisePlugin` class in `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt`
- WHEN the class-level KDoc is examined
- THEN it contains `@since 0.1.0`

#### Scenario: @since tag is present in generated API docs

- GIVEN a Gradle build completes with `gradle javadoc`
- WHEN the generated API documentation is inspected
- THEN `ShardwisePlugin` shows `@since 0.1.0` in its documentation header

### Requirement: @since Tag Follows KDoc Conventions

The `@since` tag MUST follow standard KDoc conventions (not `@since 0.1` or `@since 0.1.0-SNAPSHOT` unless that's the stable version number).

#### Scenario: Version tag uses stable release format

- GIVEN the plugin has reached version 0.1.0 stable
- WHEN `@since` is documented
- THEN it uses `@since 0.1.0` (stable version format)

#### Scenario: Tag is inside class-level documentation block

- GIVEN the `ShardwisePlugin` class
- WHEN the KDoc is written
- THEN the `@since` tag appears as the first or second line inside the class-level documentation comment block

### Requirement: @since Tag Is Discoverable in Public API

The `@since` tag MUST be visible in public API surfaces (Javadoc, IDE hover, API docs) without needing to drill into individual methods.

#### Scenario: IDE hover shows @since on class

- GIVEN an IDE with Kotlin/Kotlin plugin installed
- WHEN hovering over `ShardwisePlugin` in the codebase
- THEN the class-level KDoc including `@since 0.1.0` is displayed

#### Scenario: Generated API docs include @since

- GIVEN `gradle javadoc` completes
- WHEN the generated HTML docs for `ShardwisePlugin` are opened
- THEN the `@since 0.1.0` tag is visible in the class header
