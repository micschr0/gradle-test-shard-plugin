<!-- authoring-audit: 2026-07-16 BLUF,ModePurity,ConceptBudget,Examples,AntiPatterns,Terminology -->
# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- *Nothing yet.*

## [0.2.0] - 2026-07-19

### Added

- Native root task `generateTestWeights` that aggregates JUnit XML
  `time=` attributes into `test-weights.properties`, replacing the
  three divergent `generate-test-weights.py` snippets in the docs.
- `generateTestWeights` honours `shardwise { weightsFile.set(...) }`
  when set, falling back to `<root>/test-weights.properties` by default.

### Changed

- Documentation: replaced embedded Python snippets with `./gradlew generateTestWeights`
  in `self-updating-weights.md`, `configuration.md`, and `tutorial-migrate.md`.
- `generateTestWeights` writes standard ISO-8859-1 properties with `\uXXXX`
  escapes for non-ASCII module keys (was raw UTF-8 bytes).

### Fixed

- `generateTestWeights` now keys each module by its Gradle project path
  (`shardPath`) — the exact key the planner looks up — instead of deriving the
  key from the on-disk directory layout. Previously a nested module whose
  directory did not mirror its Gradle path (custom `projectDir`, flat layout,
  or relocated `buildDir`) generated a key the planner never found, silently
  falling back to `defaultWeight` (count-based balancing).

## [0.1.0]

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

[Unreleased]: https://github.com/micschr0/gradle-test-shard-plugin/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/micschr0/gradle-test-shard-plugin/releases/tag/v0.2.0
[0.1.0]: https://github.com/micschr0/gradle-test-shard-plugin/releases/tag/v0.1.0
