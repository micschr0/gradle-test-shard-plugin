> **Parking status (2026-07-19):** LOW PRIORITY · OPTIONAL · INDEFINITELY DEFERRED.
> ktlint's `no-unused-imports` rule (see `03-ponytail-cleanup`) closes the immediate gap that motivated this proposal.
> No trigger to pick this up unless: (a) stable detekt 2.0 lands AND ktlint proves insufficient, OR (b) broader detekt type-resolution rules become needed.
> Do **not** action without explicit owner request.
# detekt 2.0 Upgrade — Proposal

> **Status:** proposal (not yet specced). Captured here so the rationale and block conditions survive across sessions.

## Intent

Upgrade detekt from `1.23.8` (last stable, built against Kotlin 2.0.21) to `2.0.0-alpha.5` (2026-06-17, includes Kotlin 2.4.0 support via [PR #9218](https://github.com/detekt/detekt/pull/9218)) so that the type-resolution variants `detektMain`/`detektTest` stop crashing and structural rules (`UnusedImports`, `UnusedPrivateMember`, `UnusedPrivateClass`, etc.) work again.

## Background (from 2026-07-19 session)

- `detekt` base task runs **without** type resolution → cannot detect `UnusedImports`.
- `detektMain`/`detektTest` (type resolution) **crash** on Kotlin 2.4.0 (`DescriptorUtilKt.findPackage`).
- Fix is only in detekt `2.0.0-alpha.5`; no stable 1.24 backport exists.
- Workaround in place: `ktlint 14.2.0` with `no-unused-imports` enabled (see `03-ponytail-cleanup/SPEC.md` Tier 2) catches dead imports without type resolution.

## Scope (proposal — to be refined in spec phase)

### In Scope

- `gradle/libs.versions.toml`: `detekt = "2.0.0-alpha.5"` (or later stable 2.x when released).
- `config/detekt/detekt.yml`: migrate YAML schema to 2.0 (rule IDs and property names changed).
- `build.gradle.kts`: wire `detektMain`/`detektTest`/`detektFunctionalTest` into `check` (currently only base `detekt` runs).
- CI: confirm green run with the new version before merging.

### Out of Scope

- Replacing detekt with diktat or another linter (evaluated and rejected in 2026-07-19 session).
- Enabling new detekt 2.0 rules beyond the existing config surface (separate cleanup change).

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `2.0.0-alpha.5` is alpha quality | Medium | Pin to exact version; run full `./gradlew check` and `functionalTest` matrix before merge; be ready to revert. |
| Config migration introduces regressions | Medium | Diff old vs new rule set; disable any newly-defaulted rules that conflict with existing style. |
| Type-resolution tasks find new violations | High | Triage findings: fix legitimate ones, suppress false positives with `@Suppress` annotations (not blanket config disables). |
| Stable 2.0 release lands shortly after upgrade | Low | Acceptable — semantic-version migration path within 2.x is smoother than 1.x→2.x. |

## Dependencies

- `org.jlleitschuh.gradle.ktlint:14.2.0` (already in place from `03-ponytail-cleanup`) remains as complementary unused-import checker during and after migration.

## Trigger / Acceptance Criteria

- `detektMain`, `detektTest`, `detektFunctionalTest` tasks run without crash on Kotlin 2.4.0.
- All three tasks wired into `check`.
- Full `./gradlew check` green.
- Document any rule migrations in the apply-phase progress file.

## Open Questions (to resolve in spec phase)

- Wait for stable 2.0.0 release, or adopt `2.0.0-alpha.5` now?
- Migrate config manually or use detekt's `--generate-config` migration helper (if it exists for 1.x→2.x)?
- Should `detektFunctionalTest` be added as a separate task, or does the plugin auto-discover the `functionalTest` source set?

## References

- detekt 1.x → 2.x migration guide: <https://detekt.dev/docs/intro/migration>
- detekt changelog: <https://detekt.dev/changelog/>
- PR #9218 (Kotlin 2.4.0 support): <https://github.com/detekt/detekt/pull/9218>
- `plans/2026-07-17-features/03-ponytail-cleanup/SPEC.md` — ktlint workaround rationale.

---

**Date:** 2026-07-19
**Origin:** `plans/2026-07-17-features/03-ponytail-cleanup/SPEC.md` (detekt note).
