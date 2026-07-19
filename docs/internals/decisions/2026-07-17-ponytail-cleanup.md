# Ponytail Cleanup + ktlint for Unused-Import Detection — Durable Spec

> **Convention:** Per repo convention (see `docs/internals/decisions/02-followup-debt/SPEC.md`), durable specs live in `plans/`. (Former `openspec/` scratch folder was removed during migration — single source of truth is now `plans/`.)
> **Spec-Driven Development:** This change is retro-spec'd. Apply ran before spec; this file captures the work for traceability per SDD workflow §Dependency Graph. Verify will run against this spec.

## Intent

Cut over-engineering surfaced by a repo-wide `ponytail-audit` (7 ranked findings) and close the detekt type-resolution gap that let dead imports slip into the tree. Pure cleanup — zero feature additions, zero public API changes.

## Scope

### In Scope (Tier 1 — code cuts, all DONE)

| # | Tag | File | Cut |
|---|-----|------|-----|
| 1 | `delete` | `internal/TestShardPlanner.kt` | `ShardPlan.runsOn` removed (prod-dead, plugins inlines the decision). Safety-net logic preserved as test-local extension in `ShardPlanTest.kt` + `TestShardPlannerTest.kt`. |
| 2 | `delete` | `internal/ShardNodeEnvService.kt` | `val nodeIndex: Int get() = parameters.nodeIndex.get()` removed (never read; plugin uses `nodeI.get()`). `Params.nodeIndex` stays as CC cache key. |
| 3 | `delete` | `internal/GenerateTestWeights.kt` | `import org.gradle.api.tasks.testing.Test` (unused; "Test" only in KDoc prose). |
| 4 | `delete` | `test/.../ShardwisePluginApplyTest.kt` | `import ...ShardNodeEnvService` (unused). |
| 5 | `yagni` | `internal/GenerateTestWeights.kt` | `@get:Internal val minWeightMillis: Int = 1` (task property for a constant) → `private const val MIN_WEIGHT_MILLIS = 1` in companion. `Internal` import dropped. |
| 6 | `yagni` | `internal/TestWeights.kt` | `toModules(... defaultWeight: Int = DEFAULT_WEIGHT)` default param dropped — both call sites pass it explicitly. Test caller updated to `TestWeights.DEFAULT_WEIGHT`. |
| 7 | `shrink` | `internal/PlanRenderer.kt` | `titledBorder` local `head` (string built, only `.length` used) inlined to length computation. |

### In Scope (Tier 2 — lint tooling, all DONE)

| # | Change | File |
|---|--------|------|
| 8 | Add ktlint 14.2.0 plugin | `gradle/libs.versions.toml`, `build.gradle.kts` |
| 9 | Enable only `no-unused-imports`, disable all other standard rules | `.editorconfig` |
| 10 | Wire `ktlintCheck` into `check` | `build.gradle.kts` |

### Out of Scope

- detekt upgrade to 2.0.0-alpha (separate change; alpha + config migration)
- Other ktlint standard rules (trailing commas, indentation, chain-method-continuation) — defer to formatting pass
- diktat evaluation
- JVM toolchain bump (investigated: `jvmToolchain(17)` is correct for plugin consumer compatibility)
- `allWarningsAsErrors` for Kotlin compiler (investigated: doesn't catch unused imports — IDE-only inspection)

## Affected Areas

| File | Change |
|------|--------|
| `src/main/kotlin/de/micschro/shardwise/internal/TestShardPlanner.kt` | Fix 1 |
| `src/main/kotlin/de/micschro/shardwise/internal/ShardNodeEnvService.kt` | Fix 2 |
| `src/main/kotlin/de/micschro/shardwise/internal/GenerateTestWeights.kt` | Fix 3 + 5 |
| `src/test/kotlin/de/micschro/shardwise/ShardwisePluginApplyTest.kt` | Fix 4 |
| `src/main/kotlin/de/micschro/shardwise/internal/TestWeights.kt` | Fix 6 |
| `src/test/kotlin/de/micschro/shardwise/internal/ShardPlanTest.kt` | Fix 1 (test helper + caller) |
| `src/test/kotlin/de/micschro/shardwise/internal/TestShardPlannerTest.kt` | Fix 1 (test helper + caller) |
| `src/main/kotlin/de/micschro/shardwise/internal/PlanRenderer.kt` | Fix 7 |
| `gradle/libs.versions.toml` | Tier 2 — `ktlint = "14.2.0"`, `ktlint` plugin alias |
| `build.gradle.kts` | Tier 2 — ktlint plugin applied, `ktlintCheck` in `check` |
| `.editorconfig` | Tier 2 — `ktlint_standard = disabled`, `ktlint_standard_no-unused-imports = enabled` |

## Public API Decision

No public-API change. All edits are internal-symbol cuts and build-config additions. `api/gradle-test-shard-plugin.api` remains unchanged.

## Approach

**Tier 1 (code cuts):** per-finding — CodeGraph + Kotlin LSP for cross-file refs (LSP unreliable here, cross-checked with full-tree grep) → cut → re-verify.

**Tier 2 (lint tooling):** investigative — root-cause detekt's failure (type-resolution crash on Kotlin 2.4.0, fix only in 2.0.0-alpha) → evaluate alternatives (ktlint, diktat, compiler warnings) → adopt ktlint with minimal rule surface (only `no-unused-imports`, all other standard rules disabled) → verify catches dead imports via test fixture.

## Testing Strategy

Strict TDD active. No new production tests (cuts only); existing test suite covers behavior. Test files rewritten for Fix 1 preserve the same assertions via a private test-local `ShardPlan.runsOn` extension that mirrors production's safety-net semantics.

**Verify gate:**
- `./gradlew detekt` — green
- `./gradlew test` — all unit tests green
- `./gradlew functionalTest` — TestKit green (CC matrix, cross-version, IP)
- `./gradlew check` — full chain incl. koverVerify + validatePlugins + ktlintCheck
- Manual fixture: file with `import java.util.HashMap` unused → `ktlintMainSourceSetCheck` fails with `Unused import`

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `ShardPlan.runsOn` removal breaks a downstream test not in this repo | Low | `ShardPlan` is `internal` — not visible outside this repo. |
| ktlint 14.2.0 has unknown Kotlin-2.4 interactions | Low | Verified: `ktlintCheck` runs green on full tree; test fixture confirms `no-unused-imports` fires correctly. |
| Disabling all ktlint standard rules hides real issues | Low | detekt still runs for structural rules; ktlint scoped to unused imports only (complementary, not replacement). |
| `minWeightMillis` removal breaks Gradle task snapshotting | None | It was `@get:Internal` (excluded from snapshotting); replacing with `const val` is a pure refactor. |

## Rollback

Each tier's commits are independent and revertable. Tier 1 cuts are per-file (8 files, ~-9 lines net). Tier 2 is additive (3 files, +6 lines).

## Dependencies

One new plugin: `org.jlleitschuh.gradle.ktlint:14.2.0` (from Gradle Plugin Portal, already resolved). No production deps added.

## Success Criteria

- [x] Fix 1: `ShardPlan.runsOn` removed from main; tests green
- [x] Fix 2: `ShardNodeEnvService.nodeIndex` getter removed; service class intact
- [x] Fix 3: dead `Test` import removed from `GenerateTestWeights.kt`
- [x] Fix 4: dead `ShardNodeEnvService` import removed from apply test
- [x] Fix 5: `minWeightMillis` const-folded to companion
- [x] Fix 6: `toModules` default param dropped
- [x] Fix 7: `titledBorder` head local inlined
- [x] ktlint 14.2.0 plugin applied and wired into `check`
- [x] `.editorconfig` enables only `no-unused-imports`
- [x] Manual fixture verifies ktlint catches unused imports
- [x] `./gradlew check` green (ktlint + detekt + tests + kover + validatePlugins)
- [x] `api/gradle-test-shard-plugin.api` unchanged

## Status

- **Propose:** DONE (2026-07-19, retro-spec)
- **Spec:** DONE (this file)
- **Tasks:** inline in §Scope above
- **Apply:** DONE (2026-07-19) — all 7 cuts + ktlint setup applied, `check` green
- **Verify:** DONE (2026-07-19) — `./gradlew check` green; manual fixture confirms unused-import detection
- **Commit:** pending — work uncommitted, awaits SDD-trail completion

---

**Date:** 2026-07-19
**Durable storage:** `docs/internals/decisions/2026-07-17-ponytail-cleanup.md` (this file)
