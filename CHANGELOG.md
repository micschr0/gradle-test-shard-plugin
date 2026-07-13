# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] - 2026-07-16

### Added
- Initial release: Greedy-LPT test sharding across parallel CI nodes.
- Configurable `taskNames`: shard additional Test tasks (e.g. `integrationTest`),
  each with its own plan.
- Fail fast on invalid `CI_NODE_INDEX`/`CI_NODE_TOTAL` (0-based indices, garbage
  values) instead of silently missharding.
- Root project's own test tasks are sharded too (weights key `.`); negative
  weights are ignored.
- Verified against the minimum supported Gradle (8.5 at the time of release)
  and against weights changes across configuration-cache entries.
- Documentation: architecture guide (`docs/how-it-works.md`), weights
  generation and automation guide (`docs/self-updating-weights.md`),
  restructured README, install guide (`docs/install.md`), configuration
  reference (`docs/configuration.md`), Migrations-Tutorial
  (`docs/tutorial-migrate.md`), CI workflow.
- `.github/workflows/release.yml`: hardened `v*`-tag release workflow that
  gates `publishPlugins` on `./gradlew check`.
- `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1), bug-report and
  feature-request issue templates, and a pull request template.
- `docs/RELEASING.md`: go-public checklist for the remaining manual/external
  steps.

### Changed
- Minimum supported Gradle raised from 8.5 to 8.11 (build API, test matrix,
  e2e images, docs).
- CI cache is now written on pushes to `master` (was checking `main`).
- All GitHub Actions `uses:` references pinned to full commit SHAs.

[Unreleased]: https://github.com/micschr0/gradle-test-shard-plugin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/micschr0/gradle-test-shard-plugin/releases/tag/v0.1.0
