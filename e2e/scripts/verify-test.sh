#!/usr/bin/env bash
# Tests for verify.sh — feeds it synthetic node logs and asserts it accepts a
# correct distribution and rejects every way the shard plan can be wrong.
#
# verify.sh must be FAIL-CLOSED: anything it cannot prove correct must fail.
set -uo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
verify="$script_dir/verify.sh"

passed=0
failed=0

# Builds a fake node log. Args: <file> then <module>:<RUN|SKIPPED> pairs.
write_log() {
    local file="$1"
    shift
    mkdir -p "$(dirname "$file")"
    : >"$file"
    local pair module state
    for pair in "$@"; do
        module="${pair%%:*}"
        state="${pair##*:}"
        if [[ "$state" == "SKIPPED" ]]; then
            echo "> Task :$module:test SKIPPED" >>"$file"
        else
            echo "> Task :$module:test" >>"$file"
        fi
    done
}

# Writes the plan a node claims to have computed: the modules assigned to each node.
# Args: <file> then one "<node>=<mod>,<mod>" per node.
write_plan() {
    local file="$1"
    shift
    mkdir -p "$(dirname "$file")"
    printf '%s\n' "$@" >"$file"
}

# Runs verify.sh against a scratch project and reports its exit code.
run_verify() {
    local root="$1" total="$2"
    CI_PROJECT_DIR="$root" SHARDWISE_E2E_NODE_TOTAL="$total" bash "$verify" >/dev/null 2>&1
}

expect() {
    local name="$1" want="$2" got="$3"
    if [[ "$want" == "$got" ]]; then
        echo "  ok   — $name"
        passed=$((passed + 1))
    else
        echo "  FAIL — $name (expected exit $want, got $got)"
        failed=$((failed + 1))
    fi
}

# Creates a scratch consumer whose settings.gradle.kts declares the given modules.
scratch() {
    local root
    root="$(mktemp -d)"
    mkdir -p "$root/e2e/consumer" "$root/e2e/build/logs"
    {
        echo 'rootProject.name = "shardwise-e2e-consumer"'
        printf 'include('
        local first=1 m
        for m in "$@"; do
            [[ $first -eq 1 ]] || printf ', '
            printf '"%s"' "$m"
            first=0
        done
        printf ')\n'
    } >"$root/e2e/consumer/settings.gradle.kts"
    echo "$root"
}

echo "verify.sh — distribution invariants"

# ── The happy path: 3 modules, 2 nodes, each module runs exactly once. ────────
root="$(scratch mod-a mod-b mod-c)"
logs="$root/e2e/build/logs"
write_log "$logs/node-1.log" mod-a:RUN mod-b:RUN mod-c:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:SKIPPED mod-c:RUN
run_verify "$root" 2
expect "accepts a correct distribution" 0 $?
rm -rf "$root"

# ── A module that ran nowhere. Classic coverage loss. ────────────────────────
root="$(scratch mod-a mod-b mod-c)"
logs="$root/e2e/build/logs"
write_log "$logs/node-1.log" mod-a:RUN mod-b:SKIPPED mod-c:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:SKIPPED mod-c:SKIPPED
run_verify "$root" 2
expect "rejects a module skipped on every node" 1 $?
rm -rf "$root"

# ── A module that ran twice. Wasted work, and a broken plan. ─────────────────
root="$(scratch mod-a mod-b mod-c)"
logs="$root/e2e/build/logs"
write_log "$logs/node-1.log" mod-a:RUN mod-b:RUN mod-c:SKIPPED
write_log "$logs/node-2.log" mod-a:RUN mod-b:SKIPPED mod-c:RUN
run_verify "$root" 2
expect "rejects a module that ran on two nodes" 1 $?
rm -rf "$root"

# ── T-1: a declared module missing from every log entirely. ──────────────────
# The plugin dropped mod-c before it ever reached a task graph. Deriving the
# module list from the logs makes this invisible — the oracle must be
# settings.gradle.kts, not the logs.
root="$(scratch mod-a mod-b mod-c)"
logs="$root/e2e/build/logs"
write_log "$logs/node-1.log" mod-a:RUN mod-b:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:RUN
run_verify "$root" 2
expect "rejects a declared module that appears in no log (T-1)" 1 $?
rm -rf "$root"

# ── T-2: everything skipped everywhere. A vacuous green. ────────────────────
root="$(scratch mod-a mod-b)"
logs="$root/e2e/build/logs"
write_log "$logs/node-1.log" mod-a:SKIPPED mod-b:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:SKIPPED
run_verify "$root" 2
expect "rejects a run where nothing executed anywhere (T-2)" 1 $?
rm -rf "$root"

# ── T-4: a node produced no log. N must be an input, not an inference. ──────
root="$(scratch mod-a mod-b mod-c)"
logs="$root/e2e/build/logs"
write_log "$logs/node-1.log" mod-a:RUN mod-b:SKIPPED mod-c:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:RUN mod-c:SKIPPED
# node 3 died before writing anything; mod-c was assigned to it.
run_verify "$root" 3
expect "rejects a missing node log instead of shrinking N (T-4)" 1 $?
rm -rf "$root"

# ── T-3: module names must match whole, not as substrings. ──────────────────
# `mod-a` must not cross-match `mod-ab`.
root="$(scratch mod-a mod-ab)"
logs="$root/e2e/build/logs"
write_log "$logs/node-1.log" mod-a:RUN mod-ab:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-ab:RUN
run_verify "$root" 2
expect "matches module names exactly, not as substrings (T-3)" 0 $?
rm -rf "$root"

# ── An empty shard is legal: more nodes than modules. ───────────────────────
root="$(scratch mod-a mod-b)"
logs="$root/e2e/build/logs"
write_log "$logs/node-1.log" mod-a:RUN mod-b:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:RUN
write_log "$logs/node-3.log" mod-a:SKIPPED mod-b:SKIPPED
run_verify "$root" 3
expect "accepts an idle node when nodes outnumber modules" 0 $?
rm -rf "$root"

echo
echo "verify.sh — cross-node plan agreement"

# Every node must derive the SAME plan from the same inputs, and must run exactly
# the share that plan assigns to it. Counting coverage alone cannot see either.

# ── The happy path: identical plans, and each node runs its own share. ───────
root="$(scratch mod-a mod-b mod-c)"
logs="$root/e2e/build/logs"
plans="$root/e2e/build/plans"
write_log "$logs/node-1.log" mod-a:RUN mod-b:RUN mod-c:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:SKIPPED mod-c:RUN
write_plan "$plans/node-1.txt" "1=mod-a,mod-b" "2=mod-c"
write_plan "$plans/node-2.txt" "1=mod-a,mod-b" "2=mod-c"
run_verify "$root" 2
expect "accepts nodes that agree on the plan and run their own share" 0 $?
rm -rf "$root"

# ── Two nodes derived DIFFERENT plans. Non-deterministic planner. ───────────
root="$(scratch mod-a mod-b mod-c)"
logs="$root/e2e/build/logs"
plans="$root/e2e/build/plans"
write_log "$logs/node-1.log" mod-a:RUN mod-b:RUN mod-c:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:SKIPPED mod-c:RUN
write_plan "$plans/node-1.txt" "1=mod-a,mod-b" "2=mod-c"
write_plan "$plans/node-2.txt" "1=mod-a" "2=mod-b,mod-c"   # disagrees!
run_verify "$root" 2
expect "rejects nodes that derived different plans" 1 $?
rm -rf "$root"

# ── T-6: every index shifted by one. Coverage is still perfect — each module
#     runs exactly once — but every node is doing somebody else's work. Pure
#     coverage counting passes this; comparing run-set to self-assignment does not.
root="$(scratch mod-a mod-b mod-c)"
logs="$root/e2e/build/logs"
plans="$root/e2e/build/plans"
# The plan says node 1 owns mod-a,mod-b and node 2 owns mod-c...
write_plan "$plans/node-1.txt" "1=mod-a,mod-b" "2=mod-c"
write_plan "$plans/node-2.txt" "1=mod-a,mod-b" "2=mod-c"
# ...but the nodes ran each other's share.
write_log "$logs/node-1.log" mod-a:SKIPPED mod-b:SKIPPED mod-c:RUN
write_log "$logs/node-2.log" mod-a:RUN mod-b:RUN mod-c:SKIPPED
run_verify "$root" 2
expect "rejects a node running another node's share (T-6)" 1 $?
rm -rf "$root"

# ── An idle node has an EMPTY assignment. That must be accepted, not tripped
#     over: `grep` finds nothing on an empty share and reports failure doing so.
root="$(scratch mod-a mod-b)"
logs="$root/e2e/build/logs"
plans="$root/e2e/build/plans"
plan=("1=mod-a" "2=mod-b" "3=")
for n in 1 2 3; do
    write_plan "$plans/node-$n.txt" "${plan[@]}"
done
write_log "$logs/node-1.log" mod-a:RUN mod-b:SKIPPED
write_log "$logs/node-2.log" mod-a:SKIPPED mod-b:RUN
write_log "$logs/node-3.log" mod-a:SKIPPED mod-b:SKIPPED
run_verify "$root" 3
expect "accepts an idle node whose plan share is empty" 0 $?
rm -rf "$root"

echo
echo "passed: $passed, failed: $failed"
[[ "$failed" -eq 0 ]]
