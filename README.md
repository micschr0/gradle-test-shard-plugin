# Shardwise — Gradle Plugin for Deterministic Test Sharding

[![CI](https://github.com/micschr0/gradle-test-shard-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/micschr0/gradle-test-shard-plugin/actions/workflows/ci.yml)
[![Version](https://img.shields.io/gradle-plugin-portal/v/de.micschro.shardwise?label=version&color=blue)](https://plugins.gradle.org/plugin/de.micschro.shardwise)
[![License](https://img.shields.io/github/license/micschr0/gradle-test-shard-plugin)](LICENSE)

Shardwise shards multi-module test suites across parallel CI nodes using
Greedy-LPT bin-packing. Each test module runs on exactly one shard; every other
shard skips its test task — no duplication, no lost coverage. The plugin is
configuration-cache safe, produces deterministic plans from identical inputs,
and never skips a module by accident: unknown modules always run.

Shardwise is pre-1.0 software without a SemVer commitment. The API may change
between releases before 1.0. See [docs/RELEASING.md](docs/RELEASING.md) for
the go-public timing plan.

- No network calls — Shardwise reads only `CI_NODE_INDEX` and `CI_NODE_TOTAL`
  from the environment. Unlike SaaS sharding tools, it has no data exfiltration
  surface.

## Features

- **Greedy-LPT bin-packing** — distributes test modules across shards using
  historical timing data for balanced execution
- **Deterministic planning** — same inputs always produce the same shard
  assignment
- **Configuration-cache safe** — uses `BuildService` and `ValueSource`; no
  `afterEvaluate` hacks
- **Hard coverage invariant** — unknown modules always run; stale weights
  never drop tests; every module runs on exactly one shard
- **CI-agnostic** — GitLab CI, GitHub Actions, CircleCI, Buildkite, or any
  provider that sets environment variables
- **Multi-task support** — shard `test`, `integrationTest`, or any custom
  `Test` task independently, each with its own plan
- **Optional weights file** — per-module timings in `modulePath=millis` format;
  generates from JUnit XML (see [weights file format](docs/configuration.md#weights-file-format))

## Quick Start

1. **Apply the plugin.** Add to the root `build.gradle.kts`:

   ```kotlin
   plugins {
     id("de.micschro.shardwise") version "0.1.0"
   }
   ```

2. **Run a shard locally.** Set the env vars (both must be valid 1-based
   integers ≥ 1) and execute the test suite:

   ```bash
   CI_NODE_TOTAL=2 CI_NODE_INDEX=1 ./gradlew test
   ```

   A module assigned to the other shard prints a skip line whose `onlyIf`
   reason is the plugin's per-node identifier (e.g. `Shardwise node 1/2`):

   ```
   > Task :mod-a:test SKIPPED
   Skipping task ':mod-a:test' as task onlyIf 'Shardwise node 1/2' is false.
   ```

   A module on this shard runs. With `--info` you see a line like
   `> Task :services:checkout:test` (test tasks print no PASSED suffix on
   success — only `SKIPPED`, `UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE`, or
   `FAILED`).

3. **Wire CI.** Add the same `shardwise` plugin and environment variables to
   your CI configuration. See [docs/install.md](docs/install.md) for
   provider-specific examples.

## What it does

Shardwise solves a classic scheduling problem: distribute weighted jobs across
identical CI nodes so the slowest node finishes as early as possible (minimum
makespan). It uses Greedy LPT (Longest Processing Time) bin-packing, reading a
weights file of per-module historical timings. The planner is deterministic —
identical inputs always produce identical output. See
[How it works](docs/how-it-works.md) for the algorithm and design rationale.

## Safety Guarantees

The plugin ensures that misconfiguration or stale data never silently drops
tests. Every guarantee is enforced at build time:

- **Unknown modules always run** — modules absent from the weights file get
  `defaultWeight`; modules absent from all plans default to running
- **Invalid CI variables fail the build** — non-numeric or out-of-range
  `CI_NODE_INDEX` or `CI_NODE_TOTAL` causes immediate build failure
- **Every module runs on exactly one shard** — the deterministic Greedy-LPT
  planner guarantees full coverage with no duplication
- **Weights file integrity** — the file must come from a trusted source
  (committed to repo or produced by a CI job in the same pipeline)

No network calls. Reads only `CI_NODE_INDEX` and `CI_NODE_TOTAL`. No data
exfiltration surface.

## Documentation

- [Installation and CI setup](docs/install.md) — apply the plugin, configure
  tasks, wire GitLab CI / GitHub Actions / CircleCI / Buildkite plus
  Azure DevOps / Jenkins / Travis / Bitbucket
- [Configuration reference](docs/configuration.md) — full `shardwise {}`
  extension reference, `PlanDetail` levels, weights file format
- [Self-updating weights](docs/self-updating-weights.md) — Python generator,
  scheduled bot commits, and the every-run-feeds-the-next strategy
- [How it works](docs/how-it-works.md) — algorithm design, guarantees, and
  Gradle integration details
- [Migrations-Tutorial](docs/tutorial-migrate.md) — walk through a real
  migration with Need-First, Worked Example, Fading
- [Troubleshooting](docs/troubleshooting.md) — diagnostics for the
  patterns that come up after v0.1.0 ships

## What we're working on

- Android variant tasks — shard `testDebugUnitTest` and `testReleaseUnitTest`
- Kotlin Multiplatform — shard `iosTest` alongside JVM tests
- Notes and requests at [issues](/../../issues)

This is not a commitment; priorities may shift.

## Compatibility

Gradle 8.11+ (built against the 8.11 API), Java 17+.

## Contributing

Bug fixes and improvements welcome. The project uses:

- **detekt** for Kotlin static analysis — `./gradlew check` includes it
- **ShellCheck** for shell scripts — `shellcheck e2e/*.sh e2e/scripts/*.sh`
- **actionlint** for GitHub Actions workflows
- **Renovate** for automated dependency updates
- **CodeQL** for security analysis (weekly)

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full development setup.

## License

[Apache-2.0](LICENSE)

## Don't

- Don't apply the plugin to subprojects — only the root project should declare
  the `shardwise` plugin
- Don't let parallel nodes read weights from independent cache entries —
  divergence causes modules to be skipped on every node; use committed files
  or shared pipeline artifacts instead
- Don't use `test.only()` or similar Gradle built-in filtering alongside
  Shardwise — the plugin skips entire modules, not individual tests within them

---

Verification:
[ ] BLUF — outcome in first 2 sentences
[ ] Mode Purity — exactly one Diátaxis mode (landing / overview)
[ ] Concept Budget — ≤3 new concepts per section
[ ] Examples — ≥1 per concept
[ ] Anti-patterns — ≥3 "Don't" items
[ ] Terminology — one term for concept throughout
