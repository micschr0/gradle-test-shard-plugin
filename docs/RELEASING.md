# Go-public release checklist

Publish the plugin to the Gradle Plugin Portal and make the repository public.

## Prerequisites

- Push rights to the repository
- Gradle Plugin Portal API key (`GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`)
- Ability to change repository visibility

## Steps

### 1. Re-enable CodeQL triggers

`.github/workflows/codeql.yml` is `workflow_dispatch`-only while the repo is
private. `codeql-action/analyze` fails with "Resource not accessible by
integration" on private repos without GitHub Advanced Security. Once the repo
is public (or GHAS is enabled), uncomment the `push`/`pull_request`/`schedule`
triggers at the top of the file. No other change needed.

### 2. Configure Plugin Portal publish secrets

Set repository secrets `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`
(from your Gradle Plugin Portal API key) **before** pushing a `v*` tag. The
push triggers `.github/workflows/release.yml`; its `publishPlugins` step
fails without the secrets. Alternatively, publish manually:

```bash
./gradlew publishPlugins
```

### 3. Push the v0.1.0 tag

```bash
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

Confirm the tag date matches `CHANGELOG.md`'s `[0.1.0] - 2026-07-14` entry.

### 4. Verify README badges resolve

Confirm the CI badge and any Plugin Portal badge in `README.md` resolve
correctly. They work only after the workflow has run at least once on `master`
and the plugin is published.

### 5. Confirm Renovate is active

Confirm the Renovate GitHub App is installed on the repository and picks up
`renovate.json5`.

### 6. Secrets history scan

Result recorded during the `oss-review-fixes` change, run against full git
history:

```bash
docker run --rm -v "$PWD":/repo zricethezav/gitleaks:latest git /repo --log-opts="--all"
```

**Result: clean.** 36 commits scanned, ~312 KB scanned, no leaks found
(run 2026-07-14). Re-run before making the repository public if new commits
have landed since.

### 7. Action SHA-pin follow-up

All `uses:` references in `ci.yml`, `codeql.yml`, and `release.yml` were
resolved to full commit SHAs during the `oss-review-fixes` change (network was
available). No follow-up is outstanding.

If a future action bump needs re-pinning, resolve the new tag's commit SHA
before editing the workflow. Never hand-copy or guess a SHA:

```bash
# Peel a tag to its commit SHA (annotated tags need a second hop):
gh api repos/<owner>/<repo>/git/ref/tags/<tag>
# If "object.type" is "tag" (annotated), peel once more:
gh api repos/<owner>/<repo>/git/tags/<object.sha>
```

## Verify

- [ ] CodeQL workflow runs on push and pull_request
- [ ] Badges in README resolve correctly
- [ ] Plugin is published on the Gradle Plugin Portal
- [ ] Renovate has created its first dependency PR
- [ ] Secrets history scan is clean on latest commits

## Don't

- Don't delete and re-push the `v*` tag if the release workflow fails — the
  fix is usually missing secrets or a red `check`; re-run from Actions or
  publish manually with `./gradlew publishPlugins`.
- Don't cut a tag on a date that doesn't match `CHANGELOG.md`'s entry date —
  the tag date is canonical history; update the CHANGELOG first if the schedule
  slips.
- Don't make the repository public before re-running the secrets history scan
  if new commits have landed since the last scan — cache leaks or accidentally
  committed keys will be exposed on first push.

## Appendix (historical context — not part of the checklist)

### Formatter deferral (detekt-formatting)

`detekt-formatting` (`io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8`)
was evaluated for zero-diff integration during `oss-review-fixes`. Its first
run against the existing codebase produced 27 findings across 3 files —
mostly `ArgumentListWrapping` reformatting the multi-line argument style used
throughout `TestShardPlannerTest.kt`. This is not a zero-diff result, so the
wiring was **not** committed, per this project's "no partial/broad reformat"
rule.

If a formatter is revisited later, evaluate `detekt-formatting` again after a
deliberate, reviewed reformatting pass of existing sources, or evaluate
alternatives such as spotless+ktlint with its own zero-diff check.

### Deprecation warning (upstream, documented not fixed)

`./gradlew check --warning-mode all` under Gradle 9.6.1 reports one remaining
deprecation warning:

```
The ReportingExtension.file(String) method has been deprecated. This is
scheduled to be removed in Gradle 10.
```

Traced via `--stacktrace` to `io.gitlab.arturbosch.detekt.DetektPlugin.apply`
(detekt 1.23.8's own plugin application code, not this project's build
scripts). This is upstream-owned; no workaround is applied in this repo. Bump
detekt if a future release resolves it upstream.

---

Verification:
[ ] BLUF — outcome in first 2 sentences
[ ] Mode Purity — exactly one Diátaxis mode (How-to)
[ ] Concept Budget — ≤3 new concepts per section
[ ] Examples — ≥1 per concept
[ ] Anti-patterns — ≥3 "Don't" items
[ ] Terminology — one term per concept throughout
