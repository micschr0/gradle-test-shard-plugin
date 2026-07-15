# Internal decisions archive

This directory preserves the internal specifications and design documents that shaped
the `gradle-test-shard-plugin` codebase. These were originally tracked under `plans/`
and were kept as the **only** part of that workspace that has long-term value
beyond the immediate session that produced them.

## What's here

Each entry is dated (YYYY-MM-DD prefix) and named for the work it covers.

| Entry | What it decided |
|-------|-----------------|
| `2026-07-15-shardwise-generate-weights/` | Proposal and exploration for the `generateTestWeights` task that aggregates JUnit XML timing into `test-weights.properties`. Shipped in v0.2.0. |
| `2026-07-17-catalogue-findings.md` | Code-review findings from migrating inline plugin versions into `gradle/libs.versions.toml`. The cataloguing work is done; the findings are preserved for historical context. |
| `2026-07-17-followup-debt.md` | Debt register of known gaps and trade-offs accepted at the time of the v0.2.0 release. Some items remain unaddressed. |
| `2026-07-17-ponytail-cleanup.md` | The ponytail cleanup spec — the dead-code and over-engineering sweep that produced the v0.2.0 internal state. Includes several `ponytail:` markers addressed in `src/`. |
| `2026-07-18-shardwise-v2-architecture/` | The v2 architecture refactor (BuildService + ValueSource split, configuration-cache safety invariants, plugin-public-API documentation, weights generation overhaul, test-environment isolation, CI workflow permissions, version-catalog coverage). Shipped in v0.2.0. |

## What's not here (and why)

- **Parked proposals** — proposals for work that was explicitly deferred and may never
  ship (e.g. `detekt-2-upgrade`, `ktlint-formatting-rules`). These are recoverable from
  git history (`git log --all -- plans/`) but do not earn a working-tree slot.
- **Internal audit iterations** — `plans/iter-N-findings.md` and the
  `2026-07-17-audit.md` baseline were working artifacts of a single audit session.
  Their conclusions are already in the cleanup commits and the issues they surfaced
  are fixed; the iteration history is not useful to a future maintainer.
- **SDD harness config** — `plans/config.yaml` and `plans/README.md` were
  tooling-internal to the SDD (Spec-Driven Development) harness used to author these
  specs. They are not specifications themselves.

## How to use this

If you are picking up a piece of work that touches one of the systems in these
documents, read the corresponding entry first. The proposals and specs here are
authoritative for the *why* behind the code in `src/`, even when they describe
decisions that are now several minor versions in the past.
