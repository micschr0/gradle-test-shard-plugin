#!/usr/bin/env bash
# Tests for resolve-node-env.sh — the shim that recovers CI_NODE_INDEX/CI_NODE_TOTAL
# under glci, which expands `parallel: N` into job NAMES ("shard-test 2/3") but never
# sets those variables.
#
# The resolver must be FAIL-CLOSED. CI_NODE_INDEX is the one input the whole plugin
# shards on: a wrong or duplicated index makes two nodes compute the same shard, drops
# the rest of the modules, and still lets the pipeline go green. A guess here buys a
# vacuous pass — so anything that is not a strictly valid "<name> <i>/<N>" suffix must
# be an error, never a default.
set -uo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
resolver="$script_dir/resolve-node-env.sh"

passed=0
failed=0

# Sources the resolver in a fresh subshell with a controlled environment and prints
# "<exit> <CI_NODE_INDEX> <CI_NODE_TOTAL>". Args: env assignments (VAR=value).
#
# `env -i` is the point: the resolver's whole job is to react to variables being ABSENT,
# so a CI_NODE_INDEX inherited from the surrounding shell would quietly turn every
# derivation case into a pass-through case and the tests would prove nothing.
run_resolver() {
    # SC2016 is the intent here: $RESOLVER and $CI_NODE_* must expand in the INNER
    # shell, after the resolver has run — expanding them out here would read this
    # shell's environment, which is exactly the one `env -i` exists to keep out.
    # shellcheck disable=SC2016
    env -i "RESOLVER=$resolver" "$@" bash -c '
        set -uo pipefail
        # shellcheck source=/dev/null
        source "$RESOLVER"
        echo "0 ${CI_NODE_INDEX:--} ${CI_NODE_TOTAL:--}"
    ' 2>/dev/null
}

expect() {
    local name="$1" want="$2" got="$3"
    if [[ "$want" == "$got" ]]; then
        echo "  ok   — $name"
        passed=$((passed + 1))
    else
        echo "  FAIL — $name (expected '$want', got '$got')"
        failed=$((failed + 1))
    fi
}

# The resolver must not report success without setting BOTH variables, so every
# expected-ok case asserts the exit code and both values together.
expect_ok() {
    local name="$1" index="$2" total="$3"
    shift 3
    expect "$name" "0 $index $total" "$(run_resolver "$@")"
}

# Errors must be hard: the resolver aborts the shell it is sourced into, so run-node.sh
# can never reach Gradle with a half-derived or guessed index. Assert BOTH that the
# subshell died non-zero and that it published no CI_NODE_* values on the way out.
expect_error() {
    local name="$1"
    shift
    local got status
    got="$(run_resolver "$@")"
    status=$?
    if [[ "$status" -ne 0 && -z "$got" ]]; then
        echo "  ok   — $name"
        passed=$((passed + 1))
    else
        echo "  FAIL — $name (expected a hard error, got exit $status and '$got')"
        failed=$((failed + 1))
    fi
}

echo "resolve-node-env.sh — pass-through (real GitLab CI / run-docker.sh own this path)"

# Real GitLab CI sets both variables itself. The shim must be invisible there — and
# must not "correct" them from a job name, however that name happens to be spelled.
expect_ok "passes an already-set index/total through untouched" 2 3 \
    CI_NODE_INDEX=2 CI_NODE_TOTAL=3
expect_ok "ignores CI_JOB_NAME when the variables are already set" 2 3 \
    CI_NODE_INDEX=2 CI_NODE_TOTAL=3 CI_JOB_NAME="shard-test 1/9"

echo
echo "resolve-node-env.sh — derivation from the glci job name"

expect_ok "derives 1/3 from the job name" 1 3 CI_JOB_NAME="shard-test 1/3"
expect_ok "derives 3/3 from the job name" 3 3 CI_JOB_NAME="shard-test 3/3"
# Scenario E-07 runs with parallel: 1; glci names that instance "shard-test 1/1".
expect_ok "derives the single-node case 1/1 (E-07)" 1 1 CI_JOB_NAME="shard-test 1/1"

echo
echo "resolve-node-env.sh — fail-closed on anything it cannot prove"

expect_error "rejects a job name with no shard suffix" CI_JOB_NAME="shard-test"
expect_error "rejects a missing CI_JOB_NAME entirely"
expect_error "rejects a non-numeric suffix" CI_JOB_NAME="shard-test a/b"
expect_error "rejects a missing total" CI_JOB_NAME="shard-test 1/"
expect_error "rejects a missing index" CI_JOB_NAME="shard-test /3"
# 0/3 and 4/3 are the dangerous ones: both are shaped like a valid suffix, and both
# would hand the planner an index outside 1..N.
expect_error "rejects index 0 (nodes are 1-based)" CI_JOB_NAME="shard-test 0/3"
expect_error "rejects an index past the total" CI_JOB_NAME="shard-test 4/3"
expect_error "rejects a zero total" CI_JOB_NAME="shard-test 1/0"

echo
echo "resolve-node-env.sh — lockstep with the pipeline's declared node count"

# The pipeline states N twice: once as `parallel:` (which becomes the job name) and
# once as SHARDWISE_E2E_NODE_TOTAL (which verify.sh takes as its input). The scenario
# runner rewrites both with separate seds. If those ever drift apart, verify.sh would
# check a different N than the one that actually ran — a silent lie. Cross-check.
expect_ok "accepts a derived total that agrees with SHARDWISE_E2E_NODE_TOTAL" 2 3 \
    CI_JOB_NAME="shard-test 2/3" SHARDWISE_E2E_NODE_TOTAL=3
expect_error "rejects a derived total that disagrees with SHARDWISE_E2E_NODE_TOTAL" \
    CI_JOB_NAME="shard-test 2/3" SHARDWISE_E2E_NODE_TOTAL=5

echo
echo "passed: $passed, failed: $failed"
[[ "$failed" -eq 0 ]]
