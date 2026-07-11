#!/usr/bin/env bash
# One simulated CI node: copies the consumer project to a scratch dir (an isolated
# workspace, as a real runner would have), runs its sharded `test` task there, and
# stores the build log for the verify stage.
#
# Scenario knobs, all optional (the pipeline sets them; see e2e/README.md):
#   SHARDWISE_E2E_WEIGHTS       weights file content, or "missing" to point at a
#                               path that does not exist
#   SHARDWISE_E2E_PLAN_DETAIL   OFF | SUMMARY | FULL
#   SHARDWISE_E2E_FAIL_MODULE   module whose test is made to fail, to prove the
#                               pipeline cannot pass by skipping the failure
set -euo pipefail

# glci expands `parallel: N` into job names but sets no CI_NODE_* variables, so recover
# them from CI_JOB_NAME when — and only when — the runner left them unset. Fail-closed:
# it errors out rather than guess an index. Real GitLab CI and run-docker.sh set them
# and take the pass-through path untouched.
# shellcheck source=e2e/scripts/resolve-node-env.sh
source "$(dirname "$0")/resolve-node-env.sh"

: "${CI_NODE_INDEX:?must be set by the pipeline (parallel:)}"
: "${CI_NODE_TOTAL:?must be set by the pipeline (parallel:)}"

repo_root="${CI_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cp -r "$repo_root/e2e/consumer" "$work/consumer"
consumer="$work/consumer"

log_dir="$repo_root/e2e/build/logs"
plan_dir="$repo_root/e2e/build/plans"
mkdir -p "$log_dir" "$plan_dir"

# Have the plugin write down the plan THIS node derived. verify.sh compares the
# dumps across nodes: identical plans prove the planner is deterministic, and
# matching each node's dump against what it actually ran catches an index
# mapping bug that leaves "every module runs exactly once" perfectly intact.
gradle_args=(
    -p "$consumer" test --console=plain
    "-Dshardwise.planDump=$plan_dir/node-$CI_NODE_INDEX.txt"
)

# Weights: either write a file, or deliberately point at one that is absent.
# Coverage must survive both — a missing or stale weights file may never skip a module.
if [[ -n "${SHARDWISE_E2E_WEIGHTS:-}" ]]; then
    if [[ "$SHARDWISE_E2E_WEIGHTS" == "missing" ]]; then
        gradle_args+=(-Pshardwise.weights=absent-weights.properties)
    else
        printf '%s\n' "$SHARDWISE_E2E_WEIGHTS" >"$consumer/test-weights.properties"
        gradle_args+=(-Pshardwise.weights=test-weights.properties)
    fi
fi

if [[ -n "${SHARDWISE_E2E_PLAN_DETAIL:-}" ]]; then
    gradle_args+=(-Pshardwise.planDetail="$SHARDWISE_E2E_PLAN_DETAIL")
fi

# Make one module's test fail on purpose. The pipeline must go red — and must not
# be able to hide the failure by sharding it onto a node nobody checks.
if [[ -n "${SHARDWISE_E2E_FAIL_MODULE:-}" ]]; then
    fail_dir="$consumer/${SHARDWISE_E2E_FAIL_MODULE}/src/test/java"
    mkdir -p "$fail_dir"
    cat >"$fail_dir/ShardwiseE2EFailureTest.java" <<'JAVA'
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

class ShardwiseE2EFailureTest {
    @Test
    void deliberatelyFails() {
        fail("SHARDWISE_E2E_FAIL_MODULE: this failure must turn the pipeline red");
    }
}
JAVA
fi

# Use the image's own Gradle, not the root wrapper. The wrapper pins one exact
# version, which would silently override the image and make the version matrix
# test nothing — every image would run the same Gradle, and each node would
# re-download that distribution.
gradle_bin="$(command -v gradle || echo "$repo_root/gradlew")"

"$gradle_bin" "${gradle_args[@]}" | tee "$log_dir/node-$CI_NODE_INDEX.log"
