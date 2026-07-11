#!/usr/bin/env bash
# Pulls the shard-test node logs out of glci's history store, so the caller can
# read what each node actually printed.
#
# Under glci every job runs in its own container with its own clone, so nothing a
# node writes is visible on the runner afterwards. Two copies survive: the job's
# ARTIFACT, and the job's TRACE. This script reads the TRACE, on purpose.
#
# WHY NOT `glci artifacts extract`: it refuses to hand back the artifact of a job
# that did not pass. glci v0.6.0 `FindLatestArtifact` (pkg/history/history.go)
# skips any job whose status is not passed/success *before* it even looks for the
# zip, so a red node's artifact is unreachable by name alone — which is precisely
# the node whose log the failure scenario needs. Our `artifacts: when: always` is
# honoured and the zip is on disk; the CLI just will not return it. Do not
# "simplify" this back to `glci artifacts extract <job>`.
#
# The trace has no such gate: glci tees every job's output to
# <store>/pipelines/<id>/jobs/<sanitized-name>.log for every job, whatever its
# status, and `glci log <pipeline-id> <job-name>` reads exactly that file. Since
# run-node.sh pipes Gradle through `tee`, the trace carries the same build output
# as the artifact's node log.
#
# FAIL-CLOSED: a node whose trace cannot be read is a failure, not an empty file.
# The caller uses these logs as evidence, and absent evidence must never read as a pass.
#
# Usage: glci-fetch-node-logs.sh <node-total> <output-dir>
set -euo pipefail

nodes="${1:?usage: glci-fetch-node-logs.sh <node-total> <output-dir>}"
out="${2:?usage: glci-fetch-node-logs.sh <node-total> <output-dir>}"

# Pin the pipeline explicitly instead of letting `glci log` default to "latest".
# The store is keyed by working directory and accumulates every run, so a bare
# default would silently read a previous pipeline's trace if this run never got
# far enough to record one — reporting a stale pass. Resolve the id, and fail if
# there is none.
pipeline_id="$(glci history --limit 1 | awk '/^#[0-9]+/ { sub(/^#/, "", $1); print $1; exit }')"
if [[ -z "$pipeline_id" ]]; then
    echo "FAIL: glci recorded no pipeline — there are no node traces to read"
    exit 1
fi

rm -rf "$out"
mkdir -p "$out"

for i in $(seq 1 "$nodes"); do
    if ! glci log "$pipeline_id" "shard-test $i/$nodes" >"$out/node-$i.log"; then
        echo "FAIL: no trace for shard-test $i/$nodes in pipeline #$pipeline_id — cannot obtain its node log"
        exit 1
    fi
    # An empty trace is absent evidence, not a node that printed nothing: glci
    # creates the log file the moment the job starts, so a job that died before
    # producing output leaves one behind. Treat it as a failure.
    if [[ ! -s "$out/node-$i.log" ]]; then
        echo "FAIL: trace for shard-test $i/$nodes in pipeline #$pipeline_id is empty — no evidence of what the node did"
        exit 1
    fi
done
