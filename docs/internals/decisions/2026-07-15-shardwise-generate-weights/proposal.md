# Proposal: shardwiseGenerateWeights task

## Intent

Ship a Gradle task `shardwiseGenerateWeights` that generates `test-weights.properties`
from JUnit XML timings, replacing the copy-paste inline Python in the README.

Two drivers:

- **Verified bug.** The README's inline script (`README.md:196-208`) omits the
  `if module == f: module = '.'` root-key fix that `docs/self-updating-weights.md`
  has. Root-project timings get keyed as a raw path, match no module, and silently
  fall back to `defaultWeight` forever.
- **Onboarding friction.** Users must copy Python, keep a JDK-external toolchain, and
  hand-maintain logic that must stay in sync with the plugin's module-key derivation.

A Gradle task is correct by construction: it reuses the plugin's own key derivation,
so it cannot drift from the planner.

## Scope

### In Scope

- New task `shardwiseGenerateWeights` writing a single merged `test-weights.properties`.
- Pure `internal/` aggregator: JUnit XML parse + module-key derivation + millis render.
- CC-safe glue registration in `ShardwisePlugin` discovering `Test` XML output dirs
  from `ext.taskNames`.
- Replace buggy Python in `README.md` and the CI recipes in `docs/self-updating-weights.md`.

### Out of Scope

- **Per-task-name weight profiles** — deferred to a potential v0.2 feature; would break
  the frozen weights-file format or force a planner rewrite.
- Auto-running generation on every build (task is explicit, on-demand).
- Any planner change; testcase-level granularity (stays suite-level).

## Capabilities

### New Capabilities

- `weights-generation`: on-demand task that aggregates JUnit suite timings into the
  merged, per-module `test-weights.properties`.

### Modified Capabilities

- None (planner, weights-file format, and extension DSL behavior unchanged).

## Approach

Approach 1 from exploration. Pure `internal/` aggregator (no Gradle types,
plain-unit-testable): parse `<testsuite time>` per file via JDK
`javax.xml.parsers`, derive module key with the doc's fixed `.`-root logic, sum per
module, render sorted-desc `key=int(ms)` lines under the existing
`# generated from junit xml timings (millis)` header. An internal `@TaskAction` Task
class carries typed `@InputFiles`/`@OutputFile`; `ShardwisePlugin` registers it and
wires XML dirs from `Test.reports.junitXml.outputLocation` via lazy providers.

**Locked decisions:** ONE merged file summed per module (consistent with README:189
"same per-module weight feeds every task type's plan"); suite-level timing (identical
to current scripts, no user-visible weight change).

## Public API Decision

**No `apiDump` needed.** Reuse the existing public `weightsFile`
(`RegularFileProperty` on `ShardwiseExtension`) as the output target. The Task class
lives in `internal/`, excluded by `explicitApi()`. Task registration by name adds no
public Kotlin type. Recommendation: keep it this way — zero frozen-API risk.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `internal/` (new file + Task class) | New | Pure aggregator + internal `@TaskAction` task |
| `ShardwisePlugin.kt` | Modified | Register task, wire XML output dirs (lazy) |
| `README.md:193-208` | Modified | Replace Python with task usage |
| `docs/self-updating-weights.md:13-33` | Modified | Swap Python for the task in CI recipes |
| `api/*.api` | None | No public surface added |
| `functionalTest` + new unit test | New | CC-engages test; pure aggregation unit tests |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| JUnit XML dir discovery unproven on 8.11; gradle/gradle#17091 `flatMap` misbehavior on `reports.junitXml.outputLocation` | Med | Read `Test.reports.junitXml.outputLocation` (lazy `DirectoryProperty`) directly; verify against pinned 8.11 API before impl; functional test proves it end-to-end |
| Reference scripts hardcode task `test`; supporting `integrationTest` is new logic | Med | Iterate `ext.taskNames`, not a fixed glob; cover in unit + functional tests |
| Regenerated weights drop a module | Low | Weights affect balance only; missing/stale keys fall back to `defaultWeight` and still run (coverage-beats-balance invariant unchanged) |

## Rollback Plan

Additive change. Revert the commit to remove the task and restore the doc snippets;
existing plans are unaffected because the weights-file format and planner are untouched.

## Dependencies

- None new. XML via JDK `javax.xml.parsers` (zero new external deps).

## Testing Strategy

Strict TDD active. Pure unit tests for the aggregator (root-`.` keying, per-module sum,
malformed/missing XML, sorted-desc render), parallel to `TestWeightsTest.kt`. Functional
test following `ShardwisePluginFunctionalTest` asserts the task produces correct weights
AND that CC engages (`--configuration-cache`, "Configuration cache entry stored" /
"Reusing configuration cache").

## Success Criteria

- [ ] `shardwiseGenerateWeights` writes correct merged weights, root tests keyed `.`.
- [ ] Aggregation logic is pure `internal/` with plain unit tests.
- [ ] Functional test asserts CC engages.
- [ ] `gradle check` green; `api/*.api` unchanged (no `apiDump`).
- [ ] README + docs reference the task, not Python.
