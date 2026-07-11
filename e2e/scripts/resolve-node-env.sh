#!/usr/bin/env bash
# Sourced by run-node.sh. It is a separate file only so the derivation can be tested
# on its own — see run-node-env-test.sh — without Docker and without running Gradle.
#
# Recovers CI_NODE_INDEX/CI_NODE_TOTAL when the runner did not set them. glci expands
# `parallel: N` into job NAMES ("shard-test 2/3") but writes no variables for the count
# form, so under glci every node dies on run-node.sh's `:?` guards while real GitLab CI
# is fine. The job name is the only thing glci does give us, so derive from that.
#
# FAIL-CLOSED, deliberately. CI_NODE_INDEX is the single input the whole plugin shards
# on: a wrong or duplicated index means two nodes compute the same shard, the remaining
# modules run nowhere, and the pipeline still goes green — a vacuous pass, worse than a
# red one. So a suffix that is not exactly "<i>/<N>" with 1 <= i <= N is an error, never
# a default of 1.

# Already set means the runner owns them (real GitLab CI, and run-docker.sh, which
# passes them per container). Do not second-guess those — not even against CI_JOB_NAME.
if [[ -n "${CI_NODE_INDEX:-}" ]]; then
    : "${CI_NODE_TOTAL:?CI_NODE_INDEX is set but CI_NODE_TOTAL is not — the runner set only half the pair}"
    return 0
fi

shardwise_job_name="${CI_JOB_NAME:-}"

if [[ ! "$shardwise_job_name" =~ ^.*[[:space:]]([0-9]+)/([0-9]+)$ ]]; then
    echo "ERROR: CI_NODE_INDEX/CI_NODE_TOTAL are unset and CI_JOB_NAME='$shardwise_job_name'" >&2
    echo "       carries no '<name> <i>/<N>' shard suffix to derive them from." >&2
    echo "       Refusing to guess: a wrong shard index passes the pipeline while" >&2
    echo "       silently running no tests for the modules it drops." >&2
    exit 1
fi

shardwise_index="${BASH_REMATCH[1]}"
shardwise_total="${BASH_REMATCH[2]}"

if ((shardwise_total < 1 || shardwise_index < 1 || shardwise_index > shardwise_total)); then
    echo "ERROR: CI_JOB_NAME='$shardwise_job_name' yields node $shardwise_index of $shardwise_total," >&2
    echo "       which is outside the 1..N the planner requires." >&2
    exit 1
fi

# The pipeline states N twice — as `parallel:` (which becomes this job name) and as
# SHARDWISE_E2E_NODE_TOTAL (which verify.sh takes as its input, never inferring it).
# The scenario runner rewrites the two with separate seds. Should they ever drift,
# verify.sh would check an N that nothing ran with, and the whole suite would lie.
if [[ -n "${SHARDWISE_E2E_NODE_TOTAL:-}" && "$SHARDWISE_E2E_NODE_TOTAL" != "$shardwise_total" ]]; then
    echo "ERROR: CI_JOB_NAME says $shardwise_total nodes, SHARDWISE_E2E_NODE_TOTAL says $SHARDWISE_E2E_NODE_TOTAL." >&2
    echo "       'parallel:' and SHARDWISE_E2E_NODE_TOTAL have drifted apart in the pipeline." >&2
    exit 1
fi

export CI_NODE_INDEX="$shardwise_index"
export CI_NODE_TOTAL="$shardwise_total"
unset shardwise_job_name shardwise_index shardwise_total
