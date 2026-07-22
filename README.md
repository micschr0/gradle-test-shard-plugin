<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/shardwise-logo-dark.svg">
  <img alt="Shardwise — balance Gradle test shards by runtime" src="docs/assets/shardwise-logo-light.svg" width="480">
</picture>

[![CI](https://img.shields.io/github/actions/workflow/status/micschr0/gradle-test-shard-plugin/ci.yml?branch=main&style=flat-square&logo=githubactions&logoColor=white&label=CI&labelColor=02303A&color=1BA39C)](https://github.com/micschr0/gradle-test-shard-plugin/actions/workflows/ci.yml)
[![Version](https://img.shields.io/github/v/tag/micschr0/gradle-test-shard-plugin?style=flat-square&logo=gradle&logoColor=white&label=version&labelColor=02303A&color=1BA39C&sort=semver)](https://github.com/micschr0/gradle-test-shard-plugin/releases)
[![Scorecard](https://img.shields.io/ossf-scorecard/github.com/micschr0/gradle-test-shard-plugin?style=flat-square&logo=securityscorecard&logoColor=white&label=Scorecard&labelColor=02303A&color=1BA39C)](https://scorecard.dev/viewer/?uri=github.com/micschr0/gradle-test-shard-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-8.11%2B-437291?style=flat-square&logo=gradle&logoColor=white&labelColor=02303A)](https://gradle.org)
[![JDK](https://img.shields.io/badge/JDK-17%2B-437291?style=flat-square&logo=openjdk&logoColor=white&labelColor=02303A)](https://openjdk.org)
[![License](https://img.shields.io/github/license/micschr0/gradle-test-shard-plugin?style=flat-square&labelColor=02303A&color=8A9BA3)](LICENSE)

</div>

---

Splitting a Gradle test suite across CI nodes by module count leaves nodes idle:
one node draws the slow modules, the rest finish early and wait. Shardwise packs
modules by their **measured** runtime, so every node finishes at roughly the
same time.

```text
                node 1        node 2        node 3      wall time
1 node          ████████████████████████                  24 min
3 · by count    ████████████  ████████      ████          12 min  ← slowest node wins
3 · by runtime  █████████     ████████      ███████        9 min  ← packed by runtime
```

Same suite, same tests, same coverage — only the assignment changes.

---

## Is this for you?

Shardwise pays off when all three hold:

- **Multi-module build.** Sharding happens per Gradle module, so a single-module
  build has nothing to split.
- **Tests dominate wall time.** If compilation is your bottleneck, fix that first.
- **Uneven modules.** If every module takes the same time, splitting by count is
  already optimal and you need no plugin.

Nothing runs off your machine: no SaaS, no network calls, no telemetry.
Configuration-cache safe. Delete the plugin line and the old behaviour returns.

---

## Get started

Record weights once locally, then set two environment variables per CI job.
No coordinator runs anywhere: every node derives the same plan from the same
file.

```kotlin
// root build.gradle.kts
plugins {
  id("de.micschro.shardwise") version "0.4.1"
}
```

```bash
# once, locally: measure real per-module timings
./gradlew test --no-build-cache     # cached tasks report no timings
./gradlew generateTestWeights       # writes test-weights.properties
git add test-weights.properties     # commit: every node needs identical input
```

Each line holds one module and its milliseconds. The key is the Gradle path with
`/` instead of `:`, because a `.properties` key cannot contain a colon:

```properties
reporting=1840
web=900
services/checkout=600
```

Nested modules keep their full path, so `:services:checkout` becomes
`services/checkout`. The root project is `.`.

```bash
# per CI job, the only thing CI sets
CI_NODE_TOTAL=3 CI_NODE_INDEX=1 ./gradlew test
```

> [!WARNING]
> `CI_NODE_INDEX` counts from **1**, not 0. CircleCI, Buildkite and Bitbucket
> hand you a 0-based index, so add 1 there. A `0` fails the build instead of
> quietly running the wrong shard.

Set neither variable and nothing is skipped, which is why local builds run the
full suite.

Whenever the plugin is unsure — a module it has never seen, a task name it does
not know, a weights file gone stale — it runs the tests
([coverage beats balance](docs/how-it-works.md#1-coverage-beats-balance)).

<sub>Prefer weights refreshed from CI? See [self-updating weights](docs/self-updating-weights.md).
Per-provider CI snippets live in [install.md](docs/install.md).</sub>

<details>
<summary>How the plan is built</summary>

Longest-processing-time-first: sort modules by weight descending, then hand each
one to whichever node is currently lightest. Six modules on three nodes:

```text
module               weight │ node 1   node 2   node 3
────────────────────────────┼──────────────────────────
:reporting             1840 │ ▶ 1840        0        0
:web                    900 │   1840    ▶ 900        0
:api                    780 │   1840      900    ▶ 780
:services:checkout      600 │   1840      900   ▶ 1380
:domain                 400 │   1840   ▶ 1300     1380
:core                   290 │   1840   ▶ 1590     1380
────────────────────────────┼──────────────────────────
                            │   1840     1590     1380   ← 1840 is the wall time
```

The heaviest module goes first and never shares a node with another heavy one.
Ties resolve to the lowest node number, so identical inputs produce identical
plans on every node, with no cross-node communication.

</details>

---

## Configure

The defaults shard the `test` task using `test-weights.properties`. Override
only what you need:

```kotlin
// root build.gradle.kts
shardwise {
  taskNames.set(setOf("test", "integrationTest"))  // one plan per task
  defaultWeight.set(10)                            // for modules without timings
}
```

Each task name gets its own independent plan.
[configuration.md](docs/configuration.md) is the full reference: `weightsFile`,
`planDetail`, plan-only mode, and the weights file format.

---

## How many nodes do you need?

```bash
./gradlew shardwiseAnalyze
```

```text
[shardwise] WEIGHTS ANALYSIS
[shardwise]   modules:   6
[shardwise]   total:     4810ms
[shardwise]   mean:      801ms
[shardwise]   median:    600ms
[shardwise]   p95:       1840ms
[shardwise]   p99:       1840ms
[shardwise]   imbalance: 2.30x
[shardwise]
[shardwise] TOP 6 HEAVIEST
[shardwise]   1. :reporting 1840ms (38.3%)
[shardwise]   2. :web 900ms (18.7%)
[shardwise]   3. :api 780ms (16.2%)
[shardwise]   4. :services/checkout 600ms (12.5%)
[shardwise]   5. :domain 400ms (8.3%)
[shardwise]   6. :core 290ms (6.0%)
```

Your heaviest module sets the wall-time floor: no node finishes before
`:reporting` does, however many nodes you add. `imbalance` is that floor divided
by the mean. At `2.30x` the heaviest module takes more than twice the average,
so nodes past the third mostly idle. To go faster, split the heaviest module
instead of adding nodes.

The task only inspects weights. It never runs a test.

---

## Docs

| Page | Covers |
|------|--------|
| [Install](docs/install.md) | Apply, configure tasks, any CI provider |
| [Self-updating weights](docs/self-updating-weights.md) | Generate + auto-refresh `test-weights.properties` |
| [Migration](docs/tutorial-migrate.md) | Step-by-step, from hand-rolled sharding |
| [Configuration](docs/configuration.md) | `shardwise {}`, `PlanDetail`, plan-only, weights format |
| [How it works](docs/how-it-works.md) | Greedy-LPT, 4/3 bound, coverage guarantee, rationale |
| [Troubleshooting](docs/troubleshooting.md) | Verify the split, diagnose gaps and duplicates |

<sub>Pre-1.0: the API may change between releases. See [CHANGELOG](CHANGELOG.md).</sub>

---

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md) · [SUPPORT.md](SUPPORT.md) · [SECURITY.md](SECURITY.md)

**License:** [Apache-2.0](LICENSE)
