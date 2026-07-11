# Iteration 4 — Findings Ledger

> Lens: `authoring-docs` + qualitative DX review
> Surface: `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `docs/**`, `*.md`, issue/PR templates
> Sweep date: 2026-07-17

## Summary

| Tier | Count | IDs |
|------|-------|-----|
| BLOCKER | 0 | — |
| WARNING | 3 | DOC-LINK-001, DOC-LINK-002, DOC-TERM-003 |
| INFO | 3 | DOC-CI-004, DOC-DRIFT-005, DOC-CODEQL-006 |

All docs carry the `authoring-audit` metadata header and were already BLUF-reviewed (2026-07-16). The remaining issues are link/tool-drift.

---

## DOC-LINK-001 — WARNING

**Broken issues link in README.md**

- **File:** `README.md`
- **Line:** 117
- **Current text:** `[issues](/../../issues)`
- **Problem:** Double-relative path renders as `https://github.com/../../issues` on GitHub, which 404s. Also breaks on npm/pages mirrors.
- **Fix:** Replace with `[issues](https://github.com/micschr0/gradle-test-shard-plugin/issues)` (absolute URL) or `[issues](../../issues)` (GitHub-relative, one level up from the repo root — but absolute is safer).

## DOC-LINK-002 — WARNING

**`paths-ignore` in `ci.yml:6` excludes all `*.md` — doc-only PRs get zero CI feedback**

- **File:** `.github/workflows/ci.yml`
- **Line:** 6, 9 (`paths-ignore: ["README.md", "CHANGELOG.md", "LICENSE", "**.md"]`)
- **Problem:** If a contributor changes `docs/install.md` and opens a PR, CI silently skips all checks. Documentation correctness drifts without any automated guard rail.
- **Fix:** Remove `**,.md` from `paths-ignore`. At minimum, trigger `check` on doc changes so `shellcheck`, `actionlint`, and the e2e suite still run.
- **Deferred:** Iter-4 notes this; fix belongs to iter-3 (CI hardening) — cross-reference to `GA-META-011`.

## DOC-TERM-003 — WARNING

**Minor terminology inconsistency: "CI worker" vs "CI node"**

- **File:** `README.md`
- **Line:** 9 ("parallel CI workers") vs line 10, 75, 76 ("CI nodes")
- **Problem:** The docs consistently use "CI node" and "node" everywhere except this one occurrence. The glossary in `how-it-works.md` defines "CI node" as the canonical term.
- **Fix:** Change line 9 "parallel CI workers" → "parallel CI nodes".

## DOC-CI-004 — INFO

**`paths-ignore` excludes all md files (linked from DOC-LINK-002)**

- Note for iter-3 follow-up.

## DOC-DRIFT-005 — INFO

**CONTRIBUTING.md step 2 mermaid chart references `ShardwisePluginApplyTest` — correct at time of writing**

- File: `CONTRIBUTING.md`
- Line: 44
- Verified against `src/test/kotlin/de/micschro/shardwise/ShardwisePluginApplyTest.kt` — the test class exists and tests plugin apply + shardPath. No drift.

## DOC-CODEQL-006 — INFO

**README claims CodeQL "weekly" (line 133) but codeql.yml is `workflow_dispatch`-only**

- File: `README.md`, line 133: "**CodeQL** for security analysis (weekly)"
- Actual: `.github/workflows/codeql.yml` has `on: workflow_dispatch:` only (per its own comment, due to private-repo GHAS limitation).
- Fix: Update the README claim to "CodeQL (available on workflow_dispatch)" or similar, matching reality. Or leave the line unchanged and defer to iter-3 GA-META-011.
