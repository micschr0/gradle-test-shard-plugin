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

Splitting a Gradle test suite across CI nodes by module count leaves nodes
idle: one node draws the slow modules, the rest finish early and wait. Shardwise
packs modules by their **measured** runtime instead, so every node finishes at
roughly the same time.

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
Configuration-cache safe. Removing the plugin line restores the old behaviour.

---

## Get started

Record weights once locally, then set two environment variables per CI job.
There is no coordinator — every node derives the same plan from the same file.

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

The generated file is one line per module, milliseconds, keyed by Gradle path
with `/` instead of `:` (a `.properties` key cannot contain `:`):

```properties
reporting=1840
web=900
services/checkout=600
```

Nested modules use their full Gradle path: `:services:checkout` is keyed
`services/checkout`, and the root project is keyed `.`.

```bash
# per CI job, the only thing CI sets
CI_NODE_TOTAL=3 CI_NODE_INDEX=1 ./gradlew test
```

> [!WARNING]
> `CI_NODE_INDEX` is **1-based**. On 0-based CI (GitHub Actions matrix,
> CircleCI), add 1. With both variables unset, the plugin is a no-op and every
> test runs.

Every module lands on exactly one node, never zero — unknown modules, unknown
task names and stale weights all default to *running*
([coverage beats balance](docs/how-it-works.md#1-coverage-beats-balance)).

<sub>Prefer weights refreshed from CI? See [self-updating weights](docs/self-updating-weights.md).
Per-provider CI snippets live in [install.md](docs/install.md).</sub>

<details>
<summary>How the plan is built</summary>

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'ui-monospace, monospace','lineColor':'#437291','primaryColor':'#02303A','primaryTextColor':'#ffffff','primaryBorderColor':'#1BA39C'}}}%%
flowchart LR
  W["test-weights.properties"] --> P["Greedy-LPT<br/>planner"]
  E["CI_NODE_INDEX<br/>CI_NODE_TOTAL"] --> P
  P --> N1["node 1 · :reporting"]
  P --> N2["node 2 · :web :domain :core"]
  P --> N3["node 3 · :api :services:checkout"]

  classDef input fill:#02303A,stroke:#437291,stroke-width:1px,color:#ffffff;
  classDef hero  fill:#1BA39C,stroke:#02303A,stroke-width:2px,color:#02303A,font-weight:bold;
  classDef node  fill:#2E4B5A,stroke:#437291,stroke-width:1px,color:#ffffff;

  class W,E input;
  class P hero;
  class N1,N2,N3 node;
```

Longest-processing-time-first: sort modules by weight descending, put each on
the node with the least load so far. Deterministic — identical inputs produce
identical plans on every node, with no cross-node communication.

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

Each task name gets its own independent plan. Full reference —
`weightsFile`, `planDetail`, plan-only mode, weights file format — in
[configuration.md](docs/configuration.md).

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

Your heaviest module is the wall-time floor: no node can finish before
`:reporting` does, no matter how many nodes you add. `imbalance` is that
floor divided by the mean — at `2.30x`, the heaviest module takes more than
twice the average, so nodes past the third mostly idle. To go faster, split the
heaviest module rather than adding nodes.

Read-only: the task inspects weights and never runs a test.

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
