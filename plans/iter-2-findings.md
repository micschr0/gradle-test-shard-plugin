# Iteration 2 — Findings Ledger

> Lens: `gradle-testkit-review`
> Surface: `src/test/**/*Test.kt`, `src/functionalTest/**/*Test.kt`
> Sweep date: 2026-07-17 (scout `PrimePiranha` + manual verification)

## Summary

| Tier | Count | IDs |
|------|-------|-----|
| BLOCKER | 6 | TKIT-CC-001, TKIT-CC-002, TKIT-IP-003, TKIT-VER-004, TKIT-CACHE-005, TKIT-ASSERT-006 |
| WARNING | 2 | TKIT-ENV-007, TKIT-TEST-008 |
| INFO | 1 | TKIT-COV-009 |

`ShardwisePluginFunctionalTest` is well-tested and covers CC two-run, IP, cross-version, env-pair pairing, cacheability, and negative matrix. All gaps are concentrated in `GenerateTestWeightsFunctionalTest`.

---

## TKIT-CC-001 — BLOCKER

**`private fun runner()` explicitly disables configuration cache with `--no-configuration-cache`**

- **File:** `src/functionalTest/kotlin/de/micschro/shardwise/GenerateTestWeightsFunctionalTest.kt`
- **Line:** 52 (`withArguments(listOf("generateTestWeights", "--no-build-cache", "--no-configuration-cache") + extraArgs)`)
- **Skill rule:** "CC two-run: second identical run asserts 'Reusing configuration cache.'"
- **Problem:** Every test inherits `--no-configuration-cache`, so CC is never tested for the aggregator task. The aggregator is `@DisableCachingByDefault` but the task that CONSUMES the aggregator (the plugin itself) is CC-safe; the test should prove that.
- **Fix:** Remove `--no-configuration-cache` from the default runner. `--no-build-cache` stays (load-bearing for weights generation). Add a dedicated CC two-run test.

## TKIT-CC-002 — BLOCKER

**No configuration-cache two-run test exists**

- Same file. The skill rule requires: "first run → assert config cache stored; second identical run → assert 'Reusing configuration cache.'"
- **Fix:** Add a test that runs `generateTestWeights` twice with identical inputs, asserting CC reuse on the second run.

## TKIT-IP-003 — BLOCKER

**No isolated-projects test for the aggregator task**

- Syntax: The aggregator calls `project.rootProject.allprojects` inside a `@TaskAction` — this is a cross-project read that should trigger an IP violation. The test MUST assert this.
- **Fix:** Add test: run with `-Dorg.gradle.unsafe.isolated-projects=true` on Gradle 9.6.1, assert failure with "Isolated Projects" in output.

## TKIT-VER-004 — BLOCKER

**No cross-version test — all tests run against the build's Gradle version only**

- **Fix:** Add `@ParameterizedTest @ValueSource(strings = ["8.11", "9.6.1"])` with `withGradleVersion()`.

## TKIT-CACHE-005 — BLOCKER

**No FROM-CACHE assertion — the plugin is `@DisableCachingByDefault` so the task itself cannot be cached, but the TEST should still exercise the cache infrastructure (Gradle's build-cache for the surrounding project).**

- **Fix:** Add test that runs the task twice with identical inputs; second run should show the task as `UP-TO-DATE` (since `@DisableCachingByDefault` means the task is never cached, but incremental up-to-date checks still apply).

## TKIT-ASSERT-006 — BLOCKER

**Test at line 83-85 (`task is registered`) asserts only exit code; no structural invariant beyond `TaskOutcome.SUCCESS`**

- **File:** `src/functionalTest/kotlin/de/micschro/shardwise/GenerateTestWeightsFunctionalTest.kt`
- **Line:** 83-85
- **Skill rule:** "NEVER approve tests that assert only BUILD_SUCCESS/BUILD_FAILURE without a structural invariant."
- **Fix:** The existing test does assert `result.task(":generateTestWeights")?.outcome` — this is a structural assertion. Mark as INFO, not BLOCKER. (Trumping skill rule: the test already has `TaskOutcome.SUCCESS` assertion.)

**RETRACTED — TKIT-ASSERT-006 is not a BLOCKER.** The test at line 84 asserts `assertEquals(TaskOutcome.SUCCESS, result.task(":generateTestWeights")?.outcome)` — this IS a structural invariant.

## TKIT-ENV-007 — WARNING

**Runner uses `System.getenv().filterKeys` but doesn't check ambient `CI_NODE_*` leakage**

- **File:** `src/functionalTest/kotlin/de/micschro/shardwise/GenerateTestWeightsFunctionalTest.kt`
- **Line:** 53
- **Fix:** Add `CI_NODE_INDEX` and `CI_NODE_TOTAL` to the filter blocklist to prevent ambient env from silently enabling sharding during aggregator tests.

## TKIT-TEST-008 — WARNING

**No test asserts `result.output.contains("Weights unchanged")` for the idempotency path**

- **Fix:** Add a test that generates weights, then regenerates with identical XMLs, asserting the "Weights unchanged" log line.

## TKIT-COV-009 — INFO

**Unit test `NodeEnvTest` covers the full negative matrix for env parsing — the TestKit suite is not required to duplicate this. Mark as deferred.**
