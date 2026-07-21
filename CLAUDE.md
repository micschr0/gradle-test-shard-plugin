# CLAUDE.md — Shardwise Conventions

Gradle root plugin that shards a multi-module build's test tasks across parallel CI
nodes via Greedy-LPT bin-packing. See `README.md` for consumer-facing usage.

## NEVER

- NEVER modify `api/gradle-test-shard-plugin.api` without running `./gradlew apiDump`
- NEVER add public symbols outside `de.micschro.shardwise` — use `internal/`
- NEVER use `System.getenv()` in `src/main` — use the `ValueSource` API
- NEVER use `afterEvaluate` or `projectsEvaluated` — breaks configuration cache
- NEVER use Gradle APIs newer than 8.11 in `src/main` (compiles against `gradle-api:8.11`)
- NEVER skip `functionalTest` for changes to `ShardwisePlugin`, planner services, or `NodeEnvValueSource`
- NEVER batch unrelated changes in one commit — use work-unit-commits (setup / fix / verify)
- NEVER add `Co-Authored-By` to commits

## ALWAYS

- ALWAYS use conventional commit messages (`type: subject`)
- ALWAYS update `CHANGELOG.md` `[Unreleased]` for user-visible changes
- ALWAYS add `@since` annotation to new public API
- ALWAYS run `./gradlew check` before saying "done"
- ALWAYS put planning logic in pure core (`internal/TestShardPlanner`, `internal/TestWeights`)
- ALWAYS put tests at cheapest layer: unit → ProjectBuilder → functional

## Build Commands

```bash
# Sandbox-specific — use natively, not Docker:
GRADLE_USER_HOME=./.gradle-cache \
  ./gradlew test --no-daemon \
  --no-build-cache --no-configuration-cache \
  --project-cache-dir ./.gradle-cache/cache

GRADLE_USER_HOME=./.gradle-cache \
  ./gradlew functionalTest --no-daemon \
  --no-build-cache --no-configuration-cache \
  --project-cache-dir ./.gradle-cache/cache

GRADLE_USER_HOME=./.gradle-cache \
  ./gradlew check --no-daemon \
  --no-build-cache --no-configuration-cache \
  --project-cache-dir ./.gradle-cache/cache

# After any public API change:
./gradlew apiDump
```

`check` runs: detekt + ktlint (`no-unused-imports` only) + unit tests + functional tests +
kover (minBound 80%) + apiCheck + validatePlugins.
`functionalTest` takes ~6 min 25 s. CI needs ≥7 min wall-time.

## Architecture

**Version:** `0.4.0` | **Plugins:** `java-gradle-plugin` + `com.gradle.plugin-publish` 2.1.1
**Kotlin:** 2.4.0 | **Gradle:** 9.6.1 | **JVM Toolchain:** 17 | **Min Gradle API:** 8.11

Two layers, keep them separated:

```text
Public API    de.micschro.shardwise         ShardwisePlugin, ShardwiseExtension, PlanDetail
              ────────────────────────────────────────────────────────────────────────────
Gradle Glue   .internal                     ShardPlannerService, ShardNodeEnvService
                                            GenerateTestWeights, NodeEnvValueSource
              ────────────────────────────────────────────────────────────────────────────
Pure Core     .internal                     TestShardPlanner, TestWeights, PlanRenderer
                                            PlanDump, NodeEnv, TestModule, ShardPlan
```

**Public surface:** 22 lines in `api/gradle-test-shard-plugin.api`, frozen via `explicitApi()` and
`binary-compatibility-validator`. Everything unlisted is `internal`.

## Hard Invariants

- **Coverage beats balance.** Unknown modules, unknown task names, missing/stale weights must
  default to *running*. Every module runs on exactly one node; all nodes derive identical plan
  from identical inputs (deterministic planner via sorted input).
- **Configuration-cache safety.** No `afterEvaluate`/`projectsEvaluated`. Env access only through
  `ValueSource`. Lazy wiring only (`configureEach`, `onlyIf`, providers).
- **BuildService split:** `ShardPlannerService` (invalidated by `[defaultWeight, weightsText,
  taskModulePaths, nodeTotal]`) + `ShardNodeEnvService` (invalidated only by `[nodeIndex]`).
  Changing `CI_NODE_INDEX` reuses the planner cache.
- **Test environment:** `functionalTest` blocklists `CI_NODE_INDEX`/`CI_NODE_TOTAL` in the
  runner's `filterKeys` to prevent accidental sharding inside aggregator tests.
- **Coverage gate:** kover enforces ≥80% line coverage. Deliberate drops need the threshold
  adjusted and justified in the commit message.

## Tooling

| Tool | What it catches | Status |
|------|-----------------|--------|
| detekt 1.23.8 | `MaxLineLength`, `MagicNumber`, `ReturnCount`, `ForbiddenComment` | base task — no type resolution (broken on Kotlin 2.4.0) |
| ktlint 14.2.0 | `no-unused-imports` only (all other rules disabled) | active in `check` |
| kover 0.9.9 | Line coverage ≥80% | `koverVerify` in `check` |
| binary-compat-validator 0.18.1 | API surface frozen | `apiCheck` in `check` |
| actionlint | CI workflow validation | manual |
| zizmor | GHA hardening | `zizmor.yml` |

detekt type-resolution tasks (`detektMain`/`detektTest`) crash on Kotlin 2.4.0.
Fix is in detekt `2.0.0-alpha.5`. See `plans/2026-07-17-features/04-detekt-2-upgrade/PROPOSAL.md`
(parked, low priority).

## Confirmed Antipatterns (from audit, 2026-07-17)

| ID | Antipattern | Fix |
|----|-------------|-----|
| GRADLE-CC-001 | Raw `System.*` read inside `whenReady` | `project.providers.systemProperty(...)` |
| GRADLE-API-002 | Dead imports | Remove — ktlint catches now |
| GA-PERSIST-* | `actions/checkout` without `persist-credentials: false` | Add `with:` block |
| DOC-LINK-001 | Double-relative GitHub links | Absolute URL |

**Work-unit-commit discipline:** Setup / findings / fix / verify each in its own commit.

## History

v2 milestone (2026-07-18) split `ShardBuildService` into `ShardPlannerService` +
`ShardNodeEnvService` with independent CC cache keys. Ponytail cleanup (2026-07-19)
removed dead code, folded constants, inlined locals — net -9 LOC. ktlint added for
`no-unused-imports` detection. Full changelog: `CHANGELOG.md` and `plans/archive/`.
