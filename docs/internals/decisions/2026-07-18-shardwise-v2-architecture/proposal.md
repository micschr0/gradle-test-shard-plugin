# Proposal: shardwise-v2

## Intent

Ship the next major version of the shardwise plugin by unifying four concurrent work streams into a single milestone: (1) residual audit-followup-debt closure (Tier 1B/2/3), (2) the `shardwiseGenerateWeights` task, (3) splitting `ShardBuildService` into two services for independent CC caches, and (4) Gradle 9 API prep. This change consolidates pending mechanical fixes, planned features, and infrastructure hardening into one versioned release, avoiding fragmentation and reducing coordination overhead.

## Scope

### In Scope

**Tier 1B — audit debt completion (now):**

- **TKIT-IP-003**: Add `isolatedProjectsExpectedFailure` test pinning `withGradleVersion("9.6.1")` asserting failure under `-Dorg.gradle.unsafe.isolated-projects=true`.
- **TKIT-VER-004**: Add `@ParameterizedTest @ValueSource(strings = ["8.11", "9.6.1", "8.5"])` covering min, current-build, and a midpoint Gradle version.
- **TKIT-CACHE-005**: Add `cached output is byte-identical` test in `GWTFunctionalTest` asserting XML diff empty on two runs.
- **F3 (zizmor.yml refresh)**: Re-run zizmor locally if available; if unavailable in CI, re-validate by running `actionlint` on all 3 workflows and adjusting suppressions.
- **F5 (catalog deps)**: Finish moving inline dep versions to `libs.versions.toml` (already partially done at commit `6ecd7ee`).
- **F8 (CLAUDE.md retrospective)**: Append "Audit Lessons (Follow-up Debt)" section listing the 8 open findings + their fix strategies; cross-reference to this OpenSpec change.

**Tier 2 — high-effort hardening (post-Tier 1B):**

- **F2 (JUnit mutation)** in a separate change with explicit architecture decision; the user explicitly noted this needs expert-team council.
- **F6 (Renovate issue workflow)**: 4th workflow `renovate-digest.yml` cron weekly, posting to a digest GH issue.
- **GA-HARDEN-005 (deferred to separate change)**: `step-security/harden-runner` adoption deferred until SHA is verified via `gh api repos/step-security/harden-runner/git/refs/tags/v<X.Y.Z>`. Ships in its own change after v2 lands.

**New feature: `shardwiseGenerateWeights` task:**

- New task `shardwiseGenerateWeights` writing a single merged `test-weights.properties` from JUnit XML timings.
- Pure `internal/` aggregator (JUnit XML parse + module-key derivation + millis render).
- CC-safe glue registration in `ShardwisePlugin` discovering `Test.reports.junitXml.outputLocation` dirs from `ext.taskNames`.
- Replace buggy Python in `README.md` and CI recipes in `docs/self-updating-weights.md`.

**BuildService split:**

- Split `ShardBuildService` into two services: `ShardBuildService` (NodeEnv + planner params) and `ShardPlannerService` (planner state).
- Enables independent CC caches when service count > 1.

**Gradle 9 prep:**

- Verify min API is still 8.11 after dependency moves.
- Remove any deprecated Gradle APIs in main sources.
- Test and document `-Dorg.gradle.unsafe.isolated-projects=true` adoption path (TKIT-IP-003 scope extension).

### Out of Scope

- New features in `ShardwiseExtension` that would expand `api/*.api` beyond current frozen set.
- Renaming the plugin's Gradle module id (defer to v3).
- Per-task-name weight profiles (would break the frozen weights-file format or force a planner rewrite).
- Removing `explicitApi()` or migrating to internal-only (defer to v3).
- Re-baselining test counts upward (tests must remain at baseline or grow).
- F2 (JUnit mutation) in this milestone; the shell-script approach is durable enough for current size with the F1 perl-based fix, but JUnit-mutation architecture needs expert-team council.
- GA-HARDEN-005 (`step-security/harden-runner`) deferred to a separate post-v2 change; SHA verification via GitHub API required before adoption. Not in this milestone.
- GA-META-011 (codeql trigger restore) — repository state (private + GHAS off) prevents trigger restore.

## Capabilities

### New Capabilities

- **`weights-generation`**: On-demand task that aggregates JUnit suite timings into the merged, per-module `test-weights.properties` (introduced by `shardwise-generate-weights` proposal).
- **`buildservice-split`**: Two independent `BuildService` instances for `ShardBuildService` (NodeEnv params) and `ShardPlannerService` (planner state), enabling independent CC caches when `nodeTotal > 1` (new).

### Modified Capabilities

- **`test-environment-isolation`**: `GenerateTestWeightsFunctionalTest` runner strips `CI_NODE_*` to prevent ambient leakage (from `audit-followup-debt` Tier 1A).
- **`ci-workflow-permissions`**: Top-level `permissions: {}` becomes the convention; per-job opt-in continues (from `audit-followup-debt` Tier 1A).
- **`plugin-public-api-doc`**: `ShardwisePlugin` class carries `@since 0.1.0` (from `audit-followup-debt` Tier 1A).
- **`gradle-version-catalog-coverage`**: `libs.versions.toml` now owns every dependency, not just plugins (from `audit-followup-debt` Tier 1A).

## Approach

Tier-driven with feature phases.

**Tier 1A + Tier 1B (audit debt, mechanical):**
Per item: code-graph review of affected symbols, fix, same-lens re-review, gate verify (`./gradlew check apiCheck` + `shellcheck` + `actionlint`), work-unit commit. Tier 1A items already closed in commits `6f82e5c`/`8432cba`/`486b883`/`04bf5ec`; Tier 1B items are new in this milestone.

**Weights-generation feature:**
Pure `internal/` aggregator (JUnit XML parse + module-key derivation + millis render). Replace buggy Python in README and docs. Keep weights-file format and planner unchanged. Use existing `weightsFile` property on `ShardwiseExtension` as output target; task class lives in `internal/` (excluded by `explicitApi()`).

**BuildService split:**
Introduce `ShardPlannerService` inheriting from `BuildService<ShardPlannerService.Params>` with planner params only. Keep `ShardBuildService` for NodeEnv params. Pass planner result via lazy injection. Functional test asserts CC engages with two services when `nodeTotal > 1`.

**Gradle 9 prep:**
Verify min API still 8.11 after catalog migration. Remove deprecated APIs (if any). Document `-Dorg.gradle.unsafe.isolated-projects=true` adoption path (already tested by TKIT-IP-003 expansion).

**Locked decisions:**

- One merged weights file summed per module (consistent with README:189).
- Suite-level timing (identical to current scripts, no user-visible weight change).
- CAUTION: F2 (JUnit mutation) is out of scope here; the shell-script approach is durable.
- CAUTION: GA-META-011 deferred indefinitely (private repo + GHAS off).
- CAUTION: Per-task-name weight profiles deferred to v3.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt` | Modified | Add class-level `@since`, register `shardwiseGenerateWeights` task, split `usesService` calls |
| `src/main/kotlin/de/micschro/shardwise/internal/ShardBuildService.kt` | Modified | Split into two services (NodeEnv vs planner params), update TODO at line 15 |
| `src/main/kotlin/de/micschro/shardwise/internal/ShardPlannerService.kt` | New | New service holding planner state only |
| `src/functionalTest/kotlin/de/micschro/shardwise/GenerateTestWeightsFunctionalTest.kt` | Modified | Strip ambient `CI_NODE_*` from runner env (TKIT-ENV-007) |
| `src/functionalTest/kotlin/de/micschro/shardwise/ShardwisePluginFunctionalTest.kt` | Modified | Add isolatedProjects test (TKIT-IP-003), cross-version test (TKIT-VER-004), cache assertion (TKIT-CACHE-005) |
| `internal/` (weights-generation task) | New | Pure aggregator task with unit tests |
| `api/*.api` | None | No public surface changes (task lives in `internal/`) |
| `gradle/libs.versions.toml` | Modified | Add remaining dependency versions to `[libraries]` table (F5) |
| `build.gradle.kts` | Modified | Replace inline dep versions with `libs.X` references (F5) |
| `.github/workflows/ci.yml` | Modified | Top-level permissions lock-down (GA-PERM-004) |
| `README.md:193-208` | Modified | Replace Python with task usage |
| `docs/self-updating-weights.md:13-33` | Modified | Swap Python for task in CI recipes |
| `CLAUDE.md` | Modified | Append retrospective section (F8) |
| `e2e/scripts/mutation-test-core.sh` | Verified | F1-final-verify step confirms perl-switch works (already fixed at `636af6c`) |
| `plans/iter-N-findings.md` (closed findings) | Unchanged | Already on disk; cross-referenced from this change |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Perl-based mutation script misses an edge case on M5 | Med | F1-final-verify step confirms 5/5 caught before moving to Tier 1B items |
| JUnit XML dir discovery unproven on 8.11; gradle/gradle#17091 `flatMap` misbehavior on `reports.junitXml.outputLocation` | Med | Read `Test.reports.junitXml.outputLocation` directly via lazy `DirectoryProperty`; verify against pinned 8.11 API before impl; functional test proves it end-to-end |
| Reference scripts hardcode task `test`; supporting `integrationTest` is new logic | Med | Iterate `ext.taskNames`, not a fixed glob; cover in unit + functional tests |
| ShardPlannerService initialization order causes CC miss | High | Lazy injection, functional test asserts CC engages with two services when `nodeTotal > 1`; no `afterEvaluate`/`projectsEvaluated` in main sources |
| New mutation-test side effects (`.out` files in source tree) | Low | `restore_originals` trap handles it; final `diff -q` asserts source unchanged |
| Catalog dep migration silently downgrades `compileOnly(gradleApi)` | Low | `./gradlew apiCheck` is the ground truth |

## Rollback Plan

Per-item, per-tier rollback.

- **Tier 1A/B items (audit debt)**: Each item is a single-commit fix; revert that commit to undo.
- **Weights-generation task**: Additive change; remove task registration and restore doc snippets.
- **BuildService split**: Revert to single `ShardBuildService`; restore TODO comment.
- **Gradle 9 prep**: Remove any deprecated API changes; revert catalog moves; keep `minGradleVersion` at 8.11.
- **F2/F6 (Tier 2)**: Not in this milestone; no rollback needed if deferred.
- **GA-HARDEN-005**: Not in this milestone; deferred to separate post-v2 change. No action here.

## Dependencies

- **None new**. All work uses already-declared plugins (`io.gitlab.arturbosch.detekt`, `org.jetbrains.kotlinx.kover`, `com.gradle.plugin-publish`, `com.github.ben-manes.versions`, `zizmor`).

## Success Criteria

- [ ] All 8 audit findings in this milestone marked "fixed" with a commit SHA in the exploration table.
- [ ] `shardwiseGenerateWeights` task writes correct merged weights, root tests keyed `.`.
- [ ] Aggregation logic is pure `internal/` with plain unit tests.
- [ ] Functional test asserts CC engages with two services when `nodeTotal > 1`.
- [ ] Functional test asserts CC engages for `shardwiseGenerateWeights` task.
- [ ] `gradle check apiCheck` green; `api/*.api` unchanged.
- [ ] `actionlint .github/workflows/*.yml` zero findings.
- [ ] `shellcheck e2e/scripts/*.sh` zero findings.
- [ ] `bash e2e/scripts/mutation-test-core.sh` exit 0 (PASS with ≥3 of 5 caught).
- [ ] Test count ≥ 104 (current baseline).
- [ ] No source file left mutated post-mutation-run (`diff -q` clean).
- [ ] Version bump to **1.0.0** (major version to align with the single-milestone unification of audit debt + feature + infrastructure + API prep).

---

**Status**: PROPOSED
**Date**: 2026-07-18
**Milestone**: shardwise-v2
