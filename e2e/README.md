<!-- authoring-audit: 2026-07-16 BLUF,ModePurity,ConceptBudget,Examples,AntiPatterns,Terminology -->

# End-to-end tests

Proves the guarantees that unit and TestKit tests structurally cannot: that N **separate processes**, in **separate containers**, each derive the *same* shard plan and each runs its share of it — and that a failing test still turns the pipeline red.

## Goal

Prove end-to-end sharding guarantees across separate processes and containers.
The e2e suite runs the same stage scripts (`e2e/scripts/run-node.sh`,
`verify.sh`) as the real CI pipeline, so local and CI paths cannot drift apart.

## Prerequisites

- Docker daemon running
- `e2e/run-docker.sh` (local) or `glci` (CI via GitLab Runner)

## Step 1 — Run the e2e suite

```bash
e2e/run.sh              # one pipeline, default scenario
e2e/scenarios.sh        # the full scenario matrix
e2e/scenarios.sh E-03   # one scenario
```

All three commands use `e2e/run-docker.sh` to drive containers directly with
`--network host`. In CI the same scenarios run under
[glci](https://gitlab.com/gitlab-org/ci-cd/runner-tools/glci), a real GitLab
runner — glci needs Docker's embedded DNS, which is broken under gVisor, and
has no network-mode flag to work around the break.

## Step 2 — (Optional) Run the benchmark

Prove that sharding makes things faster. The correctness suite deliberately
uses instant tests that prove nothing about speed:

```bash
e2e/scripts/benchmark.sh    # N=1 vs N=3, extreme weight skew
```

Defaults to `SHARDWISE_E2E_SLEEP_UNIT_MS=100`. Exit 0 means N=3 is faster.

The consumer's tests switch between instant and sleep-based mode via
`SHARDWISE_E2E_SLEEP_TESTS`. Unset or 0 → instant (existing scenarios).
Value 1 → each module sleeps for `weight × SHARDWISE_E2E_SLEEP_UNIT_MS` ms.

## Step 3 — Inspect what each stage does

The pipeline runs three stages. Each is defined in `e2e/.gitlab-ci.yml`, the
single source of truth.

1. **build** — publishes the plugin to a maven repo inside the project tree
   (`-Dmaven.repo.local`), so the test stage resolves the *published artifact*,
   not a classpath.
2. **test** — N containers run with `CI_NODE_INDEX` and `CI_NODE_TOTAL` set
   exactly as GitLab sets them. Each copies `e2e/consumer/` to a scratch
   workspace, runs its shard, and writes both a log and the plan it derived.
3. **verify** — asserts the distribution. See below.

The consumer runs the **image's** Gradle, not the repo's wrapper. The wrapper
pins one exact version, which would silently override the image and make the
version matrix test useless.

### What verify.sh proves

The verifier is **fail-closed**: anything it cannot prove correct, it rejects.

- The module list comes from `e2e/consumer/settings.gradle.kts`, never from
  the logs. The logs are under test — a module the plugin drops entirely would
  appear in no log, and an oracle derived from the logs would never miss it.
- The node count is an input, not a count of the log files that happen to
  exist. A node that died before writing anything is a failure, not a smaller N.
- Every node's plan dump must be identical (the planner is deterministic), and
  each node must have run exactly the share assigned to *it*. An off-by-one in
  the node index leaves "every module runs exactly once" perfectly intact —
  counting coverage alone cannot see it.

`e2e/scripts/verify-test.sh` tests the verifier itself against synthetic logs
covering each of these failure modes. It runs in CI.

## Scenario matrix

Each row protects a specific guarantee. Run it when you change the relevant
code path.

| id | what it protects |
|----|------------------|
| E-01 | baseline: 4 modules over 3 nodes, each runs exactly once |
| E-03 | a failing test **must** fail the pipeline — sharding may not hide it |
| E-06 | more nodes than modules: surplus nodes idle, and still succeed |
| E-07 | a single node shards nothing and runs everything |
| E-08 | a module absent from the weights file still runs — *coverage beats balance* |
| E-09 | a missing weights file costs no coverage |
| E-10 | stale and malformed weights entries cost no coverage |
| E-13 | `planDetail=SUMMARY` |
| E-14 | `planDetail=OFF` changes output only, never the distribution |
| E-15 | the Gradle floor (8.11) and a newer Gradle both work |

The unit and TestKit suites cover planner balance, weights parsing, and invalid
env values more cheaply. They are deliberately not repeated here.

## Scenario knobs

Pass these as environment variables to `run-docker.sh` or with `glci --env`.

| variable | effect |
|----------|--------|
| `NODE_TOTAL` | number of parallel nodes (default 3) |
| `IMAGE` | Gradle image, and thus the Gradle version under test |
| `SHARDWISE_E2E_WEIGHTS` | weights file content, or `missing` to point at a missing file |
| `SHARDWISE_E2E_PLAN_DETAIL` | `OFF` \| `SUMMARY` \| `FULL` |
| `SHARDWISE_E2E_FAIL_MODULE` | module whose test is made to fail |
| `SHARDWISE_E2E_EXPECT_FAIL` | `1` — the pipeline must fail; a green run is the bug |
| `SHARDWISE_E2E_SLEEP_TESTS` | `1` to use sleep-based test durations (default off — instant tests) |
| `SHARDWISE_E2E_SLEEP_UNIT_MS` | sleep multiplier per weight unit (default 100) |

## Don't

- Don't run `./gradlew test` from inside the repo root in the e2e consumer —
  the consumer copies `e2e/consumer/` to a scratch workspace; running in the
  repo picks up the wrapper-pinned version, defeating the version-matrix test.
- Don't count log files to derive the node count — `verify.sh` takes N as input
  and fails if a node never wrote a log, instead of accepting a smaller N as
  success.
- Don't derive the module list from the logs under test — a module the plugin
  drops entirely would not appear in any log; the oracle is
  `e2e/consumer/settings.gradle.kts`, not the logs.

---


