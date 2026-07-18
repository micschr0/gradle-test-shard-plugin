# Iteration 1 — Findings Ledger

> Lens: `gradle-plugin-review`
> Surface: `src/main/**`, `build.gradle.kts`, `api/`, `settings.gradle.kts`, `gradle.properties`
> Sweep date: 2026-07-17

## Summary

| Tier | Count | IDs |
|------|-------|-----|
| BLOCKER | 1 | GRADLE-CC-001 |
| WARNING | 2 | GRADLE-API-002, GRADLE-CC-003 |
| INFO | 1 | GRADLE-DOC-004 |

---

## GRADLE-CC-001 — BLOCKER

**CC-unfriendly `System.getProperty` inside `taskGraph.whenReady` callback**

- **File:** `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt`
- **Line:** 142 (in `dumpPlans`); reinforced at line 168 (`System.console()` in `logPlan`)
- **Skill rule:** "NEVER approve `afterEvaluate { }`, `project.container()`, `System.getProperty()`, or `System.getenv()` called during configuration phase."
- **Why this matters:** `taskGraph.whenReady` runs after configuration but before execution. Configuration Cache considers `whenReady` body as configuration work — it is re-run on cache replay. A raw `System.getProperty` inside it escapes the `providers` system, so:
  - Changing `shardwise.planDump` does NOT cause a configuration-cache miss (CC never sees the read).
  - On cache-hit runs, the property is re-read every invocation, but the cached config fingerprint is stale relative to env.
  - This is the documented antipattern in architecture standard 0006 (`use-of-provider-apis-in-gradle.md`).
- **Fix:**
  - Replace `System.getProperty("shardwise.planDump")` with `project.providers.systemProperty("shardwise.planDump").getOrNull()`, captured into a `Provider<String?>` during `apply()` and materialized inside `whenReady`.
  - Replace `System.console() != null` with `project.providers.environmentVariable("TERM")` derivation, or accept as cosmetic by moving the read into `logPlan` only (which runs at task time — fine).
- **Verification:** add a TestKit test that runs with `-Dshardwise.planDump=<tmp>` and asserts the file appears with the rendered plan.

## GRADLE-API-002 — WARNING

**Dead import: `org.gradle.api.logging.Logger` in `ShardBuildService`**

- **File:** `src/main/kotlin/de/micschro/shardwise/internal/ShardBuildService.kt`
- **Line:** 3 (`import org.gradle.api.logging.Logger`)
- **Why:** Scanned the file; no `Logger` reference exists. The class inherits `org.gradle.api.services.BuildService` whose `getLogger()` returns the right `Logger` if ever needed. Dead import clutters the API surface and trips detekt bans depending on config.
- **Fix:** remove the import.

## GRADLE-CC-003 — WARNING

**`System.console()` direct read in `logPlan` (same surface as 001)**

- **File:** `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt`
- **Line:** 168 (`val renderer = PlanRenderer(ansi = System.console() != null)`)
- **Why:** Same class as 001 — direct `System.*` read in a configuration-time callback. Lower-priority because colour is a cosmetic concern and `System.console()` does not impact correctness.
- **Fix:** same — use a Provider (`providers.environmentVariable("COLOR")`, `providers.gradleProperty("shardwise.ansi")`, or a CLI flag Provider from `providers.provider { ... }`). Bundled with the 001 fix.

## GRADLE-DOC-004 — INFO

**`@since` missing on class-level declarations**

- **File:** `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt`
- **Line:** 23 (the KDoc of `ShardwisePlugin` has `@since 0.1.0`, but the class declaration at line 25 has none; same observation for `ShardBuildService` which is `internal` and so exempt).
- **Why:** `explicitApi()` is active. Public class-level `@since` aids both `binary-compatibility-validator` and downstream consumers.
- **Fix:** add `@since 0.1.0` to the class's KDoc.
- **Status:** defer to iter 7 (binary-compat lens).

---

## Files NOT touched (out of scope for iter 1)

- `src/main/kotlin/de/micschro/shardwise/internal/PlanRenderer.kt` — pure rendering, no Provider/CC concerns. Bytes-modifying would invalidate golden e2e logs.
- `src/main/kotlin/de/micschro/shardwise/internal/GenerateTestWeights.kt` — task-action-time only, `@DisableCachingByDefault` is intentional. Reviewed for `XXE` hardening — present at lines 165–169 (`disallow-doctype-decl`, `external-general-entities`, `external-parameter-entities`, `isXIncludeAware = false`, `isExpandEntityReferences = false`). ✓
- `src/main/kotlin/de/micschro/shardwise/internal/TestShardPlanner.kt` — pure planner, no plugin API touchpoints.
- `src/main/kotlin/de/micschro/shardwise/internal/PlanDump.kt`, `NodeEnvValueSource.kt` — read in iter 1.
