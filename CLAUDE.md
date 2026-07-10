# CLAUDE.md

Gradle root plugin that shards a multi-module build's test tasks across parallel CI
nodes (`CI_NODE_INDEX`/`CI_NODE_TOTAL`) via Greedy-LPT bin-packing. See README for
consumer-facing usage.

## Commands

Requires JDK 17+ on `JAVA_HOME` (no toolchain auto-provisioning configured).

```bash
./gradlew test              # unit tests (pure core logic)
./gradlew functionalTest    # TestKit tests: real multi-module builds, CC matrix
./gradlew check             # both + apiCheck + validatePlugins — run before committing
./gradlew apiDump           # regenerate api/*.api after ANY public API change
```

Functional tests are the source of truth for plugin behaviour — always run them for
changes to `ShardwisePlugin`, `ShardBuildService`, or `NodeEnvValueSource`.

## Architecture

Two layers, keep them separated:

- `internal/TestShardPlanner`, `internal/TestWeights` — **pure** (no Gradle types).
  All planning logic lives here; test it with plain unit tests.
- `ShardwisePlugin` + `internal/ShardBuildService` + `internal/NodeEnvValueSource` —
  Gradle glue only. New logic goes into the pure layer, not here.

## Hard invariants

- **Plugin ID `de.micschro.shardwise`, extension `shardwise`, and everything in
  `api/*.api` are frozen once published.** `apiCheck` gates this; only widen the API
  deliberately, never rename/remove. Kotlin `explicitApi()` is on — everything not in
  `internal/` is public API.
- **Coverage beats balance.** Unknown modules, unknown task names, missing/stale
  weights must default to *running*, never to being skipped. Every module runs on
  exactly one node; all N nodes must derive the identical plan from identical inputs
  (deterministic planner, committed/artifact weights file).
- **Configuration-cache safety.** No `afterEvaluate`/`projectsEvaluated`, env access
  only through the `ValueSource`, `ValueSource` result types implement `Serializable`,
  lazy wiring only (`configureEach`, `onlyIf`, providers). Gradle rejects
  `Property<List<T>>` in service params — use `ListProperty`/`MapProperty`/`SetProperty`.
  Every functional test asserts CC engages; keep it that way.
- Built against the **minimum supported Gradle API** (`dev.gradleplugins:gradle-api:8.5`,
  `compileOnly`); `gradleApi()` is deliberately stripped from the `api` configuration.
  Don't use Gradle APIs newer than 8.5 in main sources.

## Known trap for future work

Sharding arbitrary task names (planned v0.2): lifecycle tasks (`assemble`, `build`,
`check`) are empty containers — `onlyIf` skips only the container while `dependsOn`
work still runs. Only concrete work tasks (`Test`, docker/publish tasks) are
meaningfully shardable; validate/warn on lifecycle tasks before generalising.
