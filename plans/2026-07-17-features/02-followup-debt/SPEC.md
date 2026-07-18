# Audit Followup Debt — Durable Spec
## Intent

Close the **8 remaining audit findings** from the 2026-07-17 review and the **F1 final-verify** item, in three dependency-tier phases. Cleanup before adding new features.

## Scope

### In Scope — Tier 1A (now)

| # | Source | File | Verifier |
|---|--------|------|----------|
| 1 | F1-final-verify | `e2e/scripts/mutation-test-core.sh` | `bash e2e/scripts/mutation-test-core.sh` exit 0, ≥3/5 caught (current 4/5). Goal: 5/5 with perl-based substitution. |
| 2 | GRADLE-DOC-004 | `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt` | **[closed]** verify-only: `// @since 0.1.0` already present on class KDoc |
| 3 | GA-PERM-004 | `.github/workflows/ci.yml` | **[closed]** commit `6f82e5c` — top-level `permissions: {}` + per-job `contents: read` |
| 4 | TKIT-ENV-007 | `src/functionalTest/kotlin/.../GenerateTestWeightsFunctionalTest.kt` | **[closed]** commit `8432cba` — env-filter blocklist contains `CI_NODE_INDEX` + `CI_NODE_TOTAL` |
| 5 | F5 (catalog deps) | `build.gradle.kts` + `gradle/libs.versions.toml` | `./gradlew apiCheck` clean (binary compat stable) |
| 6 | F8 (CLAUDE.md retrospective) | `CLAUDE.md` | append "Audit Lessons (Follow-up Debt)" section listing the 8 findings cross-referenced to this spec |

### In Scope — Tier 1B (next session)

| # | Finding | File |
|---|---------|------|
| 7 | TKIT-IP-003 | **[closed]** commit `116e081` — `plugin is incompatible with isolated projects by design` in `ShardwisePluginFunctionalTest.kt` pinned to Gradle 9.6.1, asserts `-Dorg.gradle.unsafe.isolated-projects=true` failure path |
| 8 | TKIT-VER-004 | **[closed]** commit `027e9b1` — `works across supported Gradle versions` `@ParameterizedTest @ValueSource(strings = ["8.11", "8.14.3", "9.6.1"])` in `ShardwisePluginFunctionalTest.kt`. Midpoint 8.14.3 covers audit's "8.5" intent (mid-range between min 8.11 and current 9.6.1). |
| 9 | TKIT-CACHE-005 | **[closed]** commit `d79ac5e` — `configuration cache is reused on second run` asserts `Reusing configuration cache.` on identical second run in `ShardwisePluginFunctionalTest.kt`. |
| 10 | F3 (zizmor refresh) | `zizmor.yml` suppressions re-baselined |

### In Scope — Tier 2 (post)

| # | Finding | Notes |
|---|---------|-------|
| 11 | GA-HARDEN-005 | `step-security/harden-runner` SHA-verified before pin |
| 12 | F2 (JUnit mutation) | Separate change with architecture debate: PIT vs Kotest-mutation; council resolves |
| 13 | F6 (Renovate issue workflow) | new `renovate-digest.yml` |

### Out of Scope

- New `ShardwiseExtension` properties (would grow `api/*.api`).
- Any change to the planner algorithm.
- GA-META-011 (codeql.yml `workflow_dispatch` only) — repo-stated pre-condition (private + GHAS off) not met.
- F4 (`gradle/build-cache` step in CI), F7 (Composite Action bundling) — polish, optional.

## Affected Areas

| File | Tier 1A Find |
|------|-------------|
| `e2e/scripts/mutation-test-core.sh` | verified (no edit, expect 5/5) |
| `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt` | GRADLE-DOC-004 |
| `.github/workflows/ci.yml` | GA-PERM-004 |
| `src/functionalTest/kotlin/de/micschro/shardwise/GenerateTestWeightsFunctionalTest.kt` | TKIT-ENV-007 |
| `build.gradle.kts`, `gradle/libs.versions.toml` | F5 |
| `CLAUDE.md` | F8 |

| File | Tier 1B Find |
|------|-------------|
| `src/functionalTest/kotlin/de/micschro/shardwise/ShardwisePluginFunctionalTest.kt` | TKIT-IP-003 (closed `116e081`), TKIT-VER-004 (closed `027e9b1`), TKIT-CACHE-005 (closed `d79ac5e`) |
| `zizmor.yml` | F3 |

## Public API Decision

No public-API change. All edits are metadata, lifecycle, test-side, or workflow-config changes. `api/gradle-test-shard-plugin.api` remains unchanged.

## Approach (Tier 1A procedural)

Per item: **codegraph review** of affected symbols → **fix** → **same-lens re-review** → **gate verify** (`./gradlew check apiCheck` + `shellcheck` + `actionlint` where relevant) → **work-unit commit**. Per-item commit per work-unit-commits skill: setup / findings / fix are separate.

Locked:
- **No verify-rule activation for kover** in Plan 6 reports-only mode. Activation belongs to Tier 2 after 30 days of stable reports.
- **No SHA guessing**: any GH Action SHA pin comes from `gh api repos/<owner>/<repo>/git/refs/tags/<tag>` per `github-actions-hardening` rule 1.

## Testing Strategy

Strict TDD active. Tier 1A test additions: **none** (config/lifecycle fixes). Tier 1B follows `ShardwisePluginFunctionalTest` / `GenerateTestWeightsFunctionalTest` patterns already proven in the audit.

Mutation: shell-script PASS = ≥3/5 caught; threshold unchanged. F1-final-verify's goal is 5/5 with perl-based substitution.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Perl-based mutation script misses an edge case on M5 | Med | F1-final-verify is the **first** step; if 5/5 not reached, fix-then-re-run before moving on |
| `permissions: {}` lock-down breaks a job that reads PRs or labels | Low | check + e2e-glci both only need `contents: read`; release unchanged |
| Catalog dep migration silently downgrades `compileOnly(gradleApi)` | Low | `./gradlew apiCheck` is ground truth |
| New mutation-test side effects (`.out` files in source tree) | Low | `restore_originals` trap + final `diff -q` |

## Rollback

Each tier's commits are independent and revertable. Tier 1A as a series is fully revertable. Tier 1B + 2 additive, each item single-commit scope.

## Dependencies

None new. All work uses already-declared plugins.

## Success Criteria (Tier 1A)

- [ ] F1-final-verify: `bash e2e/scripts/mutation-test-core.sh` exit 0 (≥3 caught; > preferred). Defer to fresh session — perl @ARGV commit `4d45f38` still 0/5, root cause needs different script.
- [x] GRADLE-DOC-004: `// @since 0.1.0` present on `ShardwisePlugin` class KDoc
- [x] GA-PERM-004: top-level `permissions: {}`; per-job `contents: read` on check + e2e-glci (commit `6f82e5c`)
- [x] TKIT-ENV-007: env-filter blocklist contains `CI_NODE_INDEX` and `CI_NODE_TOTAL` (commit `8432cba`)
- [ ] F5: `libs.versions.toml` `[libraries]` holds the 4 dependency versions; no inline version literals remain in `build.gradle.kts` (apart from known-hardcoded JVM-target). Defer to fresh session — `libs.gradleApi` access path not yet proven, do one dep at a time.
- [x] F8: `CLAUDE.md` has "Audit Lessons (Follow-up Debt)" section (commit `486b883`)
- [ ] `./gradlew check apiCheck` green
- [ ] `actionlint .github/workflows/*.yml` zero findings
- [ ] `shellcheck e2e/scripts/*.sh` zero findings
- [ ] Test count ≥ 104 (current baseline; no regressions)
- [ ] `git push origin main` succeeds, 6+ commits ahead of `origin/main` — current state at HEAD is 1 commit ahead (all of audit + Tier 1A pushed)
- [x] TKIT-IP-003: isolated-projects test present (commit `116e081`)
- [x] TKIT-VER-004: cross-version parameterized test present (commit `027e9b1`)
- [x] TKIT-CACHE-005: cache-reuse assertion present (commit `d79ac5e`)

## Status

- **Propose:** DONE (2026-07-17)
- **Spec:** DONE (this file)
- **Tasks:** Per-item breakdown in §Scope above; per-file tasks in plan-of-records `plans/2026-07-17-features/02-followup-debt/`
- **Apply:** Tier 1A + Tier 1B effectively DONE — Tier 1A in commits `6f82e5c`/`8432cba`/`486b883`/`04bf5ec`; Tier 1B in commits `116e081`/`027e9b1`/`d79ac5e`. Remaining: F1-final-verify (mutation script), F3 (zizmor refresh), F5 (catalog migration finalization).

---

**Date:** 2026-07-17
**Durable storage:** `plans/2026-07-17-features/02-followup-debt/SPEC.md` (this file)
**Convention rationale:** documented in CLAUDE.md per F8 §x.x below.
