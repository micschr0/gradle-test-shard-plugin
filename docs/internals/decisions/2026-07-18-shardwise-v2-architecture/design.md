# Design: shardwise-v2

## Technical Approach

Unify four concurrent work streams: audit-followup-debt closure (TKIT-IP-003, TKIT-VER-004, TKIT-CACHE-005), weights-generation task (pure internal/ aggregator + Gradle glue), BuildService split (ShardPlannerService), and Gradle 9 prep (min API verification, deprecated API removal, `-Dorg.gradle.unsafe.isolated-projects=true` adoption path). GA-HARDEN-005 deferred to separate post-v2 change. Follow two-layer architecture: pure internal/ for weights aggregation and planning; Gradle glue in ShardwisePlugin and ShardBuildService/NodeEnvValueSource.

## Architecture Decisions

### Decision: Single merged weights file vs per-task-name profiles

**Choice**: One merged `test-weights.properties` per module, derived from JUnit XML timings.
**Alternatives considered**:

- Per-task-name profiles (`task1.properties`, `task2.properties`) — more complex aggregation, incompatible with frozen format.
**Rationale**: Matches existing weights-file format documented in README:189; keeps planner state simple; incremental change with zero behavioral impact on users.

### Decision: ShardPlannerService lazy injection via Provider<...>

**Choice**: Planner results passed via `Provider<ShardBuildService.PlannerResult>` from ShardPlannerService to ShardBuildService.
**Alternatives considered**:

- Constructor parameter injection — creates eager dependency graph, violates CC safety.
- Direct service lookup via `project.services` — breaks lazy wiring, violates CC safety.
**Rationale**: Matches CC-safety invariant (no afterEvaluate, lazy wiring only); keeps dependency graph acyclic; both services remain `Serializable` independently.

### Decision: Catalog migration strategy — one dep at a time with `./gradlew help` gate

**Choice**: Migrate dependencies one file at a time, verify with `./gradlew help` before proceeding.
**Alternatives considered**:

- Bulk migration of all at once — risks `libs.gradle.api` gotcha blocking build.
- Skip migration now — defers catalog coverage goal beyond v2.
**Rationale**: Avoids known `libs.gradleApi` access pattern trap (Catalog-migration gotcha in CLAUDE.md); ensures each migration step passes build before next.

## Data Flow

Weights-generation task:

```text
gradlew shardwiseGenerateWeights → ShardwisePlugin.registerGenerateTestWeights()
    → internal/GenerateTestWeights.readJunitXml(dirs) → parse XML timings
    → internal/TestWeights.toModules(paths, weights, defaultWeight) → derive modules
    → internal/TestShardPlanner.plan(modules, 1) → single-node plan
    → write test-weights.properties to ext.weightsFile
```

BuildService split (lazy injection):

```text
ShardwisePlugin.apply() → register ShardPlannerService (no NodeEnv params)
    → register ShardBuildService (usesService(ShardPlannerService::class))
    → NodeEnvValueSource.obtain() → NodeEnv (via System.getenv)
    → ShardPlannerService.plan() → planner state
    → ShardBuildService.lazy load planner state → planFor(taskName)
```

```text
ShardwisePlugin
     │
     ├─── register(ShardPlannerService) (params: planner config only)
     └─── register(ShardBuildService) (usesService(ShardPlannerService::class))
            │
            ├─── obtains NodeEnv via NodeEnvValueSource
            └─── obtains planner state via Provider<PlannerResult>
                   │
                   └─── ShardPlannerService.plan() → immutable ShardPlan
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt` | Modify | Add `@since 0.1.0` class-level KDoc; register `shardwiseGenerateWeights` task; split `usesService` calls for dual services; add `@since` tag per API spec |
| `src/main/kotlin/de/micschro/shardwise/internal/ShardBuildService.kt` | Modify | Extract NodeEnv params to NodeEnvValueSource (new `nodeEnv: Provider<NodeEnv>`); replace TODO at line 15 with implementation; keep planner params; make service CC-safe (lazy load, no `afterEvaluate`) |
| `src/main/kotlin/de/micschro/shardwise/internal/ShardPlannerService.kt` | Create | New service holding planner state only; params: `defaultWeight`, `weightsText`, `taskModulePaths`; returns immutable `PlannerResult`; lazy plan computation |
| `src/main/kotlin/de/micschro/shardwise/internal/GenerateTestWeights.kt` | Create | Pure aggregator task reading JUnit XML from `Test.reports.junitXml.outputLocation` dirs; aggregates timings per module; writes merged `test-weights.properties` to `ext.weightsFile` |
| `src/functionalTest/kotlin/de/micschro/shardwise/GenerateTestWeightsFunctionalTest.kt` | Modify | Strip ambient `CI_NODE_INDEX`/`CI_NODE_TOTAL` from runner env (filterKeys blocklist) per TKIT-ENV-007 |
| `src/functionalTest/kotlin/de/micschro/shardwise/ShardwisePluginFunctionalTest.kt` | Modify | Add `isolatedProjectsExpectedFailure` test pinning `withGradleVersion("9.6.1")` (TKIT-IP-003); add `@ParameterizedTest @ValueSource(strings = ["8.11", "9.6.1", "8.5"])` cross-version test (TKIT-VER-004); add `cached output is byte-identical` test (TKIT-CACHE-005) |
| `gradle/libs.versions.toml` | Modify | Add remaining dependency versions to `[libraries]` table (F5) |
| `build.gradle.kts` | Modify | Replace inline dep versions with `libs.X` references (F5) |
| `.github/workflows/ci.yml` | Modify | Top-level `permissions: {}` (GA-PERM-004); per-job opt-ins only |
| `README.md:193-208` | Modify | Replace Python weight script with `gradlew shardwiseGenerateWeights` task usage |
| `docs/self-updating-weights.md:13-33` | Modify | Swap Python for task in CI recipes |
| `CLAUDE.md` | Modify | Append "Audit Lessons (Follow-up Debt)" section (F8) listing 8 open findings + fix strategies |
| `api/*.api` | None | No changes (all new task lives in `internal/`) |

## Interfaces / Contracts

```kotlin
// ShardPlannerService — new service for planner state
internal abstract class ShardPlannerService : BuildService<ShardPlannerService.Params> {
    interface Params : BuildServiceParameters {
        val defaultWeight: Property<Int>
        val weightsText: Property<String>
        val taskModulePaths: MapProperty<String, List<String>>
    }
    // Lazy-loaded planner state, returned as immutable PlannerResult
    fun planFor(taskName: String): ShardPlan?
}

// ShardBuildService — modified to consume planner state via Provider
internal abstract class ShardBuildService : BuildService<ShardBuildService.Params> {
    interface Params : BuildServiceParameters {
        val nodeIndex: Property<Int>
        val nodeTotal: Property<Int>
        val plannerResult: Provider<ShardBuildService.PlannerResult>  // lazy injection
    }
    // Replaces TODO at line 15 with concrete lazy load
    val nodeTotal: Int get() = parameters.nodeTotal.get()
    fun planFor(taskName: String): ShardPlan?
    fun runsOnThisNode(taskName: String, modulePath: String): Boolean
}

// Immutable planner result for serialization
internal data class PlannerResult(
    val plans: Map<String, ShardPlan>
) : Serializable
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| **Unit** | Weights aggregation (`TestWeights.parse`, `TestWeights.toModules`) | Plain Kotlin unit tests; assert merged weights file content, root-key derivation (`.`), sorted-descending output |
| **Unit** | Shard planner logic (`TestShardPlanner.plan`) | Plain Kotlin unit tests; assert bin-packing correctness, empty/single-module cases |
| **Functional** | CC engagement with two services when `nodeTotal > 1` | Functional test asserting output contains "Configuration cache entry stored"; verify two service instances via introspection |
| **Functional** | CC engagement for `shardwiseGenerateWeights` task | Functional test asserting CC stored; verify task produces correct merged weights file |
| **Tier 1B** | `isolatedProjectsExpectedFailure` under `isolated-projects=true` | Functional test pinning 9.6.1, asserting failure with `-Dorg.gradle.unsafe.isolated-projects=true` |
| **Tier 1B** | Cross-version test (`8.11`, `9.6.1`, `8.5`) | Parameterized test asserting plugin works on all three versions |
| **Tier 1B** | Cached output byte-identical on two runs | Functional test comparing JUnit XML files before/after, asserting no changes |
| **Tier 2** | Workflow hardening passes | `actionlint .github/workflows/*.yml` zero findings; GA-PERM-004 verified |
| **Tier 2** | Catalog migration success | `./gradlew help` succeeds after each migration step; `gradle check apiCheck` green |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundaries in this change. BuildService split is service injection only; CI workflow changes use existing GitHub Actions syntax without subprocess integration.

## Migration / Rollout

No migration required — all changes are additive or internal refactors:

- Weights-generation task is additive; existing scripts deprecated in docs.
- BuildService split is additive (`ShardPlannerService` + lazy injection) with single-service fallback at `nodeTotal = 1`.
- Catalog migration is one-file-at-a-time additive; no existing APIs modified.
- CI workflow permissions are additive configuration; GA-HARDEN-005 deferred to separate change.

## Open Questions

- **F2 (JUnit mutation) JUnit-mutation architecture**: Requires expert-team council (planned for separate change); shell-script approach kept as durable enough for current size.
- **Per-task-name weight profiles**: Deferred to v3; would break frozen format or force planner rewrite.
- **F6 (Renovate digest workflow)**: Deferred to separate change; not part of this milestone.
