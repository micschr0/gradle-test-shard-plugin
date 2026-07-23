# Changelog

All notable changes to this project are documented here. This project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Pre-1.0, the public
API may change between minor versions; every break is listed under its release.

## [0.4.3](https://github.com/micschr0/gradle-test-shard-plugin/compare/v0.4.2...v0.4.3) (2026-07-23)


### 🐛 Bug Fixes

* **deps:** update dependency net.jqwik:jqwik to v1.10.1 ([#25](https://github.com/micschr0/gradle-test-shard-plugin/issues/25)) ([54e1095](https://github.com/micschr0/gradle-test-shard-plugin/commit/54e1095c66ed422f93793f9b8774980bd7c6c4d7))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.14.4 ([#26](https://github.com/micschr0/gradle-test-shard-plugin/issues/26)) ([0671bf4](https://github.com/micschr0/gradle-test-shard-plugin/commit/0671bf43e32f8d1eb4dd6ecc2dba9533465fff48))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v6 ([#27](https://github.com/micschr0/gradle-test-shard-plugin/issues/27)) ([4c07de7](https://github.com/micschr0/gradle-test-shard-plugin/commit/4c07de7a756daada359d9762cbb531dfea8f28b4))


### 🔧 CI

* fix renovate app-token permission input name ([#28](https://github.com/micschr0/gradle-test-shard-plugin/issues/28)) ([76dfe79](https://github.com/micschr0/gradle-test-shard-plugin/commit/76dfe79ffa943f545a737d0f146c6bc8cc448668))
* fix renovate pipeline and correct the gradle-api pin policy ([#21](https://github.com/micschr0/gradle-test-shard-plugin/issues/21)) ([f04e821](https://github.com/micschr0/gradle-test-shard-plugin/commit/f04e821787e7f7154c28979591f1b47e8bb9daf5))


### 📦 Build

* **renovate:** enable GHA automerge, drop stale gitAuthor/configMigration ([#23](https://github.com/micschr0/gradle-test-shard-plugin/issues/23)) ([e058d43](https://github.com/micschr0/gradle-test-shard-plugin/commit/e058d4379ba2b9ab83d34dbbf3b6e082be15d63e))

## [0.4.2](https://github.com/micschr0/gradle-test-shard-plugin/compare/v0.4.1...v0.4.2) (2026-07-23)


### 🔧 CI

* automate releases with release-please ([#17](https://github.com/micschr0/gradle-test-shard-plugin/issues/17)) ([0977d33](https://github.com/micschr0/gradle-test-shard-plugin/commit/0977d33a42f65bbf99b519874add4ba817492047))
* drop the unused JVM setup from the e2e job ([#13](https://github.com/micschr0/gradle-test-shard-plugin/issues/13)) ([70afd64](https://github.com/micschr0/gradle-test-shard-plugin/commit/70afd64f4268e05adde80ef1abc486ca782e26d7))
* sign release jars and gate on bundle presence ([#15](https://github.com/micschr0/gradle-test-shard-plugin/issues/15)) ([1f0c0f7](https://github.com/micschr0/gradle-test-shard-plugin/commit/1f0c0f7131c5631a47fd8045eea83d42c74ba46c))
* stop linting markdown list-style and blank-lines ([#19](https://github.com/micschr0/gradle-test-shard-plugin/issues/19)) ([2e49684](https://github.com/micschr0/gradle-test-shard-plugin/commit/2e49684f7afae0f1009ba54af94385f3f43f5760))


### 📦 Build

* drop removed kotlin.incremental.useClasspathSnapshot property ([#16](https://github.com/micschr0/gradle-test-shard-plugin/issues/16)) ([057a706](https://github.com/micschr0/gradle-test-shard-plugin/commit/057a7064b14bcf977370344e61aa285d7be67591))

## [Unreleased]

### 🔧 CI

- Sign release jars: wire the Sigstore tasks into `publishPlugins` and emit the
  bundles into `build/libs`, then fail the release if any jar lacks a bundle
  (v0.4.0 and v0.4.1 shipped unsigned)

## [0.4.1] — 2026-07-22

**Breaking changes:** none.

### 📚 Documentation

- Rebuild the README: new logo and visual identity, problem-first structure,
  tightened prose across every `docs/` page
- Document the `test-weights.properties` format and its `:` → `/` key rule
- Move the shard dashboard and coverage guarantee into the reference pages
- Fix stale samples and install snippets (`shardwiseAnalyze` output, wall-time
  chart, `0.3.0` → `0.4.1`)
- Correct `how-it-works.md`: only one CI variable set without the other fails
  the build; both unset stays a no-op

### ⚙️ Miscellaneous Tasks

- Drop per-file SPDX headers; the root `LICENSE` remains authoritative

### 🔒 Security

- Publish SLSA build provenance attestations for release jars
- Reproducible jars: strip timestamps and fix entry order

### 🔧 CI

- Attach signed jars and their Sigstore bundles to each GitHub Release
- Guard releases against tag/version drift and re-publish of an existing tag
- Skip the build/test/e2e suite on documentation-only changes
- Drop the unused JDK and Gradle-cache setup from the e2e job

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
