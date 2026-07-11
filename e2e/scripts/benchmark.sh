#!/usr/bin/env bash
# Benchmark: prove that sharding accelerates test execution.
#
# Runs the Docker E2E pipeline twice — N=1 then N=3 — with sleep-based tests
# and extreme weight skew. Compares wall-clock durations and reports whether
# sharding is faster.
#
# Exit codes per spec: 0=faster, 1=not faster, 2=setup failure
#
# Env vars:
#   SHARDWISE_E2E_SLEEP_UNIT_MS   sleep multiplier per weight unit (default 100)
set -euo pipefail

cd "$(dirname "$0")/.."

docker info >/dev/null 2>&1 || { echo "FAIL: Docker is required."; exit 2; }

to_seconds() {
    local t="$1"
    if [[ "$t" =~ ^([0-9]+)m\ ([0-9.]+)s$ ]]; then
        echo "$(( BASH_REMATCH[1] * 60 + ${BASH_REMATCH[2]%.*} ))"
    elif [[ "$t" =~ ^([0-9.]+)s$ ]]; then
        echo "${BASH_REMATCH[1]%.*}"
    else
        echo ""
    fi
}

UNIT_MS="${SHARDWISE_E2E_SLEEP_UNIT_MS:-1000}"

# Extreme weight skew: mod-d is 10× heavier than the next-largest module.
# The greedy-LPT planner must place mod-d on its own shard — which is exactly
# what makes N=3 faster than N=1.
WEIGHTS="mod-a=10
mod-b=20
mod-c=30
mod-d=200"

export SHARDWISE_E2E_SLEEP_TESTS=1
export SHARDWISE_E2E_SLEEP_UNIT_MS="$UNIT_MS"
export SHARDWISE_E2E_WEIGHTS="$WEIGHTS"

echo "=== benchmark: N=1 (baseline) ==="
env NODE_TOTAL=1 bash run-docker.sh >/tmp/bench-n1.log 2>&1
n1_times=$(grep 'BUILD SUCCESSFUL' /tmp/bench-n1.log | sed -n 's/.*BUILD SUCCESSFUL in \(.*\)/\1/p')
n1_secs=$(echo "$n1_times" | while read -r t; do to_seconds "$t"; done | sort -n | tail -1)
if [[ -z "$n1_secs" ]]; then
    echo "FAIL: cannot extract wall-clock from N=1 log"
    exit 2
fi
echo "N=1 baseline: ${n1_secs}s (max of: $(echo "$n1_times" | tr '\n' ' '))"

# Clean build artifacts so the N=3 run starts clean (same conditions as N=1).
# Use a container to rm root-owned files from the previous run.
docker run --rm -v "$(pwd)":/ws alpine rm -rf /ws/e2e/build 2>/dev/null || { echo "FAIL: cleanup failed (docker daemon down or permission error)"; exit 2; }

env NODE_TOTAL=3 bash run-docker.sh >/tmp/bench-n3.log 2>&1
n3_times=$(grep 'BUILD SUCCESSFUL' /tmp/bench-n3.log | sed -n 's/.*BUILD SUCCESSFUL in \(.*\)/\1/p')
n3_secs=$(echo "$n3_times" | while read -r t; do to_seconds "$t"; done | sort -n | tail -1)
if [[ -z "$n3_secs" ]]; then
    echo "FAIL: cannot extract wall-clock from N=3 log"
    exit 2
fi
echo "N=3 sharded: ${n3_secs}s"

echo
if (( n3_secs < n1_secs )); then
    echo "PASS: N=3 (${n3_secs}s) is faster than N=1 (${n1_secs}s)"
    exit 0
else
    echo "FAIL: N=3 (${n3_secs}s) is NOT faster than N=1 (${n1_secs}s)"
    exit 1
fi

