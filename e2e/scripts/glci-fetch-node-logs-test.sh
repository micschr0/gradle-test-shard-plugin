#!/usr/bin/env bash
# Tests for glci-fetch-node-logs.sh — the script that recovers each shard node's
# output from glci's history store so the failing-test scenario (E-03) can prove
# the pipeline went red for the RIGHT reason.
#
# It must be FAIL-CLOSED. Its output is EVIDENCE: if it cannot produce a node's
# log it has to say so loudly, because a caller that greps an empty directory
# finds nothing and would read that as "the wrong thing failed" — or worse, a
# future caller might read it as a pass.
#
# No Docker here: `glci run` is never invoked. A synthetic history store is written
# straight to disk (GLCI_HOME redirects glci away from the real ~/.glci) and the
# script is pointed at it, so every case runs in milliseconds.
set -uo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
fetch="$script_dir/glci-fetch-node-logs.sh"

if ! command -v glci >/dev/null; then
    echo "SKIP: glci not on PATH"
    exit 0
fi

passed=0
failed=0

# Builds a scratch project dir plus the glci history store keyed to it. glci hashes
# the project's absolute path into the store key, so the key is not ours to guess:
# let glci create the directory, then read back the one key it made.
scratch() {
    local home proj
    home="$(mktemp -d)"
    proj="$(mktemp -d)"
    (cd "$proj" && GLCI_HOME="$home" glci history >/dev/null 2>&1)
    printf '%s\n%s\n%s\n' "$home" "$proj" "$home/projects/$(ls "$home/projects")"
}

# Records a pipeline. Args: <store-root> <pipeline-id> <status>
write_pipeline() {
    local root="$1" id="$2" status="$3"
    mkdir -p "$root/pipelines/$id/jobs"
    printf '{"id":%d,"status":"%s","started_at":"2026-07-12T10:00:00Z","git_ref":"master","job_count":0}\n' \
        "$id" "$status" >"$root/pipelines/$id/pipeline.json"
}

# Records one shard node's job meta and its trace. glci maps "/" and " " in a job
# name to "_" when it names the files on disk, so "shard-test 2/3" → "shard-test_2_3".
# Args: <store-root> <pipeline-id> <index> <total> <status> <trace-content>
write_node() {
    local root="$1" id="$2" i="$3" n="$4" status="$5" trace="$6"
    local base="$root/pipelines/$id/jobs/shard-test_${i}_${n}"
    printf '{"name":"shard-test %d/%d","status":"%s","stage":"test","order_index":%d}\n' \
        "$i" "$n" "$status" "$i" >"$base.json"
    printf '%b' "$trace" >"$base.log"
}

# Runs the fetch script from inside the scratch project, against the scratch store.
run_fetch() {
    local home="$1" proj="$2" nodes="$3" out="$4"
    (cd "$proj" && GLCI_HOME="$home" bash "$fetch" "$nodes" "$out") >/dev/null 2>&1
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

echo "glci-fetch-node-logs.sh — evidence recovery"

# ── The case the whole script exists for: the node that FAILED. glci refuses to
#    return a red job's artifact (status filter in FindLatestArtifact), so the
#    trace is the only reachable copy — and it must come back.
mapfile -t s < <(scratch)
home="${s[0]}" proj="${s[1]}" root="${s[2]}"
out="$(mktemp -d)/fetched"
write_pipeline "$root" 4 failed
write_node "$root" 4 1 3 passed '> Task :mod-a:test\n'
write_node "$root" 4 2 3 passed '> Task :mod-b:test\n'
write_node "$root" 4 3 3 failed '> Task :mod-c:test FAILED\nBUILD FAILED\n'
run_fetch "$home" "$proj" 3 "$out"
expect "fetches every node's trace, including the failed one" 0 $?
grep -qrE '^> Task :mod-c:test FAILED' "$out"
expect "the failed node's FAILED line survives into the fetched log" 0 $?
rm -rf "$home" "$proj" "$out"

# ── The failing module lands wherever the planner puts it. The caller greps the
#    whole output dir, so the evidence must be found on node 1 just as on node 3.
mapfile -t s < <(scratch)
home="${s[0]}" proj="${s[1]}" root="${s[2]}"
out="$(mktemp -d)/fetched"
write_pipeline "$root" 4 failed
write_node "$root" 4 1 3 failed '> Task :mod-c:test FAILED\n'
write_node "$root" 4 2 3 passed '> Task :mod-a:test\n'
write_node "$root" 4 3 3 passed '> Task :mod-b:test\n'
run_fetch "$home" "$proj" 3 "$out"
grep -qrE '^> Task :mod-c:test FAILED' "$out"
expect "finds the FAILED line on whichever node the planner picked" 0 $?
rm -rf "$home" "$proj" "$out"

# ── The store accumulates every run. Reading the previous pipeline's trace would
#    report a stale result for a run that never produced one — so the LATEST
#    pipeline is the only one that may be read.
mapfile -t s < <(scratch)
home="${s[0]}" proj="${s[1]}" root="${s[2]}"
out="$(mktemp -d)/fetched"
write_pipeline "$root" 1 success
write_node "$root" 1 1 1 passed '> Task :mod-a:test\nSTALE PIPELINE\n'
write_pipeline "$root" 2 failed
write_node "$root" 2 1 1 failed '> Task :mod-c:test FAILED\n'
run_fetch "$home" "$proj" 1 "$out"
expect "reads a store that holds several pipelines" 0 $?
grep -qr 'STALE PIPELINE' "$out"
expect "reads the latest pipeline, not an earlier one" 1 $?
rm -rf "$home" "$proj" "$out"

# ── Nothing ran at all. Absent evidence is a failure, never an empty pass.
mapfile -t s < <(scratch)
home="${s[0]}" proj="${s[1]}"
out="$(mktemp -d)/fetched"
run_fetch "$home" "$proj" 3 "$out"
expect "fails when glci recorded no pipeline at all" 1 $?
rm -rf "$home" "$proj" "$out"

# ── A node left no trace: it never started, or N is wrong. Either way the caller
#    would be grepping an incomplete set of logs and could not tell.
mapfile -t s < <(scratch)
home="${s[0]}" proj="${s[1]}" root="${s[2]}"
out="$(mktemp -d)/fetched"
write_pipeline "$root" 4 failed
write_node "$root" 4 1 3 passed '> Task :mod-a:test\n'
write_node "$root" 4 2 3 passed '> Task :mod-b:test\n'
# node 3 has no trace on disk.
run_fetch "$home" "$proj" 3 "$out"
expect "fails when a node's trace is missing instead of returning a partial set" 1 $?
rm -rf "$home" "$proj" "$out"

# ── An empty trace is not "a node that printed nothing": glci creates the file
#    when the job starts, so an empty one means the node died before saying
#    anything. It proves nothing and must not be handed back as evidence.
mapfile -t s < <(scratch)
home="${s[0]}" proj="${s[1]}" root="${s[2]}"
out="$(mktemp -d)/fetched"
write_pipeline "$root" 4 failed
write_node "$root" 4 1 2 passed '> Task :mod-a:test\n'
write_node "$root" 4 2 2 failed ''
run_fetch "$home" "$proj" 2 "$out"
expect "fails on an empty trace rather than passing off silence as evidence" 1 $?
rm -rf "$home" "$proj" "$out"

echo
echo "passed: $passed, failed: $failed"
[[ "$failed" -eq 0 ]]
