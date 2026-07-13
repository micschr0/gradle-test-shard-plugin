# Troubleshooting sharded builds

## Goal

Run a sharded build that provably runs every module on exactly one node, with no
silent gaps or duplication.

## Prerequisites

Reproduce the problem locally before debugging in CI. Set both environment
variables manually and run with `--info`:

```bash
CI_NODE_INDEX=2 CI_NODE_TOTAL=3 ./gradlew test --info
```

The plugin is a no-op when both variables are unset — local reproduction
requires them. See [Step 1](#step-1--prove-determinism-locally) for a full
cross-shard verification loop.

## Step 1 — Prove determinism locally

Determinism means every CI node derives the same shard assignment from the same
inputs. Test it in a single shell loop: run each shard 1..N in sequence, grep
for skipped tasks, and verify no module appears on every shard or on more than
one.

```bash
#!/usr/bin/env bash
set -euo pipefail
N=3  # match CI_NODE_TOTAL

for i in $(seq 1 "$N"); do
    echo "=== Shard $i of $N ==="
    CI_NODE_INDEX=$i CI_NODE_TOTAL=$N ./gradlew test --info 2>&1 |
        grep -E "Skipping task '.+' as task onlyIf" |
        sed "s/.*Skipping task '\([^:]*:[^:]*\):.*/\1/" |
        sort -u > "/tmp/shard-$i.tasks"
    echo "  Tasks skipped on this node: $(wc -l < "/tmp/shard-$i.tasks")"
done

echo "=== Cross-shard check ==="
# Every task must skip on exactly N-1 nodes (i.e., run on exactly one).
present=$(mktemp)
for f in /tmp/shard-*.tasks; do cat "$f"; done | sort | uniq -c > "$present"
while read -r count task; do
    if [ "$count" -eq "$N" ]; then
        echo "FAIL: $task skipped on ALL $N shards — runs on zero nodes"
    elif [ "$count" -lt "$((N-1))" ]; then
        echo "FAIL: $task skipped on only $count of $N shards — runs on multiple nodes"
    fi
done < "$present"
echo "=== Determinism check complete ==="
```

**What to look for:**
- No task prints `FAIL` — every module runs on exactly one shard.
- The `sed` expression strips task names (`:services:checkout:test` →
  `:services:checkout:test`), keeping the full Gradle task path. Adjust the
  capture group if your task names are deeper or use different separators.
- The shard dump at `[Step 5](#step-5--plan-inspection-with--dshardwiseplandumppath)`
  gives a faster, cleaner check (no parsing needed, and it proves the *plan*
  matches, not just the execution outcome).

## Step 2 — Diagnose "ran on zero nodes"

A module that neither runs nor skips on any node means the plugin never saw it,
or every node decided it belongs elsewhere. Each cause has a specific string to
check.

### Divergent weights

Parallel CI nodes reading different weights files produce different plans. A
module assigned to node 2 in one plan and node 3 in another runs on neither.

**Check:** Compare the weights file on every node — size, hash, and content must
be identical. Log the hash in the pipeline setup step:

```bash
sha256sum test-weights.properties
```

**Fix:** Commit the weights file to the repo, or produce it as a shared pipeline
artifact before the parallel stage begins. Never let each node populate its own
cache entry independently.

### Missing CI_NODE_INDEX / CI_NODE_TOTAL

Without both variables, the plugin is a no-op — every task runs on every node.
One node with a missing variable and a second node with it set correctly creates
an invisible gap: the first node runs everything, the second skips some, and a
module that only runs on the second node was supposed to run on the first.

**Check:** Log the environment before Gradle starts:

```bash
echo "CI_NODE_INDEX=${CI_NODE_INDEX:-unset} CI_NODE_TOTAL=${CI_NODE_TOTAL:-unset}"
```

Expected output: `CI_NODE_INDEX=2 CI_NODE_TOTAL=3` (1-based).

**Fix:** Map your CI provider's parallelism variables explicitly. See the
[installation guide](install.md#step-3--wire-your-ci-provider) for each
provider's variable names and the 0-to-1 conversion.

### Build cache restores stale `FROM-CACHE` timing

The configuration cache stores the plan at build time. When a node restores from
`FROM-CACHE`, the stored plan reflects the *first* node that wrote it — and the
restoring node evaluates `onlyIf` against that cached plan, not the one it would
have derived. Every node that shares the same cache entry runs the same modules;
the rest run nothing.

**Check:** Grep the build log:

```bash
grep -c "FROM-CACHE" build.log
```

If every node shows `FROM-CACHE` for every test task, the plan was baked once
and broadcast.

**Fix:** Ensure each CI node gets a unique configuration-cache key, or disable
the configuration cache for sharded tasks (`--no-configuration-cache`). The
plugin itself is configuration-cache safe — what breaks is sharing one cached
plan across nodes.

### CI runner clock-skewed weights

A weights file regenerated from JUnit XML timestamps on a runner whose system
clock is off by minutes or hours produces a file with different `# generated
from` metadata or different content than the same file produced on another
runner. Git sees the change, the next CI run re-generates fresh weights from
stale measurements, and the bin-packer distributes load based on corrupted
timing data — which may assign a heavy module to every node (or none).

**Check:** Compare the weights file from the two most recent CI runs:

```bash
git diff HEAD~1 -- test-weights.properties | head -20
```

Unexpected diffs (no new modules were added, no timings changed) signal clock
drift. The file's commit timestamp vs. the runner's timestamp also reveals
drift when they differ by more than a few minutes.

**Fix:** Generate weights from a central job, not per-node. Use real wall-clock
timings (`system.millis` or test framework defaults) rather than the runner's
`date` command.

### Project path collision

Two modules with the same Gradle project path (`:services:checkout` and
`:services:checkout` in different builds) collapse to the same key in the
weights file. The bin-packer treats them as a single module and assigns one
weight to both — one of them gets the slot, the other runs nowhere.

**Check:** List duplicate project paths:

```bash
./gradlew projects 2>&1 | grep "^Project" | sort | uniq -d
```

Any duplicate output means two subprojects have the same path. Rename one in
`settings.gradle.kts` before using Shardwise.

## Step 3 — Retry semantics

Do not retry a single failed parallel node. Rerun the full job (all nodes).

**Why.** Shardwise assigns each module to exactly one node. When node 2 fails a
test and you retry only node 2, the other nodes (1, 3) do not re-run either —
but node 2's re-run also does not touch modules assigned to nodes 1 or 3. The
result is a partial pass: the modules that passed on the first attempt on nodes
1 and 3 pass once, and node 2 passes on the second attempt. No node checked the
modules that were only on node 1 or 3 on the second run. Coverage is intact
(those modules ran once on the first attempt), but you cannot prove it without
inspecting both attempts' logs.

Rerunning the full job is safe and unambiguous: every module runs exactly once
in a single coordinated batch. The cost is duplication of the passing nodes'
work — which, on a large weights-skewed build, may be significant. Mitigate by
keeping weights up to date (see
[Self-updating weights](self-updating-weights.md)), so the balance is good and
the duplicated work is minimal.

**Pipeline recommendation:** Use the CI provider's native retry-at-job-level
feature (GitLab CI `retry: 2`, GitHub Actions `jobs.<job_id>.strategy.fail-fast:
false` with a rerun trigger) rather than a script that detects failure and
re-dispatches a single matrix cell.

## Step 4 — Coverage tools (JaCoCo)

Shardwise runs each module's test task on exactly one node. JaCoCo instruments
those tasks normally, but on the other nodes `jacocoTestReport` skips because
the execution data file is missing. Aggregated reports and threshold checks must
therefore merge `.exec` files from all nodes in a post-shard collect job.

The following shell snippet runs in a single job that has access to all nodes'
`.exec` files (via pipeline artifacts or shared storage):

```bash
#!/usr/bin/env bash
# Merge JaCoCo execution data across all shard nodes.
set -euo pipefail

JACOCO_VERSION=0.8.12
JACOCCO_CLI="java -jar /tmp/jacococli.jar"

if [ ! -f "$JACOCCO_CLI" ]; then
    curl -sL "https://repo1.maven.org/maven2/org/jacoco/jacococli/${JACOCO_VERSION}/jacococli-${JACOCO_VERSION}.zip" \
        -o /tmp/jacococli.zip
    unzip -q -o /tmp/jacococli.zip jacococli.jar -d /tmp
fi

# Collect all .exec files produced across shards
exec_files=(shard-*/build/jacoco/test.exec)

if [ ${#exec_files[@]} -eq 0 ]; then
    echo "No .exec files found — did the shard jobs upload artifacts?"
    exit 1
fi

# Merge into a single aggregate .exec
java -jar /tmp/jacococli.jar merge "${exec_files[@]}" \
    --destfile merged.exec

# Generate an aggregated HTML report
# Requires class-files and source-files from each module;
# adjust paths to match your project layout.
java -jar /tmp/jacococli.jar report merged.exec \
    --classfiles build/classes/java/main \
    --sourcefiles src/main/java \
    --html jacoco-aggregate-report/
```

**Key design decisions:**

- The collect job runs **after** all shard jobs succeed, not in parallel.
- Every shard job must upload `.exec` files as artifacts with `when: always` (so
  a failure still produces partial data for debugging).
- The class-files and source-files paths must point at each module's output
  directories. For a typical multi-module Gradle build, use an
  `allprojects {}`-style strategy or a Gradle plugin that aggregates per-module
  paths into one report task (`jacocoTestReport` with `executionData`).
- Replace `shard-*/` with your CI provider's artifact layout. GitLab CI
  automatically names artifacts after the job; GitHub Actions stores them in a
  flat directory structure — adjust the glob accordingly.

## Step 5 — Plan inspection with `-Dshardwise.planDump`

The plugin can write the full plan (every node's assignment for every task type)
to a file for cross-node comparison. This is off by default — pass the system
property to enable it:

```bash
./gradlew test -Dshardwise.planDump=plans/node-$CI_NODE_INDEX.txt
```

**Where to look:** The file path is relative to the Gradle daemon's working
directory (typically the project root). Each CI node writes to its own path.
Use a shared directory name so the collect step can find all dumps.

**File format:** One line per node, `N=moduleA,moduleB` (sorted). Every node
writes the *full* plan, not just its own slice, so you can diff any two dumps
to confirm they agree:

```bash
diff plans/node-1.txt plans/node-2.txt   # identical if deterministic
```

A module listed on line `N` for two different values of `N` means different
nodes derived different plans — investigate divergent inputs (see
[Step 2](#step-2--diagnose-ran-on-zero-nodes)).

An idle node (zero modules assigned) still gets a line like `3=` — the node
count is provable from the dump alone.

## Don't

- Don't retry a single failed shard node and call the pipeline green — the
  partial pass lacks a coordinated run across all nodes (see
  [Step 3](#step-3--retry-semantics)).
- Don't let each CI node regenerate weights from its own JUnit XML timings —
  clock skew, stale cache entries, or different git refs produce divergent
  weights files, which produce divergent plans (see
  [Step 2 — Divergent weights](#divergent-weights)).
- Don't configure `jacocoTestReport` as a per-shard task that gates the
  pipeline — a single node's report sees partial coverage, causing false
  threshold failures. Merge execution data in a collect job instead (see
  [Step 4](#step-4--coverage-tools-jacoco)).
- Don't share a configuration-cache entry across CI nodes — the cached plan
  from the first node overrides the plan every other node would derive, making
  every node run the same modules.
- Don't grep for `SKIPPED` vs `PASSED` to confirm coverage — a skipped task
  does not appear in test-output logs; grep the `--info`-level `Skipping task`
  line instead (see [Step 1](#step-1--prove-determinism-locally)).

---

Verification:
[ ] BLUF — outcome in first 2 sentences
[ ] Mode Purity — exactly one Diátaxis mode (How-to)
[ ] Concept Budget — ≤3 new concepts per section
[ ] Examples — ≥1 per concept
[ ] Anti-patterns — ≥3 "Don't" items
[ ] Terminology — one term per concept throughout
