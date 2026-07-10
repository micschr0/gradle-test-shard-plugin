# Changelog

## [Unreleased]
- Documentation: architecture guide (`docs/how-it-works.md`), weights generation and
  automation guide (`docs/self-updating-weights.md`), restructured README, CI workflow.

## [0.1.0]
- Initial release: Greedy-LPT test sharding across parallel CI nodes.
- Configurable `taskNames`: shard additional Test tasks (e.g. `integrationTest`), each with an independent plan.
- Fail fast on invalid `CI_NODE_INDEX`/`CI_NODE_TOTAL` (0-based indices, garbage values) instead of silently missharding.
- Root project's own test tasks are sharded too (weights key `.`); negative weights are ignored.
- Verified against the minimum supported Gradle (8.5) and against weights changes across configuration-cache entries.
