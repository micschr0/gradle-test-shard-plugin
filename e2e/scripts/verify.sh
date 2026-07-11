#!/usr/bin/env bash
# Asserts the Shardwise distribution invariant across all node logs: every
# declared module's `test` task ran on exactly one node and was SKIPPED on the rest.
#
# FAIL-CLOSED by design. The module list and the node count are INPUTS, never
# inferred from the logs — the logs are the thing under test, so deriving the
# oracle from them would let a module the plugin dropped entirely go unnoticed.
#   modules ← e2e/consumer/settings.gradle.kts   (independent oracle)
#   nodes   ← SHARDWISE_E2E_NODE_TOTAL           (the parallel: value the pipeline ran)
set -euo pipefail

repo_root="${CI_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
log_dir="$repo_root/e2e/build/logs"
settings="$repo_root/e2e/consumer/settings.gradle.kts"

nodes="${SHARDWISE_E2E_NODE_TOTAL:?must be set to the node count the pipeline ran}"

[[ -f "$settings" ]] || { echo "FAIL: cannot read module oracle: $settings"; exit 1; }

mapfile -t modules < <(
    grep -oP 'include\(\K[^)]*' "$settings" \
        | grep -oP '"[^"]+"' \
        | tr -d '"' \
        | sort -u
)
[[ ${#modules[@]} -gt 0 ]] || { echo "FAIL: no modules declared in $settings"; exit 1; }

# Every node must have produced a log. A missing log is a failure, not a smaller N.
for i in $(seq 1 "$nodes"); do
    [[ -f "$log_dir/node-$i.log" ]] || { echo "FAIL: missing log for node $i of $nodes"; exit 1; }
done

fail=0
total_ran=0

for m in "${modules[@]}"; do
    ran=0
    skipped=0
    absent=0
    for i in $(seq 1 "$nodes"); do
        log="$log_dir/node-$i.log"
        # Anchor on the full task path so `mod-a` never cross-matches `mod-ab`.
        if grep -qE "^> Task :${m}:test SKIPPED\$" "$log"; then
            skipped=$((skipped + 1))
        elif grep -qE "^> Task :${m}:test( .*)?\$" "$log"; then
            ran=$((ran + 1))
        else
            absent=$((absent + 1))
        fi
    done
    total_ran=$((total_ran + ran))

    printf '%-14s ran on %d node(s), skipped on %d, absent from %d\n' \
        "$m:" "$ran" "$skipped" "$absent"

    if [[ "$absent" -gt 0 ]]; then
        echo "FAIL: $m is declared but missing from $absent node log(s) — it was never planned"
        fail=1
    elif [[ "$ran" -ne 1 ]]; then
        echo "FAIL: $m must run on exactly 1 of $nodes nodes, ran on $ran"
        fail=1
    fi
done

# Guard against a vacuous pass: a plan that skips everything, everywhere.
if [[ "$total_ran" -ne "${#modules[@]}" ]]; then
    echo "FAIL: expected ${#modules[@]} module executions across all nodes, saw $total_ran"
    fail=1
fi

# ── Cross-node plan agreement ────────────────────────────────────────────────
# Coverage counting alone cannot see two real bugs: a planner that is not
# deterministic (nodes disagree about the plan), and an index-mapping error where
# every node faithfully runs somebody else's share. Both keep "exactly once"
# intact. So compare the plan each node derived, and what each node actually ran.
plan_dir="$repo_root/e2e/build/plans"

if [[ -d "$plan_dir" ]]; then
    reference=""
    for i in $(seq 1 "$nodes"); do
        plan="$plan_dir/node-$i.txt"
        [[ -f "$plan" ]] || { echo "FAIL: node $i produced no plan artifact"; fail=1; continue; }

        # Every node must have derived the identical plan from identical inputs.
        if [[ -z "$reference" ]]; then
            reference="$plan"
        elif ! diff -q "$reference" "$plan" >/dev/null; then
            echo "FAIL: node $i derived a different plan than node 1 — the planner is not deterministic"
            diff "$reference" "$plan" | sed 's/^/    /'
            fail=1
            continue
        fi

        # A node must run exactly the share the plan assigns to IT — not another
        # node's share. This is what catches an off-by-one in the node index.
        # An idle node's share is legitimately empty, and grep reports failure when
        # it matches nothing — so never let these greps decide the exit status.
        assigned=$(
            { grep -oP "^$i=\K.*" "$plan" || true; } \
                | tr ',' '\n' | { grep -v '^$' || true; } | sort | tr '\n' ' '
        )
        actually_ran=$(
            for m in "${modules[@]}"; do
                if grep -qE "^> Task :${m}:test SKIPPED\$" "$log_dir/node-$i.log"; then
                    continue
                fi
                if grep -qE "^> Task :${m}:test( .*)?\$" "$log_dir/node-$i.log"; then
                    echo "$m"
                fi
            done | sort | tr '\n' ' '
        )
        if [[ "$assigned" != "$actually_ran" ]]; then
            echo "FAIL: node $i was assigned [${assigned% }] but ran [${actually_ran% }]"
            fail=1
        fi
    done
    [[ "$fail" -eq 0 ]] && echo "OK: all $nodes nodes agreed on the plan and ran their own share"
fi

if [[ "$fail" -eq 0 ]]; then
    echo "OK: all ${#modules[@]} declared modules ran exactly once across $nodes nodes"
fi
exit "$fail"
