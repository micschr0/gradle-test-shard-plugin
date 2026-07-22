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

```text
              node 1        node 2        node 3     wall time
1 node        ████████████████████████                 4810 ms
3 · by count  ████████████  ████████      ████          2440 ms  ← slowest node wins
3 · by time   ██████████    █████████     █████████     1840 ms  ← packed by runtime
```

Modules packed by measured test runtime, not count. Same suite, same coverage.

---

## Get started

Record weights once locally. Set two env vars per CI job.
No coordinator: every node derives the same plan.

```kotlin
// root build.gradle.kts
plugins {
  id("de.micschro.shardwise") version "0.4.1"
}
```

```bash
# once, locally: measure real per-module timings
./gradlew test --no-build-cache
./gradlew generateTestWeights                  # writes test-weights.properties
git add test-weights.properties                # commit: every node needs identical input
```

```bash
# per CI job, the only thing CI sets
CI_NODE_TOTAL=3 CI_NODE_INDEX=1 ./gradlew test
```

> [!WARNING]
> `CI_NODE_INDEX` is **1-based**. On 0-based CI (GitHub Actions matrix,
> CircleCI), add 1. Unset locally = every test runs.

<sub>Keep weights fresh from CI instead? See [self-updating weights](docs/self-updating-weights.md).</sub>

```text
config-cache safe   ·   no SaaS · no network · no telemetry   ·   remove 1 line to revert
```

Every module runs on exactly one node, never zero
([coverage beats balance](docs/how-it-works.md#1-coverage-beats-balance)).

<details>
<summary>How the plan is built</summary>

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

</details>

<sub>Provider snippets: [install.md](docs/install.md).</sub>

---

## Confirm it sharded

Every sharded `test` run prints a banner: which node, and which modules ran
here versus skipped on other nodes. Node 1 and node 2 show different module
sets with no overlap. That is the split working.

```text
  ╭─ S H A R D W I S E ──────────────────────────────────
  │ ██████████████████████▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▒▒▒▒▒▒▒▒▒▒▒▒▒▒
  │ ██████████████████████
  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
  │ ▒▒▒▒▒▒▒▒▒▒▒▒▒▒
  ├─ test ───────────────────────────────────────────────
  │ Node          1 of 3
  │ Running here  2 of 6 modules
  │ Skipped here  4 (run on other nodes, shown as ':<module>:test SKIPPED')
  │
  │ Modules running here
  │   :services:checkout
  │   :common:domain
  ╰──────────────────────────────────────────────────────
```

All 6 modules run once across the 3 nodes. Never zero, never twice.

---

## How many nodes

```bash
./gradlew shardwiseAnalyze
```

```text
[shardwise] WEIGHTS ANALYSIS
[shardwise]   modules:   6
[shardwise]   total:     4810ms
[shardwise]   mean:      801ms
[shardwise]   median:    700ms
[shardwise]   p95:       1840ms
[shardwise]   imbalance: 2.30x
[shardwise]
[shardwise] TOP 3 HEAVIEST
[shardwise]   1. :reporting 1840ms (38.3%)
[shardwise]   2. :web 900ms (18.7%)
[shardwise]   3. :api 780ms (16.2%)
```

The heaviest module is the wall-time floor: no node finishes faster than
`:reporting` at 1840ms, whatever the node count. `imbalance` is that module
over the mean. Split the heaviest module to push the floor lower.

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

Shards `test`, `integrationTest`, or any `Test` task, one plan each.

<sub>Pre-1.0: API may change between releases. See [CHANGELOG](CHANGELOG.md).</sub>

---

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md) · [SUPPORT.md](SUPPORT.md) · [SECURITY.md](SECURITY.md) · single maintainer.

**License:** [Apache-2.0](LICENSE)
