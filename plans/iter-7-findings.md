# Iteration 7 — Findings Ledger

> Lens: `gradle-plugin-review` (binary-compat + dependency hygiene)
> Surface: `api/*.api`, `build.gradle.kts`, `renovate.json5`
> Sweep date: 2026-07-17

## Summary

| Tier | Count | IDs |
|------|-------|-----|
| BLOCKER | 0 | — |
| WARNING | 1 | GRADLE-DEP-001 |
| INFO | 2 | GRADLE-API-002, GRADLE-REN-003 |

---

## GRADLE-DEP-001 — WARNING

**Kotlin compiler flag `-Xjvm-default` deprecated in 2.4.0**

- **File:** `build.gradle.kts:16`
- **Evidence:** `w: -Xjvm-default is deprecated. Use -jvm-default instead.`
- **Fix:** Replace `-Xjvm-default=all` with `-jvm-default=all`.

## GRADLE-API-002 — INFO

**api/gradle-test-shard-plugin.api matches source — zero drift.**

## GRADLE-REN-003 — INFO

**No `libs.versions.toml` — inlined deps. Functional; defer to post-audit.**
