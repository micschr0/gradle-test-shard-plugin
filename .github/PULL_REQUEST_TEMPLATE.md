## What does this PR do?

<!-- One or two sentences describing the change and why. Closes #___ -->

**Breaking changes:** none <!-- or: describe them -->

## Checklist

- [ ] `./gradlew check` passes locally (unit + functional tests, `apiCheck`,
      `validatePlugins`).
- [ ] If the public API changed deliberately, `./gradlew apiDump` was run and
      the updated `api/*.api` is included.
- [ ] If planning/skipping logic changed, a test demonstrates coverage is
      preserved (coverage beats balance — unknown modules/tasks/weights must
      default to running, never to being skipped).
- [ ] If Gradle glue code changed, configuration-cache safety is preserved (no
      `afterEvaluate`/`projectsEvaluated`, env access only through the
      `ValueSource`, lazy wiring only) and a functional test asserts CC
      engages.
- [ ] `CHANGELOG.md` is updated under `[Unreleased]` for user-visible changes.
