# Weights Generation Specification

## Purpose

On-demand Gradle task that aggregates JUnit suite timings into a merged `test-weights.properties` file for use by the shardwise planner. Replaces manual Python scripts, ensures correct root-key derivation, and keeps weights in sync with the plugin's module-key logic.

## Requirements

### Requirement: Aggregation of JUnit Suite Timings

The system SHALL parse all JUnit XML files from test task reports and sum test suite times per module.

#### Scenario: Single module with one test suite

- GIVEN a multi-module Gradle project with a `:module-a` module containing one test suite that took 1500ms
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the generated `test-weights.properties` contains `module-a=1500`

#### Scenario: Multiple test suites within same module

- GIVEN a `:module-a` module with two test suites that took 800ms and 700ms
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the generated `test-weights.properties` contains `module-a=1500`

#### Scenario: Multiple modules with varied suite counts

- GIVEN a project with `:module-a` (1 suite, 1500ms) and `:module-b` (2 suites, 800ms + 700ms)
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the generated `test-weights.properties` contains both `module-a=1500` and `module-b=1500`

### Requirement: Module-Key Derivation Consistency

The system MUST derive module keys using the same logic as the planner, including the root-project fix where root tests are keyed as `.`.

#### Scenario: Root-project tests are keyed as `.`

- GIVEN a root project containing test suites with 1200ms total
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the generated `test-weights.properties` contains `.=1200` (not `root=1200`)

#### Scenario: Multi-module keys follow consistent pattern

- GIVEN modules `:a` and `:b.subc` with test times 1000ms and 500ms respectively
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the generated `test-weights.properties` contains both `a=1000` and `b.subc=500`

### Requirement: Sorted-Descending Output Format

The system SHALL render module weights in descending order by default.

#### Scenario: Descending weight ordering

- GIVEN a project with modules `:b=2000`, `:a=1500`, `:c=1000`
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the generated `test-weights.properties` contains `b=2000`, `a=1500`, `c=1000` in that order

#### Scenario: Single-module output is still sorted

- GIVEN a single-module project with `:a=800`
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the generated `test-weights.properties` contains `a=800` (single line is trivially sorted)

### Requirement: Proper Output Target

The system MUST write the merged weights file to the existing `weightsFile` property on `ShardwiseExtension`, maintaining the frozen weights-file format.

#### Scenario: Output to existing property

- GIVEN a project with `ShardwiseExtension` having `weightsFile` set to `build/test-weights.properties`
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the generated file is written to `build/test-weights.properties` with correct content

#### Scenario: Uses existing weights-file format

- GIVEN a project with existing weights-file format (key=int(ms) lines, # generated comment header)
- WHEN the `shardwiseGenerateWeights` task is executed
- THEN the output follows the same format with new values (no structural changes to format)
