#!/usr/bin/env bash
# Proves the e2e suite can actually FAIL.
#
# A scenario that has only ever been seen passing is not evidence of anything.
# This breaks the plugin on purpose, in ways the suite is supposed to catch, and
# asserts the suite goes red for each one. If a mutation survives, the scenario
# that was meant to catch it is decoration.
#
# Runs against verify.sh directly with synthesised node logs and plan dumps —
# no containers, so it is fast and needs no Docker.
set -uo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
verify="$script_dir/verify.sh"

survived=0
caught=0

scratch() {
    local root
    root="$(mktemp -d)"
    mkdir -p "$root/e2e/consumer" "$root/e2e/build/logs" "$root/e2e/build/plans"
    echo 'include("mod-a", "mod-b", "mod-c", "mod-d")' \
        >"$root/e2e/consumer/settings.gradle.kts"
    echo "$root"
}

# Writes node $1's log. Remaining args are the modules it RAN; the rest are SKIPPED.
node_log() {
    local root="$1" node="$2"
    shift 2
    local ran=(" $* ") m
    : >"$root/e2e/build/logs/node-$node.log"
    for m in mod-a mod-b mod-c mod-d; do
        if [[ "${ran[*]}" == *" $m "* ]]; then
            echo "> Task :$m:test"
        else
            echo "> Task :$m:test SKIPPED"
        fi >>"$root/e2e/build/logs/node-$node.log"
    done
}

node_plan() {
    local root="$1" node="$2"
    shift 2
    printf '%s\n' "$@" >"$root/e2e/build/plans/node-$node.txt"
}

# A mutation must make verify.sh FAIL. If it passes, the mutation survived.
mutation() {
    local name="$1" root="$2"
    if CI_PROJECT_DIR="$root" SHARDWISE_E2E_NODE_TOTAL=3 bash "$verify" >/dev/null 2>&1; then
        echo "  SURVIVED — $name"
        echo "             the suite did not notice this break"
        survived=$((survived + 1))
    else
        echo "  caught   — $name"
        caught=$((caught + 1))
    fi
    rm -rf "$root"
}

# The plan every healthy node derives.
PLAN=("1=mod-a,mod-d" "2=mod-b" "3=mod-c")

echo "mutation testing — can the e2e suite fail at all?"
echo

# ── Sanity: the unmutated case must PASS, or every result below is meaningless. ──
root="$(scratch)"
for n in 1 2 3; do node_plan "$root" "$n" "${PLAN[@]}"; done
node_log "$root" 1 mod-a mod-d
node_log "$root" 2 mod-b
node_log "$root" 3 mod-c
if CI_PROJECT_DIR="$root" SHARDWISE_E2E_NODE_TOTAL=3 bash "$verify" >/dev/null 2>&1; then
    echo "  baseline — a healthy plan passes (as it must)"
else
    echo "  BROKEN   — the healthy baseline FAILS; every result below is worthless"
    rm -rf "$root"
    exit 1
fi
rm -rf "$root"

# ── Mutation: the plugin skips a module everywhere (coverage lost). ──────────
root="$(scratch)"
for n in 1 2 3; do node_plan "$root" "$n" "${PLAN[@]}"; done
node_log "$root" 1 mod-a          # mod-d dropped
node_log "$root" 2 mod-b
node_log "$root" 3 mod-c
mutation "a module is skipped on every node" "$root"

# ── Mutation: the plugin runs a module twice (onlyIf inverted somewhere). ────
root="$(scratch)"
for n in 1 2 3; do node_plan "$root" "$n" "${PLAN[@]}"; done
node_log "$root" 1 mod-a mod-d
node_log "$root" 2 mod-b mod-c    # mod-c also runs here
node_log "$root" 3 mod-c
mutation "a module runs on two nodes" "$root"

# ── Mutation: a module vanishes from the build entirely. ─────────────────────
#     This is the one an oracle derived from the logs cannot see.
root="$(scratch)"
for n in 1 2 3; do node_plan "$root" "$n" "${PLAN[@]}"; done
for n in 1 2 3; do
    : >"$root/e2e/build/logs/node-$n.log"
    for m in mod-a mod-b mod-c; do   # mod-d never appears at all
        if [[ "$n" == 1 && "$m" == mod-a ]] \
            || [[ "$n" == 2 && "$m" == mod-b ]] \
            || [[ "$n" == 3 && "$m" == mod-c ]]; then
            echo "> Task :$m:test"
        else
            echo "> Task :$m:test SKIPPED"
        fi >>"$root/e2e/build/logs/node-$n.log"
    done
done
mutation "a module disappears from the build entirely" "$root"

# ── Mutation: the planner is not deterministic — nodes disagree. ─────────────
root="$(scratch)"
node_plan "$root" 1 "${PLAN[@]}"
node_plan "$root" 2 "1=mod-a" "2=mod-b,mod-d" "3=mod-c"   # node 2 disagrees
node_plan "$root" 3 "${PLAN[@]}"
node_log "$root" 1 mod-a mod-d
node_log "$root" 2 mod-b
node_log "$root" 3 mod-c
mutation "the planner is not deterministic across nodes" "$root"

# ── Mutation: node index off by one. Every node runs somebody else's share,
#     and coverage stays perfect — exactly once, everywhere. Only comparing each
#     node's run set against its OWN assignment catches this.
root="$(scratch)"
for n in 1 2 3; do node_plan "$root" "$n" "${PLAN[@]}"; done
node_log "$root" 1 mod-b          # should have run mod-a, mod-d
node_log "$root" 2 mod-c          # should have run mod-b
node_log "$root" 3 mod-a mod-d    # should have run mod-c
mutation "every node runs the next node's share (index off by one)" "$root"

# ── Mutation: nothing runs anywhere. The vacuous green. ─────────────────────
root="$(scratch)"
for n in 1 2 3; do
    node_plan "$root" "$n" "${PLAN[@]}"
    node_log "$root" "$n"         # no module runs
done
mutation "nothing executes on any node" "$root"

echo
echo "caught: $caught, survived: $survived"
if [[ "$survived" -gt 0 ]]; then
    echo "FAIL: $survived mutation(s) survived — the suite cannot catch them"
    exit 1
fi
echo "OK: every mutation was caught — the suite can fail when the plugin breaks"
