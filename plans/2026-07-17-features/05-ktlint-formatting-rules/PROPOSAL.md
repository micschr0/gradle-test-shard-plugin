# ktlint Formatting Rules — Proposal

> **Status:** proposal (not yet specced). Captured here so the rule-set decision is documented.

## Intent

Extend the currently minimal ktlint configuration (only `no-unused-imports` enabled) to enforce consistent code formatting across the codebase. Today ktlint is scoped narrowly to fill the dead-import gap left by broken detekt type resolution; this change would promote it to a first-class formatter.

## Background (from 2026-07-19 session)

- Initial ktlint adoption (`03-ponytail-cleanup/SPEC.md` Tier 2) ran ktlint with all standard rules enabled → ~350 violations across the tree (trailing commas, indentation, chain-method-continuation, etc.).
- Decision at the time: disable all standard rules, enable only `no-unused-imports`. Formatting deferred.
- This proposal picks up the deferred formatting work.

## Scope (proposal — to be refined in spec phase)

### In Scope

- Audit the ~350 violations from the initial ktlint run (cached in session artifacts; re-run `ktlintCheck` with full rules to regenerate).
- Decide which standard rules to enable (candidates: `trailing-comma-on-declaration-site`, `trailing-comma-on-call-site`, `indentation`, `no-wildcard-imports`, `no-blank-line-before-rbrace`, `no-consecutive-blank-lines`, `no-empty-class-body`).
- Apply `ktlintFormat` to auto-fix mechanical violations, hand-fix the rest.
- Update `.editorconfig` with the chosen rule set.
- Commit formatting as a dedicated commit (separate from logic changes) for clean diff review.

### Out of Scope

- Adding ktlint as a pre-commit hook (separate tooling change).
- Adopting ktlint's experimental ruleset (defer until stable).
- Replacing detekt for structural rules (detekt stays; ktlint is complementary).

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Auto-format introduces semantic regressions | Low | Run full `./gradlew check` after `ktlintFormat`; review diff manually for suspicious changes. |
| Rule set too aggressive, generates churn on every commit | Medium | Start with the smallest useful set (trailing commas + indentation); add more incrementally. |
| Conflicts with IntelliJ default formatter | Medium | Document `.editorconfig` settings; align IntelliJ Kotlin style to ktlint where possible. |
| Large formatting-only commit obscures `git blame` | High | Use `git blame --ignore-rev` after merge; document the formatting commit SHA in `CLAUDE.md`. |

## Dependencies

- `org.jlleitschuh.gradle.ktlint:14.2.0` (already in place).

## Trigger / Acceptance Criteria

- Chosen rule set enabled in `.editorconfig`.
- `./gradlew ktlintCheck` green across all source sets.
- Formatting-only commit merged with documented `--ignore-rev` SHA.
- `CLAUDE.md` updated with formatting commit reference.

## Open Questions (to resolve in spec phase)

- Which subset of standard rules? Minimal (3-5) or comprehensive (all ~30)?
- Adopt `trailing-comma` rules now that Kotlin 2.4 fully supports them?
- Run `ktlintFormat` once and commit, or set up a pre-commit hook for incremental adoption?

## References

- ktlint rules reference: <https://pinterest.github.io/ktlint/latest/rules/standard/>
- `plans/2026-07-17-features/03-ponytail-cleanup/SPEC.md` — initial ktlint adoption rationale.

---

**Date:** 2026-07-19
**Origin:** `plans/2026-07-17-features/03-ponytail-cleanup/SPEC.md` (deferred formatting).
