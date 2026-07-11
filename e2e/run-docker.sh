#!/usr/bin/env bash
# Docker-based executor for the e2e pipeline, used where glci cannot run
# (glci drives Docker's embedded DNS, which is broken under gVisor; it has no
# network-mode flag, so --network host cannot be handed to it).
#
# It runs the SAME stage scripts as e2e/.gitlab-ci.yml — e2e/scripts/run-node.sh
# and e2e/scripts/verify.sh — so the local and CI paths cannot drift apart.
# Anything that belongs to the pipeline itself goes in those scripts, not here.
#
# Scenario knobs (see e2e/README.md):
#   NODE_TOTAL             number of parallel shard-test nodes   (default 3)
#   IMAGE                  Gradle image to run in                (default gradle:8.11-jdk17)
#   SHARDWISE_E2E_EXPECT_FAIL=1   the pipeline is expected to FAIL; passing is the error
#   SKIP_CLEANUP=1         keep artifacts from the previous run
set -euo pipefail

cd "$(dirname "$0")/.."
PROJECT_DIR="$(pwd)"
IMAGE="${IMAGE:-gradle:8.11-jdk17}"
NODE_TOTAL="${NODE_TOTAL:-3}"
CACHE_DIR="$PROJECT_DIR/.gradle-cache"

mkdir -p "$CACHE_DIR"/caches "$CACHE_DIR"/wrapper "$CACHE_DIR"/notifications

docker info >/dev/null 2>&1 || { echo "ERROR: Docker is required."; exit 1; }

# Containers run as root, so previous artifacts may be root-owned — clean them
# from inside a container too. A failure here is fatal: stale node logs from an
# earlier run would corrupt verification.
if [[ "${SKIP_CLEANUP:-}" != "1" ]]; then
    docker run --rm --network host -v "$PROJECT_DIR:$PROJECT_DIR" -w "$PROJECT_DIR" "$IMAGE" \
        sh -c 'rm -rf e2e/build build/maven-repo'
fi

# Runs one pipeline job in a container. The project dir is bind-mounted so the
# maven repo written in the build stage is still there for the test stage, and
# ~/.gradle is mounted so the Gradle distribution is downloaded only once.
docker_job() {
    local name="$1" script="$2"
    shift 2
    echo "── job: $name ──"
    # Check the exit code explicitly: `set -e` does not fire inside a function
    # whose result is consumed by an `if`, which is exactly how run_pipeline is called.
    local exit_code=0
    docker run --rm --network host \
        -v "$PROJECT_DIR:$PROJECT_DIR" \
        -v "$CACHE_DIR:/home/gradle/.gradle" \
        -w "$PROJECT_DIR" \
        -e CI_PROJECT_DIR="$PROJECT_DIR" \
        -e HOME=/home/gradle \
        -e GRADLE_USER_HOME=/home/gradle/.gradle \
        -e SHARDWISE_E2E_NODE_TOTAL="$NODE_TOTAL" \
        -e SHARDWISE_E2E_WEIGHTS="${SHARDWISE_E2E_WEIGHTS:-}" \
        -e SHARDWISE_E2E_PLAN_DETAIL="${SHARDWISE_E2E_PLAN_DETAIL:-}" \
        -e SHARDWISE_E2E_FAIL_MODULE="${SHARDWISE_E2E_FAIL_MODULE:-}" \
        -e SHARDWISE_E2E_SLEEP_TESTS="${SHARDWISE_E2E_SLEEP_TESTS:-}" \
        -e SHARDWISE_E2E_SLEEP_UNIT_MS="${SHARDWISE_E2E_SLEEP_UNIT_MS:-}" \
        "$@" \
        "$IMAGE" \
        bash -euo pipefail -c "$script" \
        || exit_code=$?
    if (( exit_code != 0 )); then
        echo "── job: $name ✗ FAILED (exit=$exit_code, total=$NODE_TOTAL nodes) ──"
        return 1
    fi
    echo "── job: $name ✓ ──"
}

# Point mavenLocal at a directory inside the bind-mounted project, so the plugin
# published in the build stage is visible to the consumer in the test stage.
# Setting maven.repo.local directly beats symlinking ~/.m2: it does not depend on
# HOME, on the image's user, or on ~/.gradle being a symlink (which it is).
# shellcheck disable=SC2016
BEFORE_SCRIPT='
    mkdir -p "$CI_PROJECT_DIR/build/maven-repo"
    export GRADLE_OPTS="-Dmaven.repo.local=$CI_PROJECT_DIR/build/maven-repo ${GRADLE_OPTS:-}"
'

run_pipeline() {
    echo "== stage: build =="
    if [[ "${SKIP_BUILD:-}" != "1" ]]; then
        docker_job "publish-plugin" "$BEFORE_SCRIPT ./gradlew publishToMavenLocal"
    fi

    echo "== stage: test (parallel: $NODE_TOTAL) =="
    mkdir -p e2e/build/logs
    local pids=() i
    for i in $(seq 1 "$NODE_TOTAL"); do
        docker_job "shard-test $i/$NODE_TOTAL" "$BEFORE_SCRIPT bash e2e/scripts/run-node.sh" \
            -e CI_NODE_INDEX="$i" \
            -e CI_NODE_TOTAL="$NODE_TOTAL" \
            &
        pids+=($!)
    done

    local pid failed=0
    for i in "${!pids[@]}"; do
        pid="${pids[$i]}"
        if ! wait "$pid"; then
            echo "FAIL: shard-test node $((i + 1))/$NODE_TOTAL failed — see e2e/build/logs/node-$((i + 1)).log"
            failed=1
        fi
    done
    [[ "$failed" -eq 0 ]] || return 1

    echo "== stage: verify =="
    docker_job "verify-distribution" "bash e2e/scripts/verify.sh"
}

# A pipeline expected to fail must fail for the RIGHT reason. A pipeline that dies
# because the image lacks a JDK also "fails", and would satisfy a naive check while
# proving nothing. So demand evidence: the module we broke must have actually run
# and reported its test as failed.
if [[ "${SHARDWISE_E2E_EXPECT_FAIL:-}" == "1" ]]; then
    if run_pipeline; then
        echo "== FAIL: pipeline passed but was expected to fail =="
        exit 1
    fi
    fail_module="${SHARDWISE_E2E_FAIL_MODULE:?EXPECT_FAIL needs a module to break}"
    if ! grep -qE "^> Task :$fail_module:test FAILED" e2e/build/logs/node-*.log 2>/dev/null; then
        echo "== FAIL: the pipeline failed, but not because $fail_module's test failed =="
        echo "         Something else broke — this proves nothing as it stands. =="
        exit 1
    fi
    echo "== pipeline failed because $fail_module's test failed, as required ✓ =="
    exit 0
fi

run_pipeline
echo "== pipeline complete ✓ =="
