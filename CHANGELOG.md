# Changelog

All notable changes to this project are documented here. This project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Pre-1.0, the public
API may change between minor versions; every break is listed under its release.

## [Unreleased]

## [0.4.1] — 2026-07-22

**Breaking changes:** none.

### 📚 Documentation

- Redesign README: teal palette, cohesive badge row, themed mermaid, GitHub
  alert callouts, mobile-safe charts
- Add a theme-adaptive before/after logo (`docs/assets/shardwise-logo-*.svg`)
- Restructure README: problem first, add "Is this for you?" and "Configure"
- Fix `shardwiseAnalyze` sample: add `p99`, `TOP 3` → `TOP 6`, wrong `median`
- Rescale the wall-time chart to minutes, bar lengths matching the numbers
- Document the `test-weights.properties` format and its `:` → `/` key rule
- Move the shard dashboard to `configuration.md` as `PlanDetail.FULL` output
- Move the coverage guarantee to `how-it-works.md`; link it from the README
- Fix stale install-snippet version (`0.3.0` → `0.4.1`) in `install.md` and
  `tutorial-migrate.md`

### 🔒 Security

- Publish SLSA build provenance attestations for release jars
- Reproducible jars: strip timestamps and fix entry order

### 🔧 CI

- Attach signed jars and their Sigstore bundles to each GitHub Release
- Guard releases against tag/version drift and re-publish of an existing tag
- Skip the build/test/e2e suite on documentation-only changes

### 🧪 Testing

- Fail `check` on version drift between the root and the e2e consumer

## [0.4.0] — 2026-07-22

**Breaking changes:** none.

### 📚 Documentation

- Declare configuration-cache compatibility on the plugin portal metadata
- Sharpen the plugin description and tags for portal discoverability

### 🔧 CI

- Publish a GitHub Release with changelog notes after each portal publish
- Restore Scorecard push/schedule triggers now that the repository is public

## [0.3.0] — 2026-07-21

**Breaking changes:** none.

### 🚀 Features

- Add `planOnly` mode: skip tests and log the per-module plan
- Add `shardwiseAnalyze` task: distribution statistics for the weights file

### 📚 Documentation

- Configuration reference: plan-only + analyzer sections

### 🧪 Testing

- Plan-only + analyzer functional coverage

## [0.2.0] — 2026-07-19

**Breaking changes:** none.

### 🚀 Features

- Add Shardwise test-sharding plugin
- E2e test pipeline + scenario suite
- Plugin hardening (CC + lifecycle)
- Plan dashboard
- Add generateTestWeights task

### 💼 Other

- Add DevSecOps tooling
- Bump Kotlin 2.4.10 + JUnit 6.1.2
- Version catalog, kover, Renovate

### 🚜 Refactor

- Split ShardBuildService
- Ponytail cleanup + ktlint

### 📚 Documentation

- Overhaul documentation set
- SDD retrospective + audit lessons
- Add OSS files

### 🧪 Testing

- Property + mutation + env test suite

### ⚙️ Miscellaneous Tasks

- Hardened CI/CD with OSSF Scorecard
- Docs lint workflow
- Prepare public release
