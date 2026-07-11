# Iteration 6 — Findings Ledger

> Lens: `gradle-plugin-review` + read-only walk
> Surface: `e2e/**`, `ci.yml` e2e jobs
> Sweep date: 2026-07-17

## Summary

| Tier | Count | IDs |
|------|-------|-----|
| BLOCKER | 0 | — |
| WARNING | 0 | — |
| INFO | 1 | E2E-OK-001 |

**Zero findings.** The e2e pipeline has been reviewed end-to-end and demonstrates exactly what a well-engineered CI/CD consumer should.

---

## E2E-OK-001 — INFO

**Full pipeline structure verified as correct:**

| Concern | Status |
|---------|--------|
| Pipeline definition (`e2e/.gitlab-ci.yml`) | ✅ 3 stages (build → test → verify), correct artifact wiring, `when: always` for logs |
| Consumer project (`e2e/consumer/`) | ✅ 4 modules, plugin applies at root, weights file configurable |
| Version drift check (`ci.yml:37-39` + `publish-plugin` script) | ✅ Root `version` in build.gradle.kts matches consumer's plugin version — asserted at CI time as grep + [[ comparison ]] |
| Scenario matrix (9 scenarios in `ci.yml` matrix + 9 documented in `e2e/README.md`) | ✅ Covers baseline, fail-test, surplus nodes, single node, missing weights, malformed weights, PlanDetail variants, Gradle version floor |
| Determinism proof (`verify.sh`) | ✅ Fail-closed: node count is input, module list from settings.gradle.kts NOT from logs, dump comparison proves nodes agreed |
| CI scripts self-tested (`verify-test.sh`, `run-node-env-test.sh`, `mutation-test.sh`, `glci-fetch-node-logs-test.sh`) | ✅ CI runs these as pre-e2e steps — each script's correctness is verified before the pipeline is trusted |
| glci binary (SHA-256 pinned, cached but re-verified every run) | ✅ |
| gitlab-runner version pinned (`.glciconfig.toml` override in CI) | ✅ Workaround for 19.2.0 hang |

## Notes

- `e2e/scripts/benchmark.sh` exists but is not gated in CI — correct per README ("prove that sharding makes things faster" is optional).
- Benchmark uses `SHARDWISE_E2E_SLEEP_UNIT_MS` env var for determinism — well-designed.
- `e2e/consumer/build.gradle.kts` has a comment "MUST match root build.gradle.kts version" — and the version drift check proves it.
