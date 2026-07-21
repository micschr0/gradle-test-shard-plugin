# Changelog

All notable changes to this project are documented here. This project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Pre-1.0, the public
API may change between minor versions; every break is listed under its release.

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
