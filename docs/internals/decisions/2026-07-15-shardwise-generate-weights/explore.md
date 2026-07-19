# Exploration: shardwiseGenerateWeights task

## Current State

**Module-key derivation (the "." root key)** lives in `ShardwisePlugin.kt:169-170`,
Gradle-`Project`-typed, not in the pure layer:

```kotlin
private val Project.shardPath: String
    get() = path.removePrefix(":").replace(':', '/').ifEmpty { "." }
```

This works from `project.path`, not filesystem paths, so it can't be reused directly
by an XML aggregator. The correct filesystem-path analog is in
`docs/self-updating-weights.md:23-27`:

```python
module = f.split('/build/test-results/')[0]
if module == f:  # result lives directly under build/ → root project, key '.'
    module = '.'
```

Confirmed: `README.md:196-208`'s inline script omits the `if module == f: module = '.'`
fix — root-project timings get keyed as the raw file path, never match any module, and
silently fall back to `defaultWeight` forever. The new task must mirror the doc's fixed
logic, not the README's buggy one.

**Weights parsing (inverse direction)**: `internal/TestWeights.kt:11-23` (`parse`) is a
pure, Gradle-free `key=value` parser with no path semantics — treats keys as opaque
strings. Generation is the pure inverse: render `Map<String, Int/Long>` as sorted-desc
`key=value` lines with the `# generated from junit xml timings (millis)` header comment
(used verbatim in both README and docs).

**CC-safe wiring pattern to copy** (`ShardwisePlugin.kt:24-86`):

- `taskModulePaths` (line 42-48): `project.provider { ... }` snapshotting
  `ext.taskNames.get()` and walking `project.allprojects` filtering `Test` tasks —
  configuration-time, lazy-safe.
- `weightsText` (line 50-51): `ext.weightsFile.map { readTextOrEmpty(it.asFile) }.orElse("")`
  — lazy `Provider<String>`, actual I/O deferred to resolution time.
- `ShardBuildService` (`internal/ShardBuildService.kt`): `BuildService<Params>` where
  `Params` holds only `Property`/`MapProperty`, computation happens via `by lazy { }`
  inside the service instance — never captures raw `Project`/`Task`.
- `NodeEnvValueSource` (`internal/NodeEnvValueSource.kt:7-13`):
  `ValueSource<NodeEnv, ValueSourceParameters.None>` with `NodeEnv : Serializable` — the
  CC-safe env-access template.

No Gradle `Task` (with `@TaskAction`) exists in this plugin today — everything is
`Test`-task `onlyIf`/`usesService` wiring plus a `taskGraph.whenReady` hook.
`shardwiseGenerateWeights` would be the plugin's first real registered `Task`.

**Two-layer split**: pure `internal/TestShardPlanner`, `internal/TestWeights` (no Gradle
types) vs. `ShardwisePlugin` + `internal/ShardBuildService` + `internal/NodeEnvValueSource`
(glue) — hard rule per CLAUDE.md.

**Public API**: `explicitApi()` on; `api/gradle-test-shard-plugin.api` lists only
`PlanDetail`, `ShardwiseExtension`, `ShardwisePlugin`. No task-configuration DSL exists yet.

**Functional test pattern**
(`src/functionalTest/kotlin/de/micschro/shardwise/ShardwisePluginFunctionalTest.kt`):
`writeExampleProject()` (lines 21-54) builds a synthetic multi-module TestKit project;
`runner()` (56-67) always appends `--configuration-cache`. CC assertion is inlined
per-test (no shared helper), e.g. lines 98-102:

```kotlin
assertTrue(
    result.output.contains("Configuration cache entry stored") ||
        result.output.contains("Reusing configuration cache"),
    "configuration cache must engage"
)
```

A dedicated reuse test (`configuration cache is reused on second run`, lines 378-388)
runs twice and checks `"Reusing configuration cache."` on the second run.

## Affected Areas

- `src/main/kotlin/de/micschro/shardwise/ShardwisePlugin.kt` — register the new task,
  wire discovery of `Test` task XML output dirs from `ext.taskNames`
- `src/main/kotlin/de/micschro/shardwise/internal/` — new pure aggregation logic
  (module-key derivation + XML parse + millis rendering), likely a new `internal/` file
  plus a new internal `Task` class
- `README.md:193-208` — replace the buggy inline Python with `shardwiseGenerateWeights` usage
- `docs/self-updating-weights.md:13-33` — swap `python3 generate-test-weights.py` for the
  Gradle task in the CI recipes
- `api/gradle-test-shard-plugin.api` — only if a new public member is added
- `src/functionalTest/kotlin/de/micschro/shardwise/ShardwisePluginFunctionalTest.kt`
  (or sibling) — new functional tests following the existing pattern
- New pure unit test file for the aggregation logic (parallel to `TestWeightsTest.kt`)

## Public API Impact

Task registration by name does not require a new public Kotlin type. An internal
`@TaskAction` class (recommended, for typed `@Input`/`@OutputFile`/`@InputFiles` CC-safe
declarations) stays in `internal/` and is excluded from `explicitApi()`. `apiDump` is
needed only if a new public `ShardwiseExtension` property or top-level public type is
added — reusing the existing `weightsFile` property as the generation target avoids that
entirely (proposal decision).

## Approaches

1. **Pure `internal/` aggregator + internal Task class + glue registration** — mirrors the
   existing `TestWeights`/`TestShardPlanner` vs. `ShardwisePlugin`/`ShardBuildService` split.
   - Pros: respects the two-layer rule; XML/aggregation logic is plain-unit-testable; typed
     `@Input`/`@OutputFile` declarations are the standard CC-safe idiom for cacheable tasks;
     zero-to-minimal API growth.
   - Cons: first Task-with-`@TaskAction` in this codebase.
   - Effort: Medium.
2. **Inline `tasks.register(...) { doLast { ... } }` calling a pure helper** — no new Task class.
   - Cons: `doLast` closures risk capturing unresolved `Project`/Provider state (weaker
     CC-safety guarantee); less isolatable for unit testing.
   - Effort: Low.

## Recommendation

Approach 1 — the only one that cleanly satisfies both the two-layer rule and the CC-safety
idiom already proven elsewhere in this file.

## Decisions taken (orchestrator + user, before propose)

- **Aggregation: ONE merged `test-weights.properties`** across all configured `taskNames`,
  summed per module. Consistent with the current planner model ("the same per-module weight
  feeds every task type's plan", README:189). Separate per-task-name profiles would break the
  frozen weights-file format or force a planner rewrite — deferred to a potential v0.2 feature.
- **Time granularity: suite-level** — sum of `<testsuite time>` attributes, identical to the
  existing README/docs scripts. Keeps user-visible weights unchanged; no behavior break.

## Risks

- **JUnit XML output-dir discovery is unproven in this codebase.** No existing code touches
  `Test.reports.junitXml.outputLocation` (a lazy `DirectoryProperty`) or
  `Test.binaryResultsDirectory`. Known upstream issue gradle/gradle#17091 reports `flatMap`
  misbehaving on `reports.junitXml.outputLocation` in some scenarios — verify against the
  pinned Gradle 8.11 API before implementation.
- **Reference scripts hardcode task name `test`** in their glob
  (`**/build/test-results/test/TEST-*.xml`) — "support `integrationTest`" is new logic, not a
  doc mirror.
- **No XML parsing dependency exists yet** — use JDK built-in `javax.xml.parsers`, consistent
  with "kill the Python dependency" and zero new external deps.

## Next

sdd-propose (with the two decisions above locked in).
