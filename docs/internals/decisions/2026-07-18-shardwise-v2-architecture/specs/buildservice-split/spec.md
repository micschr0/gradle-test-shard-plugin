# Build Service Split Specification

## Purpose

Split the monolithic `ShardBuildService` into two independent `BuildService` instances — one for `NodeEnv` parameters (`ShardBuildService`) and one for planner state (`ShardPlannerService`) — enabling independent configuration-cache (CC) caches when `nodeTotal > 1`.

## Requirements

### Requirement: Two Independent BuildServices

The system MUST provide two separate `BuildService` classes: `ShardBuildService` and `ShardPlannerService`.

#### Scenario: ShardBuildService holds NodeEnv parameters

- GIVEN the plugin is applied to a project
- WHEN `ShardBuildService` is accessed via `project.getServices()`
- THEN it exposes NodeEnv value source parameters (e.g., `CI_NODE_INDEX`, `CI_NODE_TOTAL`)

#### Scenario: ShardPlannerService holds planner state

- GIVEN the plugin is applied to a project
- WHEN `ShardPlannerService` is accessed via `project.getServices()`
- THEN it exposes planner state (e.g., evaluated weights, task names)

### Requirement: Lazy Injection of Planner Results

The system MUST pass planner results from `ShardPlannerService` to `ShardBuildService` via lazy injection.

#### Scenario: Planner service injects results into builder service

- GIVEN both services are initialized in a configuration cache-safe order
- WHEN `ShardBuildService` requests planner results
- THEN it receives them via lazy provider injection (no eager dependency graph)

#### Scenario: No eager dependency creates circular reference

- GIVEN both services attempt eager access to each other's state
- WHEN the plugin is applied
- THEN the dependency graph does NOT create a cycle (verified by `afterEvaluate`/`projectsEvaluated` prohibition)

### Requirement: Configuration-Cache Engagement for Multiple Services

The system MUST assert that configuration cache engages when `nodeTotal > 1` and two services are registered.

#### Scenario: CC engages with multiple services

- GIVEN a project with `ShardwiseExtension.nodeTotal = 2`
- WHEN `gradlew test --configuration-cache` is executed
- THEN the output contains "Configuration cache entry stored" (not "Configuration cache skipped")

#### Scenario: CC uses independent caches per service

- GIVEN a project with `ShardwiseExtension.nodeTotal = 2`
- WHEN two `BuildService` instances are registered
- THEN each service maintains independent cached state (verified by functional test assertions)

### Requirement: Immutable Serializable Results

The system MUST ensure both services return immutable, `Serializable` results to support CC serialization.

#### Scenario: ShardPlannerService returns immutable builder state

- GIVEN `ShardPlannerService` has computed planner state
- WHEN planner state is returned to callers
- THEN the returned object is immutable (no public setters, only read-only properties)

#### Scenario: ShardBuildService result is Serializable

- GIVEN `ShardBuildService` is queried for NodeEnv parameters
- WHEN the result is serialized for CC
- THEN the object implements `Serializable` and no runtime exceptions occur during serialization

### Requirement: Service Registration in ShardwisePlugin

The system MUST register both services in `ShardwisePlugin` with proper `usesService` declarations.

#### Scenario: Both services registered with usesService

- GIVEN `ShardwisePlugin.apply()` executes
- WHEN services are registered
- THEN `ShardBuildService` is registered with `usesService(ShardPlannerService::class)`
- AND `ShardPlannerService` is registered with `usesService(ShardBuildService::class)` (only if NodeEnv parameters needed by planner, which is the case)

#### Scenario: Single service registered when nodeTotal = 1

- GIVEN a project with `ShardwiseExtension.nodeTotal = 1`
- WHEN services are registered
- THEN only one `BuildService` instance is registered (reusing existing `ShardBuildService` or equivalent)
