# Tasks: shardwise-v2

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~660 LOC (Tier 1B tests: ~150, weights-generation: ~250, BuildService split: ~200, catalog: ~30, docs: ~50; GA-HARDEN-005 deferred to separate change) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 → PR 4 → PR 5 |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main|feature-branch-chain|size-exception|pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Tier 1B test additions (TKIT-IP-003, TKIT-VER-004, TKIT-CACHE-005) | PR 1 | `./gradlew functionalTest --tests "ShardwisePluginFunctionalTest.isolatedProjectsExpectedFailure"` | Real scenario command | Remove `isolatedProjectsExpectedFailure` test + restore old behavior |
| 2 | weights-generation task (pure aggregator + Gradle glue) | PR 2 | `./gradlew shardwiseGenerateWeights` | Real scenario command | Remove task registration + restore Python script in README |
| 3 | BuildService split (ShardPlannerService + lazy injection) | PR 3 | `./gradlew test functionalTest --configuration-cache` | Real scenario command + CC harness | Revert to single ShardBuildService + restore TODO comment |
| 4 | Catalog migration (F5) only — GA-HARDEN-005 deferred to separate change | PR 4 | `./gradlew check apiCheck` | `gradle check apiCheck` | Revert `libs.versions.toml` swaps + restore inline versions |
| 5 | Docs + CLAUDE.md retrospective (F8) | PR 5 | `./gradlew javadoc` | `gradle javadoc` | Remove retrospective section, restore old CLAUDE.md content |

## Phase 1: Foundation / Infrastructure

- [ ] 1.1 Create `src/main/kotlin/de/micschro/shardwise/internal/ShardPlannerService.kt` with `internal abstract class ShardPlannerService : BuildService<ShardPlannerService.Params>` and `Params` interface containing `defaultWeight`, `weightsText`, `taskModulePaths` as `Property`/`MapProperty`.
- [ ] 1.2 Create `src/main/kotlin/de/micschro/shardwise/internal/GenerateTestWeights.kt` with `internal abstract class GenerateTestWeights : DefaultTask()` reading JUnit XML from `Test.reports.junitXml.outputLocation` dirs.
- [ ] 1.3 Create unit test class `internal/GenerateTestWeightsTest.kt` for weights aggregation (test root-key derivation as `.`, sorted-descending output, single/multi-module scenarios).
- [ ] 1.4 Create unit test class `internal/ShardPlannerServiceTest.kt` for planner state (test lazy plan computation, empty/single-module plans, immutability).
- [ ] 1.5 Update `gradle/libs.versions.toml` to add remaining library versions to `[libraries]` table (F5).

## Phase 2: Core Implementation

- [ ] 2.1 RED: Add test to `ShardwisePluginFunctionalTest.kt` for `isolatedProjectsExpectedFailure` under `withGradleVersion("9.6.1")` asserting failure with `-Dorg.gradle.unsafe.isolated-projects=true` (TKIT-IP-003).
- [ ] 2.2 RED: Add parameterized test to `ShardwisePluginFunctionalTest.kt` with `@ValueSource(strings = ["8.11", "9.6.1", "8.5"])` asserting plugin works on all three versions (TKIT-VER-004).
- [ ] 2.3 RED: Add test to `ShardwisePluginFunctionalTest.kt` for `cached output is byte-identical` asserting JUnit XML diff empty on two runs (TKIT-CACHE-005).
- [ ] 2.4 GREEN: Implement `isolatedProjectsExpectedFailure` test failure handling in `ShardwisePlugin.kt` (early-return or assertion in `apply()`).
- [ ] 2.5 GREEN: Implement `@ParameterizedTest` cross-version test runner in `ShardwisePluginFunctionalTest.kt` verifying plugin compatibility.
- [ ] 2.6 GREEN: Implement cache-assertion test asserting no JUnit XML changes between runs.
- [ ] 2.7 RED: Add unit test to `GenerateTestWeightsTest.kt` for root-key derivation (`.`) and sorted-descending output.
- [ ] 2.8 RED: Add unit test to `GenerateTestWeightsTest.kt` for malformed XML handling (empty file, no test suites).
- [ ] 2.9 GREEN: Implement `internal/TestWeights.parse(dirs: List<Directory>): Map<String, Long>` aggregating JUnit XML timings per module.
- [ ] 2.10 GREEN: Implement `internal/TestWeights.toModules(paths: List<String>, weights: Map<String, Long>, defaultWeight: Int): List<TestModule>` deriving module keys with root-project fix (`.`) and sorting descending.
- [ ] 2.11 GREEN: Implement `GenerateTestWeights` task registration in `ShardwisePlugin.kt` with `outputFile` set to `ext.weightsFile` and `outputs.file(outputFile)`.
- [ ] 2.12 GREEN: Replace Python weight script in `README.md:193-208` with `gradlew shardwiseGenerateWeights` task usage documentation.
- [ ] 2.13 RED: Add functional test to `GenerateTestWeightsFunctionalTest.kt` asserting CC engages for `shardwiseGenerateWeights` task (verify CC stored + merged weights file correct).
- [ ] 2.14 RED: Add functional test to `GenerateTestWeightsFunctionalTest.kt` asserting CC engages with two services when `nodeTotal > 1` (verify "Configuration cache entry stored" output).
- [ ] 2.15 GREEN: Strip `CI_NODE_INDEX`/`CI_NODE_TOTAL` from `GenerateTestWeightsFunctionalTest` runner environment using `filterKeys { it !in listOf("CI_NODE_INDEX", "CI_NODE_TOTAL") }` (TKIT-ENV-007).
- [ ] 2.16 RED: Add test to `ShardPlannerServiceTest.kt` asserting CC engages with two services when `nodeTotal > 1`.
- [ ] 2.17 RED: Add test to `ShardPlannerServiceTest.kt` asserting `PlannerResult` is immutable and `Serializable`.
- [ ] 2.18 GREEN: Implement `ShardPlannerService.planFor(taskName: String): ShardPlan?` returning immutable `PlannerResult` computed once and cached.
- [ ] 2.19 GREEN: Update `ShardBuildService` to accept `provider: Provider<ShardBuildService.PlannerResult>` for lazy planner state injection (no `afterEvaluate`, no eager constructor params).
- [ ] 2.20 RED: Add test to `ShardwisePluginFunctionalTest.kt` asserting GA-PERM-004 top-level `permissions: {}` in `ci.yml`.
- [ ] 2.21 RED: Add test to `ShardwisePluginFunctionalTest.kt` asserting GA-PERM-004 `actions/checkout` step has `persist-credentials: false`.
- [ ] 2.23 RED: Add test to `ShardwisePluginFunctionalTest.kt` asserting catalog migration completeness (`gradle check apiCheck` passes + no inline versions in `build.gradle.kts`).
- [ ] 2.24 GREEN: Update `ci.yml` top-level permissions to `permissions: {}` with per-job opt-ins only (GA-PERM-004).
- [ ] 2.25 GREEN: Update `ci.yml`, `codeql.yml`, `release.yml` `actions/checkout` steps to include `persist-credentials: false` (GA-PERSIST already applied to `ci.yml`; verify coverage in `codeql.yml`/`release.yml`).
- [ ] 2.27 GREEN: Replace inline dependency versions in `build.gradle.kts` with `libs.X` references (catalog migration F5).
- [ ] 2.28 RED: Add test to `GenerateTestWeightsFunctionalTest.kt` asserting no ambient `CI_NODE_*` leak on runner.
- [ ] 2.29 GREEN: Add `@since 0.1.0` class-level KDoc to `ShardwisePlugin.kt` (GRADLE-DOC-004).
- [ ] 2.30 GREEN: Append "Audit Lessons (Follow-up Debt)" section to `CLAUDE.md` listing the 8 open findings + fix strategies (F8).

## Phase 3: Integration / Wiring

- [ ] 3.1 GREEN: Verify `ShardwisePlugin.registerGenerateTestWeights()` correctly writes merged `test-weights.properties` to `ext.weightsFile` with correct content (root-key as `.`, sorted-descending).
- [ ] 3.2 GREEN: Verify `ShardPlannerService` lazy plan computation doesn't create cyclic dependency graph (no `afterEvaluate`, lazy provider injection).
- [ ] 3.3 GREEN: Verify CC engages with two services when `nodeTotal > 1` (assert "Configuration cache entry stored" in output).
- [ ] 3.4 GREEN: Verify CC engages for `shardwiseGenerateWeights` task (assert CC stored + correct weights file).
- [ ] 3.5 GREEN: Verify `ci.yml` passes `actionlint` with zero findings (GA-PERM-004). `codeql.yml`/`release.yml` GA-PERM-004 coverage already verified in earlier iterations.
- [ ] 3.6 GREEN: Verify `gradle check apiCheck` passes with zero errors (`api/*.api` unchanged).
- [ ] 3.7 GREEN: Verify `shellcheck e2e/scripts/*.sh` passes with zero findings.
- [ ] 3.8 GREEN: Verify `bash e2e/scripts/mutation-test-core.sh` exits 0 with ≥3 of 5 mutations caught.
- [ ] 3.9 GREEN: Verify test count ≥ 104 (maintain baseline).
- [ ] 3.10 GREEN: Verify no source files left mutated post-mutation-run (`diff -q` clean).
- [ ] 3.11 GREEN: Verify all 8 audit findings in this milestone marked "fixed" with a commit SHA in the exploration table.

## Phase 4: Testing / Verification

- [ ] 4.1 RED: Write `@ParameterizedTest @ValueSource(strings = ["8.11", "9.6.1", "8.5"])` cross-version test in `ShardwisePluginFunctionalTest.kt` asserting plugin works on all versions.
- [ ] 4.2 GREEN: Verify `./gradlew functionalTest --configuration-cache` passes with two services when `nodeTotal > 1`.
- [ ] 4.3 GREEN: Verify `./gradlew shardwiseGenerateWeights` task writes correct merged weights (root-key as `.`, sorted-descending).
- [ ] 4.4 GREEN: Verify `./gradlew check apiCheck` passes with catalog migration complete.
- [ ] 4.5 GREEN: Verify all functional tests pass with CC enabled (no `--no-configuration-cache` needed).

## Phase 5: Cleanup

- [ ] 5.1 RED: Add `@since 0.1.0` class-level KDoc to `ShardwisePlugin.kt` (GRADLE-DOC-004).
- [ ] 5.2 RED: Add `@since 0.1.0` method-level KDocs where missing (API spec compliance).
- [ ] 5.3 GREEN: Update `api/*.api` if any new public API surfaces are exposed (none expected).
- [ ] 5.4 GREEN: Verify `gradle javadoc` generates clean output with all `@since` tags visible.
- [ ] 5.5 RED: Update `docs/self-updating-weights.md:13-33` with `gradlew shardwiseGenerateWeights` task usage (Python removed).
- [ ] 5.6 GREEN: Run `./gradlew clean build --no-daemon --stacktrace` to ensure final build passes.

## Threat Matrix Tasks

N/A — design recorded `N/A` for all threat-matrix rows (no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundaries in this change).
