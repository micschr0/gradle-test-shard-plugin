# plans/ — SDD Artifact Store

`plans/` is the **single source of truth** for all Spec-Driven Development (SDD) artifacts in this repo. It is git-tracked, durable across sessions, and the only place SDD phases read from and write to.

## Why plans/ (not openspec/)

Historically this repo used `openspec/` as the SDD artifact store. That folder was `.gitignore`d and agent-local, so every SDD change needed a git-tracked mirror in `plans/` (see the migration note in `2026-07-17-features/02-followup-debt/SPEC.md` — since removed). Two stores with the same content is drift waiting to happen, so `openspec/` was retired on 2026-07-19 and `plans/` is now the only store.

## Layout

| Path | Contents |
|------|----------|
| `config.yaml` | Stack info, TDD mode, test commands, SDD phase rules. Read by every SDD phase before it runs. |
| `2026-07-17-audit.md` | Master plan for the 10-iteration end-to-end audit (2026-07-17). |
| `iter-{1..9}-findings.md` | Per-iteration findings ledger from the audit. |
| `2026-07-17-features/<NN-slug>/` | Active SDD changes. Each folder holds one `SPEC.md` (durable spec per this repo's convention) plus any per-phase artifacts. |
| `archive/<YYYY-MM-DD-slug>/` | Completed SDD changes, kept for provenance. |

## SDD Phase Convention

Per the SDD workflow (`~/.claude/skills/_shared/sdd-orchestrator-workflow.md`), phases read/write the following artifacts:

| Phase | Reads | Writes |
|-------|-------|--------|
| `sdd-explore` | nothing | `explore.md` |
| `sdd-propose` | exploration (optional) | `proposal.md` |
| `sdd-spec` | proposal (required) | `SPEC.md` (this repo's naming) |
| `sdd-design` | proposal (required) | `design.md` |
| `sdd-tasks` | spec + design (required) | `tasks.md` |
| `sdd-apply` | tasks + spec + design | `apply-progress.md` |
| `sdd-verify` | spec + tasks + apply-progress | `verify-report.md` |
| `sdd-archive` | all artifacts | move change folder to `archive/<YYYY-MM-DD-slug>/` |

Artifacts go under the change's folder in `2026-07-17-features/<NN-slug>/` while active, then under `archive/<YYYY-MM-DD-slug>/` once archived.

## Conventions

- **Durable spec filename is `SPEC.md`** (uppercase), not `spec.md`. Established in `02-followup-debt` and kept for consistency.
- **Numbered feature folders** (`01-catalogue`, `02-followup-debt`, `03-ponytail-cleanup`) allocate the next number in sequence.
- **Archive folder names** use `YYYY-MM-DD-<slug>` (no numbering).
- **No artifacts outside `plans/`**. Any new SDD-adjacent folder (`openspec/`, `specs/`, `.sdd/`) is a regression — delete on sight.
- **Strict TDD is active** (see `config.yaml`). Every apply phase runs the configured test command before reporting green.
