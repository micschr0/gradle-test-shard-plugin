# CLAUDE.md

Gradle root plugin that shards a multi-module build's test tasks across parallel CI
nodes (`CI_NODE_INDEX`/`CI_NODE_TOTAL`) via Greedy-LPT bin-packing. See README for
consumer-facing usage.

## Commands

**Always run Gradle in Docker.** Do not invoke `./gradlew` on the host — the host may
have no JDK, and the container pins the JDK version the build expects. The named volume
persists the dependency cache across runs; without it every run re-downloads the world.

gradle() {
  docker run --rm --network host -v "$PWD":/w -w /w \
    -v gradle-shardwise-cache:/home/gradle/.gradle \
    gradle:8.11-jdk17 ./gradlew --no-daemon "$@"
}

gradle test              # unit tests (pure core logic)
gradle functionalTest    # TestKit tests: real multi-module builds, CC matrix
gradle check             # both + apiCheck + validatePlugins — run before committing
gradle apiDump           # regenerate api/*.api after ANY public API change

The container needs network egress to resolve plugins on the first run — pass
`--network host` when the sandbox blocks default bridge networking.


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
- Built against the **minimum supported Gradle API** (`dev.gradleplugins:gradle-api:8.11`,
  `compileOnly`); `gradleApi()` is deliberately stripped from the `api` configuration.
  Don't use Gradle APIs newer than 8.11 in main sources.

## Known trap for future work

Sharding arbitrary task names (planned v0.2): lifecycle tasks (`assemble`, `build`,
`check`) are empty containers — `onlyIf` skips only the container while `dependsOn`
work still runs. `ShardwisePlugin` warns when a graph task named in `taskNames` has
no actions, but the warning is advisory only: the `dependsOn` work still runs
unsharded on every node. Only concrete work tasks (`Test`, docker/publish tasks)
are meaningfully shardable.
