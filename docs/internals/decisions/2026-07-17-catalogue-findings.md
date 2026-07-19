# Plan 02 · Findings — libs.versions.toml migration

> Lens: code review of the inlined versions in `build.gradle.kts`
> Re-review of the candidate `gradle/libs.versions.toml` against the project's existing patterns.

## Current State (build.gradle.kts)

| Element | Current form | Source line |
|---------|--------------|-------------|
| Kotlin compiler | `kotlin("jvm") version "2.4.0"` | 3 |
| detekt | `id("io.gitlab.arturbosch.detekt") version "1.23.8"` | 4 |
| binary-compatibility-validator | `id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"` | 5 |
| plugin-publish | `id("com.gradle.plugin-publish") version "2.1.1"` | 6 |
| gradleApi dep | `compileOnly("dev.gradleplugins:gradle-api:8.11")` | 32 |
| junit BOM | `testImplementation(platform("org.junit:junit-bom:6.1.1"))` | 35 |
| junit jupiter | `testImplementation("org.junit.jupiter:junit-jupiter")` | 36 |
| junit platform launcher | `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` | 37 |

## Finding (BLOCKER)

**Inlined versions contradict the project's Renovate + Gradle Plugin Portal flow.**

`renovate.json5` declares `packageRules` for `gradle` manager and groups updates — but Renovate can only graduate these into centralised version bumps if a single source of truth exists. With 4 plugin ids and 4 dependency versions inlined across the script, Renovate's diff becomes noisy, and a Gradle Plugin Portal release that pins a new Kotlin version requires 4 separate `gradle.lock`-style bumps.

## Pattern Decision

Follow the Gradle 2.x recommendation for plugin authors: a single `gradle/libs.versions.toml` with `[versions]`, `[libraries]`, `[plugins]` tables, referenced from `build.gradle.kts` via `alias(libs.plugins.X)` and `libs.X`. No API surface change. `apiCheck` remains the gate.

## Locked decisions

- **Plugin keys** use camelCase (Gradle's version-catalog TOML extension translates `kebab-case` to `camelCase` accessor names). Verified via Gradle docs.
- **TOML `[plugins]` table** holds only the plugins currently used; new plugins must be added there before use in `build.gradle.kts`.
- **No Renovate config change** in this cycle — the catalog simply gives Renovate a single source of truth to bump.

## Affected files

- `gradle/libs.versions.toml` (new)
- `build.gradle.kts` (modified: replace version literals with `libs.*` references)

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `alias()` referencing mistake breaks compile-time plugin resolution | Med | Gate `./gradlew check apiCheck` is the source of truth; verify before merge |
| `kover` plugin added in same cycle as catalog → double failure | High (observed) | Split into Plan 2 (catalog) and Plan 6 (kover); this cycle does NOT apply kover |
| Catalog's TOML dotted key syntax differs from kebab-case | Low | Use camelCase keys consistently per Gradle convention |

## Rollback

Pure refactor: revert the commit. No API change, no behavior change, no consumer impact.
