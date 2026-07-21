# Shardwise — Gradle Plugin for Deterministic Test Sharding

[![CI](https://github.com/micschr0/gradle-test-shard-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/micschr0/gradle-test-shard-plugin/actions/workflows/ci.yml)
[![Version](https://img.shields.io/gradle-plugin-portal/v/de.micschro.shardwise?label=version&color=blue)](https://plugins.gradle.org/plugin/de.micschro.shardwise)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/micschr0/gradle-test-shard-plugin/badge)](https://scorecard.dev/viewer/?uri=github.com/micschr0/gradle-test-shard-plugin)
[![License](https://img.shields.io/github/license/micschr0/gradle-test-shard-plugin)](LICENSE)

Shardwise packs your test modules onto CI nodes by measured runtime instead of
module count, so no node sits idle while another runs the heavy suite.

Four ways to run the same 12-module suite (█ testing, ░ idle but paid for):

```text
everything on one runner
  runner 1   ████████████████████████ 4810ms
  wall 4810ms · 1 runner

one runner per module
  :reporting █████████                1840ms
  :core      █████░░░░                 920ms
  :db        ██░░░░░░░                 420ms
  …9 more, all idle by 1400ms+
  wall 1840ms · 12 runners

3 runners, split by module count
  runner 1   ████████████             2440ms
  runner 2   ███████░░░░░             1430ms
  runner 3   █████░░░░░░░              940ms
  wall 2440ms · 3 runners

3 runners, split by measured weight
  runner 1   █████████                1840ms
  runner 2   ███████░░                1490ms
  runner 3   ███████░░                1480ms
  wall 1840ms · 3 runners
```

One runner per module is no faster than Shardwise and costs four times as much:
both finish in 1840ms, but twelve runners bill 22080ms of runner time against
Shardwise's 5520ms. Eleven of them sit idle waiting for `:reporting`.

_Illustrative example — your numbers depend on how your suite is distributed._

Works with any CI provider that sets `CI_NODE_INDEX` and `CI_NODE_TOTAL`. The
planner runs entirely inside your build: no SaaS account, no uploaded build
scans, no per-seat pricing. Every module runs exactly once.

_Pre-1.0: the API may change between releases — the [CHANGELOG](CHANGELOG.md)
records every break._

## What it does

Shardwise solves a classic scheduling problem — minimum makespan: distribute
weighted jobs across identical machines so the slowest one finishes as early as
possible. It uses Greedy-LPT (Longest Processing Time) bin-packing, reading a
weights file of per-module historical timings. The planner is deterministic —
identical inputs always produce identical output. See
[How it works](docs/how-it-works.md) for the algorithm and design rationale.

Throughout these docs, a **node** is the CI machine and a **shard** is the slice
of modules assigned to it.

## Features

- **Greedy-LPT bin-packing** — packs modules onto nodes by measured runtime;
  identical inputs always produce identical assignments
- **Coverage before balance** — a module Shardwise cannot weigh still runs
- **CI-agnostic** — GitLab CI, GitHub Actions, CircleCI, Buildkite, or any
  provider that sets environment variables
- **Multi-task support** — shard `test`, `integrationTest`, or any custom
  `Test` task independently, each with its own plan
- **Optional weights file** — per-module timings, one `path=millis` per line
  (`services/checkout=120000`), generated from JUnit XML (see
  [weights file format](docs/configuration.md#weights-file-format))
- **Configuration-cache safe** — no `afterEvaluate`, no eager task realization

## Quick Start

1. **Apply the plugin.** Add to the root `build.gradle.kts`:

   ```kotlin
   plugins {
     id("de.micschro.shardwise") version "0.2.0"
   }
   ```

2. **Run a shard locally.** Set both env vars (1-based integers ≥ 1) and run
   the tests:

   ```bash
   CI_NODE_TOTAL=2 CI_NODE_INDEX=1 ./gradlew test
   ```

   Modules assigned to the other shard are skipped:

   ```text
   > Task :services:checkout:test SKIPPED
   Skipping task ':services:checkout:test' as task onlyIf 'Shardwise node 1/2' is false.
   ```

3. **Wire CI.** Add the same `shardwise` plugin and environment variables to
   your CI configuration. See [docs/install.md](docs/install.md) for
   provider-specific examples.

## See where your time goes

`./gradlew shardwiseAnalyze` prints the weight distribution. It writes no files
and changes no state, so it is safe on any build:

```text
[shardwise] WEIGHTS ANALYSIS
[shardwise]   modules:   12
[shardwise]   total:     4810ms
[shardwise]   p95:       920ms
[shardwise]   imbalance: 4.59x
[shardwise]
[shardwise] TOP 10 HEAVIEST
[shardwise]   1. :reporting 1840ms (38.3%)
[shardwise]   2. :core 920ms (19.1%)
```

`imbalance` is the heaviest module divided by the average one. Use it to pick
your node count: because a module never splits, the heaviest one floors the wall
time, and nodes past that floor are wasted spend.

```text
1 node    4810ms      3 nodes   1840ms  ← floor
2 nodes   2410ms      6 nodes   1840ms
```

Here the floor is `:reporting` at 1840ms, so three nodes is this build's
sweet spot — a fourth costs money and saves nothing. To go faster than the
floor, split the heaviest module. See the [configuration
reference](docs/configuration.md#weights-analyzer) for the full output.

## Safety Guarantees

Misconfiguration and stale data never silently drop tests — the build enforces
each guarantee:

- **Unknown modules always run** — modules absent from the weights file get
  `defaultWeight`; modules absent from all plans default to running
- **Invalid CI variables fail the build** — a non-numeric `CI_NODE_INDEX` or
  `CI_NODE_TOTAL`, an index below 1, or an index above the total fails the
  build immediately
- **Every module runs on exactly one node** — the deterministic Greedy-LPT
  planner guarantees full coverage with no duplication

One thing the build cannot check for you: the weights file must come from a
trusted source — committed to the repo, or produced by a CI job in the same
pipeline. Nodes reading weights from independent cache entries can diverge; see
[Divergent weights](docs/troubleshooting.md#divergent-weights).

No network calls. The plugin reads two environment variables and nothing else.

## Documentation

Start with installation, then the tutorial; the rest is reference:

| Page | What it covers |
|------|---------------|
| [Installation and CI setup](docs/install.md) | Apply the plugin, configure tasks, wire up any CI provider |
| [Migration tutorial (from manual sharding)](docs/tutorial-migrate.md) | Walk through a real migration step by step |
| [Configuration reference](docs/configuration.md) | Full `shardwise {}` extension, `PlanDetail` levels, weights file format |
| [Self-updating weights](docs/self-updating-weights.md) | Generate `test-weights.properties` from JUnit XML and refresh automatically |
| [How it works](docs/how-it-works.md) | Algorithm design, Greedy-LPT rationale, invariants |
| [Troubleshooting](docs/troubleshooting.md) | Diagnostics for common CI and development issues |

## Compatibility

Gradle 8.11+ (built against the 8.11 API), Java 17+.

## Contributing

Bug fixes and improvements welcome — see [CONTRIBUTING.md](CONTRIBUTING.md)
for the development setup and tooling.

## License

[Apache-2.0](LICENSE)
