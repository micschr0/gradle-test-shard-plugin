# CLAUDE.md — Conventions & Resume Pointer

Resume cold-start: this file is the durable invariant. The audit findings live as
git-tracked per-iteration ledgers in `plans/iter-N-findings.md` (closed) and the active
follow-up spec at `plans/2026-07-17-features/02-followup-debt/SPEC.md`.

Local-scratch convention: `openspec/` is git-ignored at this repo. Anything written
there (e.g. `openspec/changes/audit-followup-debt/{explore,proposal}.md`) is the agent
runtime scratch, NOT in the durable history. The git-tracked mirror is always under
`plans/`. If you find `openspec/` content unique to the latest change, mirror it to
`plans/...` immediately.




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

### Sandbox-specific override (this environment only)

The default `gradle()` wrapper above fails with `PKIX path building failed` because
iron-proxy intercepts TLS to `services.gradle.org`. Use this instead — it avoids the
TLS path by hosting the build on the host JVM:

```bash
# 1. Once per session, clear root-owned artifacts from prior container runs:
docker run --rm -v "$(pwd)":/w -w /w --user root alpine:3.21 \
  sh -c 'rm -rf build .gradle'

# 2. Build natively with the cache in the project tree (NOT /tmp — capped at 2GB):
GRADLE_USER_HOME=./.gradle-cache \
  ./gradlew test functionalTest --no-daemon \
  --no-build-cache --no-configuration-cache \
  --project-cache-dir ./.gradle-cache/cache
```
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

## Audit Lessons (2026-07-17)

Findings from the 10-iteration autonomous audit that hardened the repository.
These are concrete, anchored to real fixes; no speculative advice.

### Confirmed Hard Rules

- **`System.getProperty()` / `System.console()` inside `taskGraph.whenReady` is a CC violation** → use `project.providers.systemProperty(...)` and `project.providers.provider { ... }` captured during `apply()`, threaded through method signatures. (GRADLE-CC-001, fixed in `dumpPlans` / `logPlan`.)
- **Every `actions/checkout` step must carry `persist-credentials: false`.** (GA-PERSIST-001, -002, -004)
- **Every workflow file must have `permissions: {}` at top-level + per-job `permissions:`.** (GA-PERM-004, -009)
- **Concurrency must protect main: `cancel-in-progress: ${{ github.ref != 'refs/heads/main' }}`.** (GA-CONC-003)
- **Every job must have `timeout-minutes`.** (GA-TIMEOUT-007, -008)
- **Repository must carry `zizmor.yml` with `persona: pedantic`.** (GA-ZIZMOR-006)

### Confirmed Antipatterns

| ID | Antipattern | Fix |
|----|-------------|-----|
| GRADLE-CC-001 | Raw `System.*` read inside `whenReady` | `project.providers.systemProperty(...).orElse("")` |
| GRADLE-API-002 | Dead imports (`import org.gradle.api.logging.Logger` unused) | Remove |
| GA-PERSIST-* | `actions/checkout` without `persist-credentials: false` | Add `with:` block |
| DOC-LINK-001 | Double-relative GitHub link `(/../../issues)` | Absolute URL |

### Tooling Drift

- Kotlin 2.4.0: `-Xjvm-default` → `-jvm-default=enable` (the `all` value does not exist in the new flag).
- `paths-ignore` in `ci.yml` must NOT exclude `**.md` — doc-only PRs need CI feedback.

### Work-Unit-Commit Discipline

NEVER batch setup, findings, and fix into one commit. Follow the work-unit-commits skill:
a separate commit for setup, findings, and each fix group — keeps `git revert` scoped.

### Open Risks (deferred)

- GA-HARDEN-005: `step-security/harden-runner` not added (SHA verification pending).
- No `gradle/libs.versions.toml` — dependencies inlined in `build.gradle.kts`.
- `codeql.yml` is `workflow_dispatch:`-only — functional but never runs automatically.

## Audit Lessons (Follow-up Debt — 2026-07-17)

Post-audit residual work captured in the durable spec at
`plans/2026-07-17-features/02-followup-debt/SPEC.md`.
Tier 1A applied (commits `6f82e5c`, `8432cba`); Tier 1B+ open.

### Confirmed Hard Rules (Tier-1A-applied)

- **TestKit functional env must blocklist `CI_NODE_INDEX`/`CI_NODE_TOTAL`** — runners
  that expose them can silently enable sharding inside aggregator tests. Add them to the
  runner's `filterKeys` blocklist alongside `JAVA_HOME` and `PATH`. (TKIT-ENV-007, fix `8432cba`.)
- **Top-level workflow `permissions: {}` — per-job opt-in only.** (GA-PERM-004, fix `6f82e5c`.)

### Tooling / SDD Lessons (this run)

- **Coverage-tool-first mutation testing.** Before debugging a mutation script that
  reports "UNDETECTED", run the project's own coverage tool on the mutation targets.
  kover showing `BRANCH=0 missed` on `TestWeights` and `TestShardPlanner` proves the
  branch paths are tested — remaining 0/5 means the substitution itself is broken, not
  a test gap. (Saved F1 from another hour of wrong-direction debugging.)
- **Perl substitution with shell-substituted patterns is fragile.** Patterns containing
  `"` / `!` / `(` / `)` collide with bash double-quote boundaries. Pass patterns via
  `@ARGV` (perl reads `$pat` and `$mut` from args) so bash never touches the contents;
  use `\Q...\E` for regex specials.
- **Audit-`@since` audit** — never trust `info` tags blindly. Explicit `GRADLE-DOC-004`
  was listed in the findings ledger but the KDoc `@since 0.1.0` was already present at
  the time of audit. Always verify by reading the file before scheduling a fix.

### Catalog-migration gotcha (F5, deferred)

`libs.gradle.api` does NOT resolve; the correct accessor for TOML key `gradle-api` is
`libs.gradleApi` (kebab -> camelCase). Attempting a full dep migration without that
distinction broke `./gradlew help` (BUILD FAILED). Reverted the in-flight change and
flagged the migration as Tier-1B work to be done with one file at a time, verifying
each swap with `./gradlew help` before moving on.

### Work-Unit-Commit Discipline

Setup / findings / fix / verify each in its own commit. The audit process itself
demonstrated: a single batch commit (e.g. setup+findings+fix in one) makes later
`git revert` imprecise and obscures audit-trail blame.

## shardwise-v2 Milestone (2026-07-18)

v2 closed the audit follow-up, split `ShardBuildService` into two services with
independent CC cache keys, and verified the existing `generateTestWeights` task
shipped in a previous milestone. Chained PR plan: 1 (audit) → 2 (weights, no-op —
already shipped) → 3 (BuildService split) → 4 (docs). Branch `shardwise-v2/side/catalog-deferral`
holds the F5 catalog-deferral as a side branch, not part of the v2 release chain.

### Confirmed lessons from v2

- **Stale-Spec Pattern: verify against `main` HEAD before planning work.** Three
  of the v2 PRs (audit refresh, weights-generation, partial catalog migration) had
  the implementation already on `main` that the OpenSpec proposal said needed to be
  written. Always check `git log` for the relevant symbols and read the file before
  scheduling a fix — audit follow-up debt is the canonical case (TKIT-IP-003,
  TKIT-VER-004, TKIT-CACHE-005 all closed in commits `116e081` / `027e9b1` / `d79ac5e`
  before v2 started).

- **BuildService cache-key split: `nodeTotal` stays with the planner.** A naive
  `ShardBuildService` split puts every parameter on the consumer side, but
  `TestShardPlanner.plan(modules, nodeTotal)` needs `nodeTotal` to bin-pack.
  Real maximum split: planner gets `[defaultWeight, weightsText, taskModulePaths, nodeTotal]`,
  nodeEnv service gets `[nodeIndex]`. Changing `CI_NODE_INDEX` invalidates only the
  NodeEnv cache. Five-vs-zero split is not achievable without restructuring
  `ShardPlan` to drop its `nodeTotal` field.

- **Gradle 9.6.1 catalog-accessor behaviour is partially observed, not diagnosed.**
  `libs.versions.X.get()` and `libs.plugins.X` work. `libs.X` (single-hyphen library
  like `libs.gradleApi`) failed compilation in this build. Multi-hyphen accessors
  (`libs.junit.bom`, `libs.junit.jupiter`) were not tested. Full diagnostic was not
  read; cause undetermined. See engram observation 13 for the empirical scope and
  the working string-concat fallback `compileOnly("dev.gradleplugins:gradle-api:" + libs.versions.gradleApi.get())`.

- **Class naming for service split: rename before commit, not after.** The
  `ShardBuildService` → `ShardNodeEnvService` rename is a 3-file change. Doing it
  before the first commit (not retroactively after the cache-key split is in)
  keeps the diff readable. **DX rule**: a class whose only field is a single
  primitive value should not have "Service" in its name without "What it serves"
  context. `ShardNodeEnvService(nodeIndex)` reads correctly;
  `ShardBuildService(nodeIndex)` was misleading.

- **The `lsp` tool is not exposed in this omp runtime.** Codegraph `mcp__codegraph_explore`
  is available; `lsp findReferences` / `goToDefinition` is not. For Kotlin refactors
  rely on `grep -rn "<Symbol>" --include="*.kt" src/`, then read each file before editing.
  The xd:// LSP tools (`mcp__dart_lsp` etc.) are Dart-only.

- **Test runtime budget: full `functionalTest` takes ~6 min 25 s with `--max-workers=1`.**
  The cross-version parameterized test (`@ValueSource ["8.11", "8.14.3", "9.6.1"]`)
  alone is ~3 min. CI must give the suite ≥7 min wall-time or split it.

### Work-Unit-Commit Discipline (re-affirmed in v2)

Each PR was a single commit on its branch. PR 1 (audit refresh) was spec-only
(8 LOC). PR 3 (BuildService split) was code + KDoc + unit-test + functional-test
(114 LOC). When a PR surfaces as "no real work" (PR 2), do not invent work to fill
it — the spec's stale-vs-fresh check is the deliverable. Force-creating changes
to justify a PR slot regresses the codebase.
