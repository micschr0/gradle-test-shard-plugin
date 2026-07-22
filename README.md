<div align="center">

# Shardwise

**Your CI has 3 nodes. One finishes in 40s and waits 4 minutes for the slow one.**
**Shardwise packs test modules by measured runtime, so they all finish together.**

[![CI](https://github.com/micschr0/gradle-test-shard-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/micschr0/gradle-test-shard-plugin/actions/workflows/ci.yml)
[![Version](https://img.shields.io/gradle-plugin-portal/v/de.micschro.shardwise?label=version&color=blue)](https://plugins.gradle.org/plugin/de.micschro.shardwise)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/micschr0/gradle-test-shard-plugin/badge)](https://scorecard.dev/viewer/?uri=github.com/micschr0/gradle-test-shard-plugin)
[![License](https://img.shields.io/github/license/micschr0/gradle-test-shard-plugin)](LICENSE)

</div>

---

## The problem, in one picture

Same 12-module suite. Same 3 nodes. Same bill.
`█` testing · `░` idle but paid for

```text
SPLIT BY MODULE COUNT                             SPLIT BY MEASURED WEIGHT
  node 1  ████████████████████  2440ms              node 1  ███████████████░░░░░  1840ms
  node 2  ████████████░░░░░░░░  1430ms              node 2  ████████████░░░░░░░░  1490ms
  node 3  ████████░░░░░░░░░░░░   940ms              node 3  ████████████░░░░░░░░  1480ms
          └─────────────────┘                               └────────────┘
          wall  2440ms                                      wall  1840ms   25% faster
```

Four modules per node looks fair. It isn't — one module can outweigh five.

<sub>Illustrative; your numbers depend on how your suite is distributed.</sub>

---

## What it looks like on a node

Every sharded node prints its own plan at build start:

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

No guessing which node ran what. It's in the log.

---

## 60-second start

```kotlin
// root build.gradle.kts
plugins {
  id("de.micschro.shardwise") version "0.3.0"
}
```

```bash
./gradlew test --no-build-cache     # 1. run the suite once
./gradlew generateTestWeights       # 2. aggregate timings → test-weights.properties (commit it)

CI_NODE_TOTAL=3 CI_NODE_INDEX=1 ./gradlew test   # 3. that's sharding. done.
```

Then set the same two env vars per CI job. That's the whole integration.

```text
        CI_NODE_INDEX / CI_NODE_TOTAL          your CI already has these
                     │
                     ▼
 test-weights.properties ──► Greedy-LPT planner ──► node 1: :checkout :domain
      (committed)              (deterministic)      node 2: :reporting
                                                    node 3: :web :api :core
```

Every node computes the *same* plan from the same inputs. No coordinator, no lock, no shared state.

<sub>Requires Gradle 8.11+, Java 17+, a multi-module build. Applied to the root project — module builds untouched.</sub>

---

## Why not just split by module count?

|  | Count-based | **Shardwise** |
|---|---|---|
| Balances by | how *many* modules | how *long* they take |
| Slow node | drags the whole build | packed first, LPT |
| Needs a SaaS account | — | **no** |
| Uploads your build data | — | **no** |
| Coordinator between nodes | sometimes | **no** — deterministic |
| Cost | your CI bill | **free, Apache-2.0** |
| Backing out | rewrite the split | **delete one line** |

---

## The guarantee that matters

> The worst outcome for a sharding tool isn't a slow build.
> It's a module that runs on **zero** nodes — CI reports green for code nobody tested.

Every default errs toward *running*:

```text
 ✓  module missing from weights file   →  runs (gets defaultWeight)
 ✓  weights file stale or absent       →  runs (falls back to count-based)
 ✓  module missing from every plan     →  runs (coverage beats balance)
 ✗  CI_NODE_INDEX=0, "abc", or > total →  build FAILS immediately, loudly
```

Plus: every module lands on **exactly one** node, no duplication. No `afterEvaluate`, no eager task realization — configuration-cache safe. No network calls, no telemetry.

<sub>One thing the build can't check: weights must come from a trusted source — committed, or produced in the same pipeline. See [Divergent weights](docs/troubleshooting.md#divergent-weights).</sub>

---

## How many nodes should you buy?

```bash
./gradlew shardwiseAnalyze
```

A module never splits, so your heaviest one is a hard floor on wall time:

```text
  1 node   ████████████████████████████████  4810ms
  2 nodes  ████████████████                  2405ms
  3 nodes  ████████████                      1840ms   ← floor (:reporting)
  6 nodes  ████████████                      1840ms   ← same wall, 2× the bill
                                             ▲
                                             └─ :reporting alone = 1840ms
```

Three nodes is this build's sweet spot. A fourth costs money and saves nothing — split `:reporting` to go lower.

---

## Docs

**Starting out?** [Install](docs/install.md) → [Self-updating weights](docs/self-updating-weights.md)
**Replacing hand-rolled sharding?** [Migration tutorial](docs/tutorial-migrate.md)

| Page | What it covers |
|------|---------------|
| [Installation and CI setup](docs/install.md) | Apply the plugin, configure tasks, wire up any CI provider |
| [Self-updating weights](docs/self-updating-weights.md) | Generate `test-weights.properties` and refresh it automatically |
| [Migration tutorial](docs/tutorial-migrate.md) | A real migration, step by step |
| [Configuration reference](docs/configuration.md) | `shardwise {}` extension, `PlanDetail`, plan-only mode, weights format |
| [How it works](docs/how-it-works.md) | Greedy-LPT, the 4/3 approximation bound, design rationale |
| [Troubleshooting](docs/troubleshooting.md) | Diagnostics for common CI and dev issues |

Shard `test`, `integrationTest`, or any custom `Test` task — each gets its own independent plan.

<sub>Pre-1.0: the API may change between releases. The [CHANGELOG](CHANGELOG.md) records every break.</sub>

---

## Contributing

Bug fixes and improvements welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for setup and tooling.
Maintained by a single author: [SUPPORT.md](SUPPORT.md) for expectations, [SECURITY.md](SECURITY.md) for vulnerability reporting.

**License:** [Apache-2.0](LICENSE)

<!-- ci: doc-skip round 3 -->
