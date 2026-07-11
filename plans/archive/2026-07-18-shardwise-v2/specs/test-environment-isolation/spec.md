# Test Environment Isolation Specification

## Purpose

Ensure functional tests that exercise the shardwise plugin's test environment isolation behavior do not silently pick up ambient CI environment variables (`CI_NODE_INDEX`/`CI_NODE_TOTAL`) that would incorrectly enable sharding in test scenarios where it should not apply.

## Requirements

### Requirement: CI_NODE_* Blocked in Test Runner

The system MUST blocklist `CI_NODE_INDEX` and `CI_NODE_TOTAL` from the test runner's environment, preventing ambient CI variables from leaking into test execution.

#### Scenario: Runner strips CI_NODE_INDEX

- GIVEN a functional test run on a CI node where `CI_NODE_INDEX=3`
- WHEN the `GenerateTestWeightsFunctionalTest` runner filters the environment
- THEN the test executes with `CI_NODE_INDEX` unset or blocked

#### Scenario: Runner strips CI_NODE_TOTAL

- GIVEN a functional test run on a CI node where `CI_NODE_TOTAL=4`
- WHEN the `GenerateTestWeightsFunctionalTest` runner filters the environment
- THEN the test executes with `CI_NODE_TOTAL` unset or blocked

#### Scenario: Both variables blocked simultaneously

- GIVEN a CI node where `CI_NODE_INDEX=1` and `CI_NODE_TOTAL=3`
- WHEN the `GenerateTestWeightsFunctionalTest` runner filters the environment
- THEN both variables are removed before test execution

### Requirement: Runner Uses FilterKeys Pattern

The system MUST use Gradle's `filterKeys` pattern to explicitly blocklist environment variables instead of relying on exclusion patterns.

#### Scenario: FilterKeys removes CI_NODE_*

- GIVEN `GenerateTestWeightsFunctionalTest` filters environment variables
- WHEN the filter block is executed
- THEN `filterKeys { it !in listOf("CI_NODE_INDEX", "CI_NODE_TOTAL") }` is present

#### Scenario: Other standard variables pass through

- GIVEN a test runner with filter configured
- WHEN `JAVA_HOME` and `PATH` are present in test environment
- THEN they remain unchanged in filtered environment

### Requirement: Prevents Ambient Leakage in Aggregator Tests

The system MUST ensure that ambient CI variables do not enable shardwise sharding behavior in tests that should run with default settings.

#### Scenario: No sharding with CI variables blocked

- GIVEN a test configured to verify no sharding behavior
- WHEN `CI_NODE_INDEX`/`CI_NODE_TOTAL` are blocked
- THEN the test asserts planner runs without sharding (e.g., produces single-node plan)

#### Scenario: Tests verify isolation even on CI

- GIVEN a CI environment where `CI_NODE_INDEX=2`
- WHEN tests are run with `GenerateTestWeightsFunctionalTest` runner
- THEN tests verify isolation by explicitly checking that sharding did not occur
