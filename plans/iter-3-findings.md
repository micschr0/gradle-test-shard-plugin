# Iteration 3 — Findings Ledger

> Lens: `github-actions-hardening`
> Surface: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `.github/workflows/codeql.yml`
> Sweep date: 2026-07-17

## Summary

| Tier | Count | IDs |
|------|-------|-----|
| BLOCKER | 0 | — |
| WARNING | 9 | GA-PERSIST-001, GA-PERSIST-002, GA-CONC-003, GA-PERM-004, GA-HARDEN-005, GA-ZIZMOR-006, GA-TIMEOUT-007, GA-CONC-008, GA-PERM-009 |
| INFO | 2 | GA-PERM-010, GA-META-011 |

All 3 workflows reviewed. No BLOCKERs found — the author already applied most hardening rules correctly. Remaining gaps are WARNING-tier additions.

---

## GA-PERSIST-001 — WARNING

**Checkout steps in ci.yml missing `persist-credentials: false`**

- **File:** `.github/workflows/ci.yml`
- **Lines:** 31 (check job), 136 (e2e-glci job)
- **Skill rule:** "Checkout: persist-credentials: false"
- **Fix:** Add `with: persist-credentials: false` to both `actions/checkout` steps.

## GA-PERSIST-002 — WARNING

**Checkout in codeql.yml missing `persist-credentials: false`**

- **File:** `.github/workflows/codeql.yml`
- **Line:** 26
- **Fix:** Same as above.

## GA-CONC-003 — WARNING

**ci.yml uses unconditional `cancel-in-progress: true`**

- **File:** `.github/workflows/ci.yml`
- **Line:** 13
- **Skill rule:** "Concurrency: cancel branches, protect main — `cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}`"
- **Why:** A push to main cancels an in-progress main build. For fast-merge repos with rare main pushes this is benign, but the guard is cheap.
- **Fix:** Change `cancel-in-progress: true` to `${{ github.ref != 'refs/heads/main' }}`.

## GA-PERM-004 — WARNING

**ci.yml `permissions: contents: read` instead of `permissions: {}`**

- **File:** `.github/workflows/ci.yml`
- **Lines:** 16–17
- **Skill rule:** "Deny-all permissions, opt-in per job — `permissions: {}`"
- **Why:** The current `contents: read` is the only permission needed, so it's equivalent — but `{}` is the denylist-first convention.
- **Fix:** Replace with `permissions: {}` and add `permissions: contents: read` at job level (both check + e2e already read-only).

## GA-HARDEN-005 — WARNING

**No `harden-runner` step in any of the 3 workflows**

- **Files:** `ci.yml`, `release.yml`, `codeql.yml`
- **Skill rule:** "harden-runner: block egress, Docker is the exception"
- **Fix:** Add `step-security/harden-runner` with `egress-policy: block` + allowed endpoints (`github.com:443`, `api.github.com:443`, `objects.githubusercontent.com:443`, `*.actions.githubusercontent.com:443`, `*.blob.core.windows.net:443`). For `ci.yml`'s e2e-glci job: add `gitlab.com:443` for glci download. For `release.yml`: add `plugins.gradle.org:443` for publish.

## GA-ZIZMOR-006 — WARNING

**No `zizmor.yml` configuration**

- **File:** (missing)
- **Skill rule:** "Config: `zizmor.yml` at repo root. Inline suppressions PROHIBITED."
- **Fix:** Create `zizmor.yml` with `persona: pedantic`. No suppressions needed yet until zizmor is run and produces findings.

## GA-TIMEOUT-007 — WARNING

**codeql.yml `analyze` job has no `timeout-minutes`**

- **File:** `.github/workflows/codeql.yml`
- **Line:** 22 (missing)
- **Skill rule:** "Timeout every job. Default 360 min is a bug."
- **Fix:** Add `timeout-minutes: 20` (CodeQL analysis is moderate-length).

## GA-CONC-008 — WARNING

**codeql.yml missing `concurrency` block**

- **File:** `.github/workflows/codeql.yml`
- **Fix:** Add standard concurrency block. Manual-only trigger makes this low-risk, but consistency matters.

## GA-PERM-009 — WARNING

**codeql.yml missing top-level `permissions: {}`**

- **File:** `.github/workflows/codeql.yml`
- **Line:** 19 (before jobs)
- **Fix:** Add `permissions: {}` at top level; job-level `permissions: contents: read, security-events: write` already present.

## GA-PERM-010 — INFO

**release.yml `publish` job has `permissions: contents: read` correct for tag-trigger — no gap**

- File: `.github/workflows/release.yml`
- The job reads checkout + publishes to Plugin Portal via key/secret (env-based). `permissions: {}` at top-level is correct; no repo-write needed.

## GA-META-011 — INFO

**codeql.yml is `workflow_dispatch:`-only**

- File: `.github/workflows/codeql.yml`
- Comment documents the intent (private repo, GHAS not enabled). Not a hardening gap per se — the comment's escape hatch says "Restore the push/pull_request/schedule triggers below once the repo is public or GHAS is enabled".
- **Recommendation:** If the repo is now public, restore triggers. Otherwise defer as documented.
