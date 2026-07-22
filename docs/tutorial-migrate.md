# Migrate from manual sharding to Shardwise

Your Gradle build uses `parallel: 3` in GitLab CI plus a hand-maintained per-node test list — or no list at all, letting whichever node finishes first run everything. This creates three problems:

1. **Uneven load.** One node gets the 20-minute `:services:checkout` test suite; another gets `:common:*` modules that finish in 30 seconds. Wall time equals the slowest node.
2. **Stale coverage.** When you add a new module and forget to update the test list, some nodes never run it. CI reports green for code that wasn't tested.
3. **Non-reproducibility.** The same commit can shard differently across CI runs because nothing ties a module's assignment to its content.

After this tutorial, your `parallel: 3` line stays; your hand-maintained test list goes. Shardwise uses Greedy-LPT bin-packing against a `test-weights.properties` file to balance the assignment deterministically. The tutorial ends with a coverage assertion proving no module is ever silently lost.

## Contents

- [Subgoal 1 — Capture the current distribution to a baseline](#subgoal-1--capture-the-current-distribution-to-a-baseline)
- [Subgoal 2 — Apply the Shardwise plugin](#subgoal-2--apply-the-shardwise-plugin)
- [Subgoal 3 — Generate the weights file](#subgoal-3--generate-the-weights-file)
- [Subgoal 4 — Verify the coverage invariant](#subgoal-4--verify-the-coverage-invariant)
- [Subgoal 5 — Tear down the manual sharding](#subgoal-5--tear-down-the-manual-sharding)
- [Subgoal 6 — Keep the weights fresh (your turn)](#subgoal-6--keep-the-weights-fresh-your-turn)
- [The sample project we anchor on](#the-sample-project-we-anchor-on)

## The sample project we anchor on

This tutorial uses a 6-module build:

```text
sample-gradle-build/
├── app/                    # 60s of integration tests
├── core/                   # 5s of unit tests
├── common/                 # 2s of shared utilities
└── services/
    ├── checkout/           # 1200s test suite (the elephant)
    ├── payment/            # 600s test suite
    └── shipping/           # 300s test suite
```

Total test time serial: ~2167s. With 3 nodes balanced, the target is roughly ~750s per node instead of 2167s — about a third of the wall time.

You don't need a project this size to follow along. Every step shows the command and the expected output so you can replicate the behaviour on your own build.

---

## Subgoal 1 — Capture the current distribution to a baseline

You cannot tell whether your new sharding is correct without something to compare against. Before you change anything, capture what the current build does on each node.

### Worked example (full scaffolding — observe and copy)

```bash
for i in 1 2 3; do
  echo "=== Node $i ==="
  CI_NODE_INDEX=$i CI_NODE_TOTAL=3 ./gradlew test --console=plain 2>&1 | \
    grep -E "(:.+:test (SKIPPED|FAILED)|Skipping task '.+' as task onlyIf)" | head -50
done > /tmp/before-sharding.log
```

What to look for:

- Each `=== Node N ===` section lists the modules whose `:test` task ran on that node.
- Count the wall-clock time per node — note the largest delta between fastest and slowest.

For the sample project, a typical "before" log shows Node 1 doing almost nothing (only `:core` and `:common`), Node 2 doing `:app`, and Node 3 doing everything in `:services:*` plus `:app` — the round-robin `hash()` does not use weights.

### If you see "BUILD FAILED before any test ran"

Your `:test` configuration references `$CI_NODE_INDEX` without handling the unset case. Some CI runners set `$CI_NODE_INDEX` to "1" by default even when `CI_NODE_TOTAL` is unset locally.

Run `printenv | grep CI_NODE` on the runner: both variables must be present, or both absent. One without the other is the failure.

---

## Subgoal 2 — Apply the Shardwise plugin

Keep the same `parallel: 3` line in your CI YAML. Delete the per-node test-selection logic; add a one-line plugin apply.

### Worked example (full scaffolding)

In your root `build.gradle.kts`:

```kotlin
plugins {
    // ... your existing plugins
    id("de.micschro.shardwise") version "0.4.1"
}
```

That is the entire plugin configuration for now. No changes to your module build files. The plugin discovers `Test` tasks through `configureEach`, attaches its own `onlyIf` per task at execution time, and reads `CI_NODE_INDEX` / `CI_NODE_TOTAL` through a configuration-cache-safe `ValueSource`.

Now run the build with the same CI variables:

```bash
CI_NODE_INDEX=1 CI_NODE_TOTAL=3 ./gradlew test --info 2>&1 | head -40
```

Expected output (excerpt):

```text
:test-app:test SKIPPED
:core:test SKIPPED
:common:test SKIPPED
:services:checkout:test STARTED
:services:payment:test SKIPPED
:services:shipping:test SKIPPED
```

Notice which tasks ran: shard 1's deterministic plan assigned `:services:checkout:test` to it for this run. The four other `:test` tasks were deliberately skipped on this node, not lost.

### If you see "Plugin 'de.micschro.shardwise' not found"

You likely declared `id("de.micschro.shardwise") version "0.4.1"` in `plugins {}` but the plugin portal isn't in your `pluginManagement {}` repositories. Check your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        // ...
    }
}
```

---

## Subgoal 3 — Generate the weights file

Without a weights file, Shardwise has no way to balance. It uses `defaultWeight = 10` for everything, which gives you round-robin — exactly the problem you are trying to escape.

Your test artifacts (JUnit XML files) live in each module's `build/test-results/test/`. Aggregate them into one `test-weights.properties` file whose contents are identical on every CI node of a run.

### Worked example, partial scaffolding (one blank to fill)

The generator is a built-in task — there is **no code to write**. The plugin
ships `generateTestWeights` (registered on the root project). Run it after a
full test suite:

```bash
./gradlew test --no-build-cache
./gradlew generateTestWeights
```

The task walks every `Test` task output declared in `taskNames` (default:
`test`), sums the `time=` attribute per module, and writes a sorted ISO-8859-1
properties file at the root project. Confirm by:

```bash
cat test-weights.properties
```

**Verify your fill-in:** `cat test-weights.properties` should look like this for the sample project (the generator writes an automatic `#<date>` line at the top — nothing else in the header):

```properties
#Thu Jul 17 09:00:00 UTC 2026
services/checkout=1200000
services/payment=600000
services/shipping=300000
app=60000
core=5000
common=2000
```

The values are milliseconds (`services/checkout=1200000` means 1200 seconds). The list is sorted by descending duration.

### If you see `weights == {}` (empty file generated)

Two likely causes:

1. **No JUnit XMLs were found.** Run `./gradlew clean test --no-build-cache` first; without a fresh test run there are no `**/build/test-results/test/TEST-*.xml` files for the aggregator to walk.
2. **JUnit XMLs have `time="0"` or are absent.** Wipe the build with `./gradlew clean test` first. Some Gradle versions write `time="0"` when a test task is restored from the build cache; a fresh execution produces real timings.

---

## Subgoal 4 — Verify the coverage invariant

The plugin is applied and `test-weights.properties` is in place. Before removing the old logic, prove that no module is silently skipped across all shards.

### Worked example (full scaffolding)

Run the build once per shard index. Each run prints the tasks that ran on that shard:

```bash
for i in 1 2 3; do
  echo "=== Shard $i ==="
  CI_NODE_INDEX=$i CI_NODE_TOTAL=3 ./gradlew test --info 2>&1 | \
    grep -E '^(> Task|Skipping task)' | \
    grep -oE ":['a-zA-Z0-9:_-]+:test" | sort -u
done
```

What to check:

- **Every module appears in exactly one shard's list.** A module in zero shards means the weights file excluded it — rerun the generator and fix the path glob.
- **The three lists are mutually disjoint.** A module in two shards means the planner is not deterministic. Run `git diff test-weights.properties` between the most recent and previous run; if identical, file an issue with the `--info` logs from all three shards.

### If you see "a module name appears in zero shards"

A module was added to the build since the weights file was generated. The plugin falls back to `defaultWeight = 10` and includes it in the plan, but if your `shardwise { taskNames.set(setOf("test")) }` block lists task names that don't include this module's task (e.g. `integrationTest`), the test isn't in the set. Verify the `taskNames.set(...)` line includes every task type you want sharded.

### If you see "a module name appears on two shards"

Two causes:

1. **`./gradlew test --rerun-tasks` was used** — the configuration cache uses a stale entry. Wipe the local `.gradle/configuration-cache/` directory and rerun.
2. **Two modules share a `project.path`** (e.g. one in `services/` and one in `examples/` with the same relative name). Rename to disambiguate; the planner sorts modules by full `project.path`, which collides on duplicates.

---

## Subgoal 5 — Tear down the manual sharding

Coverage is provably preserved and balance works. Delete the old code. Find every place you set `onlyIf` based on `$CI_NODE_INDEX`, and remove it.

### Worked example (delete)

Before:

```kotlin
// build.gradle.kts (root)
tasks.withType<Test>().configureEach {
    if (System.getenv("CI_NODE_INDEX")?.toIntOrNull() != null) {
        val n = System.getenv("CI_NODE_INDEX").toInt()
        onlyIf { abs(project.path.hashCode()) % 3 == n - 1 }
    }
}
```

After: nothing. Shardwise attaches its own `onlyIf` to every matching `Test` task. Your manual one was either a subset or a superset, and Shardwise will fight both.

Search your repo for `CI_NODE_INDEX` and `CI_NODE_TOTAL` — anywhere those names appear in `*.gradle.kts` files, they are candidates for deletion. (They appear in your `e2e/` tests and `.gitlab-ci.yml`; those are not the plugin configuration.)

### If you see "a task is skipped when both your old onlyIf and Shardwise say run"

The runner skips a task when ANY `onlyIf` returns false. Your old `onlyIf` and Shardwise's both fire: one says "run" (the module is assigned to this node), the other says "skip" (the hash-based condition), and the task skips because Gradle `onlyIf` is an AND — all predicates must allow execution. Delete the `onlyIf { ... }` block (or the entire `tasks.withType<Test>().configureEach { ... }` wrapper) from your build files now; Subgoal 5 walks through that.

---

## Subgoal 6 — Keep the weights fresh (your turn)

You now have a one-time weights file. It will drift as your build changes — modules get added, tests get rewritten, slow tests get faster. The migration is incomplete without a job that refreshes the weights on a schedule.

The example below keeps this tutorial self-contained. For the full reference — every transport option (committed file, artifact, cache), both refresh strategies, and the caveats — [Self-updating weights](self-updating-weights.md) is the canonical source.

### Worked example — GitLab CI (full scaffolding)

```yaml
update-test-weights:
  rules:
    - if: $CI_PIPELINE_SOURCE == "schedule"
  script:
    - ./gradlew test --no-build-cache   # force real execution
    - ./gradlew generateTestWeights
    - |
      if ! git diff --quiet test-weights.properties; then
        git add test-weights.properties
        git commit -m "Update test weights"
        git push "https://ci:${PROJECT_TOKEN}@${CI_SERVER_HOST}/${CI_PROJECT_PATH}.git" \
          "HEAD:refs/heads/ci/update-test-weights" \
          -o merge_request.create -o merge_request.target=$CI_DEFAULT_BRANCH
      fi
```

### Your task — write it yourself

Adapt the above to your CI provider. The schema is fixed:

1. A scheduled job that re-runs all tests with `--no-build-cache` so timings reflect today's CI runner, not whatever machine seeded the cache entry.
2. Regenerate `test-weights.properties`.
3. Open a PR (or push to a follow-up branch) **only when the file changed** — the `git diff --quiet` gate is what stops empty commits.

**Verify your adaptation:** running the adapted pipeline once should produce a commit with a non-empty diff, and the resulting plan must still match what you verified in Subgoal 4 (same module-to-node assignment for unchanged modules). The diff is acceptable as long as the affected modules balance better than they did before.

Hint: GitHub Actions uses `peter-evans/create-pull-request@v6`; Buildkite uses `parallelism: 1` with a single scheduled trigger; CircleCI uses `cron` schedules and `workflows.<job-name>.filters.branches`.

### If you see "the refresh job always pushes an empty commit"

Your script regenerates the file but does not check whether it actually changed before pushing. The `if ! git diff --quiet` block is the gate — keep it. Without it, every scheduled run produces a no-op commit that pollutes the git history.
