#!/usr/bin/env bash
# Runs the Shardwise e2e scenario matrix.
#
# Each scenario is one full pipeline invocation. The axes that TestKit already
# covers deterministically (planner balance, weights parsing, invalid env values)
# are deliberately NOT repeated here — e2e only earns its cost where real,
# separate processes are the thing under test.
#
# Usage: e2e/scenarios.sh [scenario-id ...]      (default: all)
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

passed=0
failed=0
declare -a failures=()

# Runs one pipeline. Args: <id> <description> then KEY=VALUE env assignments.
scenario() {
    local id="$1" desc="$2"
    shift 2
    if [[ $# -gt 0 && ${#WANTED[@]} -gt 0 ]] && ! printf '%s\n' "${WANTED[@]}" | grep -qx "$id"; then
        return 0
    fi

    echo
    echo "════════════════════════════════════════════════════════════════"
    echo "  $id — $desc"
    echo "════════════════════════════════════════════════════════════════"

    if env "$@" bash e2e/run-docker.sh >"/tmp/shardwise-$id.log" 2>&1; then
        echo "  PASS — $id"
        passed=$((passed + 1))
    else
        echo "  FAIL — $id (log: /tmp/shardwise-$id.log)"
        tail -15 "/tmp/shardwise-$id.log" | sed 's/^/    | /'
        failed=$((failed + 1))
        failures+=("$id")
    fi
}

WANTED=("$@")

# ── E-01 — baseline: 4 modules over 3 nodes, each module runs exactly once. ──
scenario E-01 "baseline distribution (N=3)" NODE_TOTAL=3

# ── E-03 — a failing test must turn the pipeline red. The single most important
#           scenario: if sharding could hide a failure, every other test is void.
scenario E-03 "a failing test fails the pipeline" \
    NODE_TOTAL=3 SHARDWISE_E2E_FAIL_MODULE=mod-c SHARDWISE_E2E_EXPECT_FAIL=1

# ── E-06 — more nodes than modules. Surplus nodes idle, and must still exit 0. ─
scenario E-06 "more nodes than modules (N=7, 4 modules)" NODE_TOTAL=7

# ── E-07 — a single node shards nothing: every module must run. ──────────────
scenario E-07 "single node runs everything (N=1)" NODE_TOTAL=1

# ── E-09 — a missing weights file must never cost coverage. ──────────────────
scenario E-09 "missing weights file still covers every module" \
    NODE_TOTAL=3 SHARDWISE_E2E_WEIGHTS=missing

# ── E-10/E-11 — stale and garbage weights. `mod-zombie` no longer exists;
#     `not/a/module` never did. Neither may starve a live module out of the plan.
scenario E-10 "stale and unknown weights entries keep coverage" \
    NODE_TOTAL=3 \
    SHARDWISE_E2E_WEIGHTS='mod-a=10
mod-b=20
mod-zombie=9999
not/a/module=50
mod-c=abc
=10'

# ── E-08 — coverage beats balance: mod-d has no weights entry at all and must
#           still run. The flagship invariant.
scenario E-08 "a module absent from weights still runs" \
    NODE_TOTAL=3 \
    SHARDWISE_E2E_WEIGHTS='mod-a=10
mod-b=20
mod-c=30'

# ── E-13/E-14 — the output contract. Distribution must hold regardless. ──────
scenario E-13 "planDetail=SUMMARY" NODE_TOTAL=3 SHARDWISE_E2E_PLAN_DETAIL=SUMMARY
scenario E-14 "planDetail=OFF does not disable sharding" \
    NODE_TOTAL=3 SHARDWISE_E2E_PLAN_DETAIL=OFF

# ── E-15 — the Gradle floor still works. The consumer runs the image's Gradle,
#     so this genuinely varies the version under test (the root wrapper would
#     otherwise pin one version and make every image identical).
scenario E-15 "the minimum supported Gradle (8.11)" NODE_TOTAL=3 IMAGE=gradle:8.11-jdk17
scenario E-15b "a newer Gradle (8.14)" NODE_TOTAL=3 IMAGE=gradle:8.14-jdk17

echo
echo "════════════════════════════════════════════════════════════════"
echo "  scenarios passed: $passed, failed: $failed"
[[ "$failed" -eq 0 ]] || printf '  failed: %s\n' "${failures[*]}"
echo "════════════════════════════════════════════════════════════════"
[[ "$failed" -eq 0 ]]
