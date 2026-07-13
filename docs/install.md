# Installing Shardwise and sharding tests in CI

## Goal

Install the Shardwise Gradle plugin and configure your CI pipeline to run test
modules in parallel across multiple nodes.

## Prerequisites

- Gradle 8.11 or later
- Java 17 or later
- The `shardwise` plugin declaration must live in the **root project** only
  (never in a subproject `build.gradle.kts`)

## Step 1 — Apply the plugin

Add the plugin to the root project's `build.gradle.kts`:

*Pre-1.0: breaking changes may occur without a major version bump. See
[CHANGELOG](../CHANGELOG.md) for details.*

```kotlin
// build.gradle.kts (root project only)
plugins {
    id("de.micschro.shardwise") version "0.1.0"
}
```

## Step 2 — Configure sharded tasks

The `shardwise {}` extension controls which `Test` tasks to shard and which
weights file to use. Add this to the root project:

```kotlin
shardwise {
    // Which Test tasks to shard (default: ["test"])
    taskNames.set(setOf("test", "integrationTest"))
    // Optional: committed per-module timings, modulePath=millis per line
    weightsFile.set(layout.projectDirectory.file("test-weights.properties"))
    // Optional: weight for modules absent from the file (default 10)
    defaultWeight.set(10)
    // Optional: how much of the shard plan to log (default FULL)
    planDetail.set(PlanDetail.SUMMARY)
}
```

`planDetail` requires `import de.micschro.shardwise.PlanDetail`. It changes
console output only — never the distribution.

The plugin reads `CI_NODE_INDEX` and `CI_NODE_TOTAL` (1-based) from the
environment. With both unset (local runs), nothing is skipped. When either is
set, both must be valid — a non-numeric value or an out-of-range index fails the
build immediately. Test tasks not listed in `taskNames` are never skipped, and
the root project's own test tasks are sharded like any module (weights key: `.`).

## Step 3 — Wire your CI provider

Every provider maps its own parallelism variables to `CI_NODE_INDEX` and
`CI_NODE_TOTAL`. GitLab CI sets both automatically; other providers require
explicit mapping (0- vs 1-based indices).

### GitLab CI

GitLab sets both variables automatically with `parallel:`:

```yaml
# .gitlab-ci.yml
test-backend:
  stage: test
  parallel: 3
  script:
    - ./gradlew test
  artifacts:
    when: always
    reports:
      junit:
        - "**/build/test-results/test/TEST-*.xml"
```

### GitHub Actions

Map the matrix index and total manually:

```yaml
# .github/workflows/test.yml
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix: { shard: [1, 2, 3] }
    env:
      CI_NODE_INDEX: ${{ matrix.shard }}
      CI_NODE_TOTAL: 3
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "17" }
      - run: ./gradlew test
```

### CircleCI

CircleCI uses `parallelism:` for total nodes and `CIRCLE_NODE_INDEX` (0-based)
for the index. Add 1 to convert:

```yaml
# .circleci/config.yml
version: 2.1
jobs:
  test:
    parallelism: 3
    environment:
      CI_NODE_TOTAL: "3"
    steps:
      - run: CI_NODE_INDEX=$((CIRCLE_NODE_INDEX + 1)) ./gradlew test
```

### Buildkite

Buildkite uses `parallelism:` for total nodes and `BUILDKITE_PARALLEL_JOB`
(0-based) for the index. Add 1 to convert:

```yaml
# .buildkite/pipeline.yml
steps:
  - label: ":test: Test shard"
    parallelism: 3
    command: >-
      CI_NODE_INDEX=$((BUILDKITE_PARALLEL_JOB + 1))
      CI_NODE_TOTAL=$BUILDKITE_PARALLEL_JOB_COUNT
      ./gradlew test
```

### Other providers

These CI services do not set `CI_NODE_INDEX` or `CI_NODE_TOTAL` automatically.
Map their native variables:

**Azure DevOps.** Use `System.JobPositionInPhase` (1-based) and `System.TotalJobsInPhase`:

```yaml
# azure-pipelines.yml
jobs:
  - job: test
    strategy:
      parallel: 3
    steps:
      - bash: CI_NODE_INDEX=$(System.JobPositionInPhase) CI_NODE_TOTAL=$(System.TotalJobsInPhase) ./gradlew test
```

**Jenkins.** Set variables from a parameterized build:

```groovy
// Jenkinsfile
pipeline {
    agent any
    parameters {
        choice(name: 'SHARD', choices: ['1', '2', '3'])
    }
    environment {
        CI_NODE_INDEX = "${params.SHARD}"
        CI_NODE_TOTAL = '3'
    }
    stages {
        stage('Test') {
            steps { sh './gradlew test' }
        }
    }
}
```

**Travis CI.** Use a build matrix:

```yaml
# .travis.yml
env:
  jobs:
    - SHARD=1
    - SHARD=2
    - SHARD=3
script: CI_NODE_INDEX=$SHARD CI_NODE_TOTAL=3 ./gradlew test
```

**Bitbucket Pipelines.** Use `BITBUCKET_PARALLEL_STEP` (0-based) and `BITBUCKET_PARALLEL_STEP_COUNT`:

```yaml
# bitbucket-pipelines.yml
image: gradle:8-jdk17
pipelines:
  default:
    - parallel:
        steps:
          - step:
              name: "Test shard"
              script:
-                - export CI_NODE_TOTAL=$BITBUCKET_PARALLEL_STEP_COUNT
                - export CI_NODE_INDEX=$((BITBUCKET_PARALLEL_STEP + 1))
                - ./gradlew test
```

**Generic pattern.** For any CI that sets an index via environment variable:

```bash
CI_NODE_INDEX=$YOUR_CI_INDEX CI_NODE_TOTAL=$YOUR_CI_TOTAL ./gradlew test
```

## Step 4 — Verify

Run tests on a CI shard or simulate one locally:

```bash
# Simulate shard 2 of 3
CI_NODE_INDEX=2 CI_NODE_TOTAL=3 ./gradlew test --info 2>&1 | tee build.log
Confirm the plugin shards correctly by running tests in a CI pipeline or
locally with environment variables set manually:

```bash
# Simulate shard 2 of 3 locally
CI_NODE_TOTAL=3 CI_NODE_INDEX=2 ./gradlew test --info
```

Look for the onlyIf skip line. The plugin attaches
`onlyIf("Shardwise node ${n}/${t}")` to each sharded task, so a task on the
wrong shard prints a line whose reason is `Shardwise node N/M` (the actual
node and total), not a fabricated phrase. Test tasks on the right shard run
and print no PASSED suffix on success (`> Task :foo:test`); only `SKIPPED`,
`UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE`, or `FAILED` are standard task outcomes.

To assert in a script, capture task names per shard and verify the three
lists partition the full module set:

```bash
# One task name per line, per shard
for i in 1 2 3; do
  echo "=== Shard $i ==="
  CI_NODE_INDEX=$i CI_NODE_TOTAL=3 ./gradlew test --info 2>&1 | \
    grep -E '^(> Task|Skipping task)' | \
    grep -oE ":['a-zA-Z0-9:_-]+:test" | sort -u
done

# The union of all three lists equals the full module set: every module
# appears in exactly one shard. A name in zero shards means the weights
# file excluded it (regenerate); a name in two shards is a determinism bug.
```
Every shard must skip some modules and run others. No module should be skipped
on every shard or PASSED on multiple shards.

To verify distribution in a script:

```bash
grep -E "(SKIPPED|PASSED|UP-TO-DATE|FROM-CACHE)" build.log
```

## Don't

- Don't apply the plugin to subprojects. Only the root project declares `shardwise`; subproject test tasks are discovered automatically
- Don't let parallel nodes read weights from independent caches. Use a committed file, a shared artifact, or a pre-seeded cache populated before the parallel stage starts
- Don't set `CI_NODE_INDEX` or `CI_NODE_TOTAL` for local development unless you mean to simulate a shard. With both unset, the plugin is a no-op — all tests run normally

---

Verification:
[ ] BLUF — outcome in first 2 sentences
[ ] Mode Purity — exactly one Diátaxis mode (How-to)
[ ] Concept Budget — ≤3 new concepts per section
[ ] Examples — ≥1 per concept
[ ] Anti-patterns — ≥3 "Don't" items
[ ] Terminology — one term per concept throughout
