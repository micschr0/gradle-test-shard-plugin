<div align="center">

[![Shardwise](https://img.shields.io/badge/⚡_SHARDWISE-02303A?style=for-the-badge&labelColor=02303A&color=1BA39C)](https://plugins.gradle.org/plugin/de.micschro.shardwise)

# Shardwise

**Balance Gradle test shards by measured runtime, not module count.**

[![CI](https://img.shields.io/github/actions/workflow/status/micschr0/gradle-test-shard-plugin/ci.yml?branch=main&style=flat-square&logo=githubactions&logoColor=white&label=CI&labelColor=02303A&color=1BA39C)](https://github.com/micschr0/gradle-test-shard-plugin/actions/workflows/ci.yml)
[![Version](https://img.shields.io/github/v/tag/micschr0/gradle-test-shard-plugin?style=flat-square&logo=gradle&logoColor=white&label=version&labelColor=02303A&color=1BA39C&sort=semver)](https://github.com/micschr0/gradle-test-shard-plugin/releases)
[![Scorecard](https://img.shields.io/ossf-scorecard/github.com/micschr0/gradle-test-shard-plugin?style=flat-square&logo=securityscorecard&logoColor=white&label=Scorecard&labelColor=02303A&color=1BA39C)](https://scorecard.dev/viewer/?uri=github.com/micschr0/gradle-test-shard-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-8.11%2B-437291?style=flat-square&logo=gradle&logoColor=white&labelColor=02303A)](https://gradle.org)
[![JDK](https://img.shields.io/badge/JDK-17%2B-437291?style=flat-square&logo=openjdk&logoColor=white&labelColor=02303A)](https://openjdk.org)
[![License](https://img.shields.io/github/license/micschr0/gradle-test-shard-plugin?style=flat-square&labelColor=02303A&color=8A9BA3)](LICENSE)

</div>

---

## Why runtime, not module count

One node running everything, or N nodes split by hand — both leave time on the table.

```text
BY MODULE COUNT — wall 2440ms
  node 1  ████████████████████ 2440
  node 2  ████████████░░░░░░░░ 1430
  node 3  ████████░░░░░░░░░░░░  940

BY MEASURED WEIGHT — wall 1840ms
  node 1  ███████████████░░░░░ 1840
  node 2  ████████████░░░░░░░░ 1490
  node 3  ████████████░░░░░░░░ 1480
```

`█` testing · `░` idle. One module can outweigh five. **25% faster wall time.**

> [!TIP]
> Multi-module builds only. A single module doesn't shard —
> use Gradle's `maxParallelForks` instead.

<sub>Illustrative. On one node today? [How many nodes](#how-many-nodes) shows the speedup.</sub>

---

## Get started

```kotlin
// root build.gradle.kts
plugins {
  id("de.micschro.shardwise") version "0.4.1"
}
```

```bash
./gradlew test --no-build-cache                   # real timings, not cached
./gradlew generateTestWeights                     # writes test-weights.properties
git add test-weights.properties && git commit     # commit it — nodes need identical input

CI_NODE_TOTAL=3 CI_NODE_INDEX=1 ./gradlew test    # sharded
```

> [!WARNING]
> `CI_NODE_INDEX` is **1-based**. On 0-based CI (GitHub Actions matrix,
> CircleCI), add 1. Unset locally = every test runs.

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'ui-monospace, monospace','lineColor':'#437291','primaryColor':'#02303A','primaryTextColor':'#ffffff','primaryBorderColor':'#1BA39C'}}}%%
flowchart LR
  W["test-weights.properties"] --> P["Greedy-LPT<br/>planner"]
  E["CI_NODE_INDEX<br/>CI_NODE_TOTAL"] --> P
  P --> N1["node 1 · :checkout :domain"]
  P --> N2["node 2 · :reporting"]
  P --> N3["node 3 · :web :api :core"]

  classDef input fill:#02303A,stroke:#437291,stroke-width:1px,color:#ffffff;
  classDef hero  fill:#1BA39C,stroke:#02303A,stroke-width:2px,color:#02303A,font-weight:bold;
  classDef node  fill:#2E4B5A,stroke:#437291,stroke-width:1px,color:#ffffff;

  class W,E input;
  class P hero;
  class N1,N2,N3 node;
```

Same inputs, same plan on every node. No coordinator. Set the two env vars per CI job.

Config-cache safe. No SaaS, no network, no telemetry. Back out by deleting one line.
Every module runs on exactly one node — never zero, so CI can't go green on untested
code ([coverage beats balance](docs/how-it-works.md#1-coverage-beats-balance)).

<sub>Provider snippets → [install.md](docs/install.md).</sub>

---

## How many nodes

```bash
./gradlew shardwiseAnalyze
```

All on one node → add nodes, wall time drops. Heaviest module is the floor.

```text
  1 node   ████████████████████████  4810ms
  2 nodes  ████████████             2405ms
  3 nodes  █████████                1840ms  ◄ floor
  6 nodes  █████████                1840ms  ◄ no gain
```

`◄ floor` = heaviest module (`:reporting`). Past it, nodes idle — split it to go lower.

---

## Per-node output

Every sharded node prints its own plan at build start:

```text
── SHARDWISE · test ──────────────

  Node          1 of 3
  Running here  2 of 6 modules
  Skipped here  4 (on other nodes)

  Modules   :services:checkout
            :common:domain

──────────────────────────────────
```

---

## Docs

| Page | Covers |
|------|--------|
| [Install](docs/install.md) | Apply, configure tasks, any CI provider |
| [Self-updating weights](docs/self-updating-weights.md) | Generate + auto-refresh `test-weights.properties` |
| [Migration](docs/tutorial-migrate.md) | Step-by-step, from hand-rolled sharding |
| [Configuration](docs/configuration.md) | `shardwise {}`, `PlanDetail`, plan-only, weights format |
| [How it works](docs/how-it-works.md) | Greedy-LPT, 4/3 bound, coverage guarantee, rationale |
| [Troubleshooting](docs/troubleshooting.md) | Common CI and dev issues |

Shards `test`, `integrationTest`, or any `Test` task — independent plan each.

<sub>Pre-1.0: API may change between releases. See [CHANGELOG](CHANGELOG.md).</sub>

---

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md) · [SUPPORT.md](SUPPORT.md) · [SECURITY.md](SECURITY.md) · single maintainer.

**License:** [Apache-2.0](LICENSE)
