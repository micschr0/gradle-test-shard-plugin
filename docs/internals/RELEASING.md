# Go-public release checklist

Publish the plugin to the Gradle Plugin Portal and make the repository public.

## Prerequisites

- Push rights to the repository
- Gradle Plugin Portal API key (`GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`)
- Ability to change repository visibility

## Steps

### 1. Re-enable CodeQL triggers

`.github/workflows/codeql.yml` is `workflow_dispatch`-only while private,
because `codeql-action/analyze` fails without GitHub Advanced Security. Once
public (or GHAS-enabled), uncomment the `push`/`pull_request`/`schedule`
triggers. No other change needed.

### 2. Configure Plugin Portal publish secrets

Set repository secrets `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`
(from your Gradle Plugin Portal API key) **before** pushing a `v*` tag. The
push triggers `.github/workflows/release.yml`; its `publishPlugins` step
fails without the secrets. Alternatively, publish manually:

```bash
./gradlew publishPlugins
```

> **Note:** `$VERSION` below is the version being released (e.g. for v0.2.0, replace every `$VERSION` with `0.2.0`).

### 3. Push the v$VERSION tag

```bash
git tag -a v$VERSION -m "v$VERSION"
git push origin v$VERSION
```

Confirm the tag date matches `CHANGELOG.md`'s `[$VERSION] - <release-date>` entry.

### 4. Verify README badges resolve

Confirm the CI badge and Plugin Portal badge in `README.md`. They resolve
only after the workflow has run once on `main` and the plugin is published.

### 5. Confirm Renovate is active

Confirm Renovate is installed and picks up `renovate.json`.

### 6. Secrets history scan

Run against full git history (last scanned 2026-07-14):

```bash
docker run --rm -v "$PWD":/repo zricethezav/gitleaks:latest git /repo --log-opts="--all"
```

**Result: clean** (36 commits, ~312 KB, no leaks). Re-run before going public
if new commits have landed.

### 7. Action SHA-pin follow-up

All `uses:` references in `ci.yml`, `codeql.yml`, and `release.yml` already
pinned to full commit SHAs. No follow-up needed.

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

