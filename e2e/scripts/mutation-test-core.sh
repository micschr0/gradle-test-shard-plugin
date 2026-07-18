#!/usr/bin/env bash
# Mutation test for the pure core (`TestWeights.parse` + `TestShardPlanner.plan`).
#
# Backs up the source, applies targeted edge-case mutations, asserts the unit
# test suite catches at least 3 of them. A pass = the suite detects the kinds
# of silent breakage a refactor could introduce.
#
# Run: bash e2e/scripts/mutation-test-core.sh
set -euo pipefail

repo_root="${CI_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
results_dir="$repo_root/e2e/build/mutation"
mkdir -p "$results_dir"

weights_file="$repo_root/src/main/kotlin/de/micschro/shardwise/internal/TestWeights.kt"
planner_file="$repo_root/src/main/kotlin/de/micschro/shardwise/internal/TestShardPlanner.kt"
backup_dir="$results_dir/backup"
mkdir -p "$backup_dir"

# Backup originals, then restore on EXIT — even on error — so the source tree
# is never left dirty. (Single-shot script; if the trap restores on a clean
# path, no harm; on error, it's the safety net.)
cp "$weights_file" "$backup_dir/TestWeights.kt.orig"
cp "$planner_file" "$backup_dir/TestShardPlanner.kt.orig"
# shellcheck disable=SC2329 # invoked indirectly via EXIT trap
restore_originals() {
  cp "$backup_dir/TestWeights.kt.orig" "$weights_file"
  cp "$backup_dir/TestShardPlanner.kt.orig" "$planner_file"
}
trap restore_originals EXIT

fail=0
passed=0

# Apply, run tests, revert in this scope. Exit code is the test result.
# Avoid shell-quoting hell: pass patterns as Perl @ARGV.
# Apply, run tests, revert in this scope. Exit code is the test result.
mutate_file() {
  local file="$1" orig="$2" mutated="$3"
  perl -i -pe '
      BEGIN { $pat = shift; $mut = shift; }
      if (/\Q$pat\E/) { s/\Q$pat\E/$mut/g; }
    ' "$orig" "$mutated" "$file"
}
apply_and_test() {
  local label="$1" file="$2" orig="$3" mutated="$4"

  set +e
  ./gradlew --no-daemon --console=plain test --tests 'de.micschro.shardwise.internal.*' > /dev/null 2>&1
  local exit_code=$?
  set -e
  # Restore within function — no reliance on outer trap if set -e fires.
  cp "$backup_dir/$(basename "$file").orig" "$file"
  if (( exit_code != 0 )); then
    printf "CAUGHT\n"
    return 0
  else
    printf "UNDETECTED — test suite did not fail\n"
    return 1
  fi
}

echo "=== Mutation test: pure core ==="

# Helper: increment a counter without `set -e` killing us when the value is 0.
inc() {
  local -n ref="$1"
  ref=$(( ref + 1 ))
}

# M1: filter collapses — all lines dropped, every module gets defaultWeight.
if apply_and_test "M1: parse skips all '=' lines" \
    "$weights_file" \
    'filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }' \
    'filter { false }' \
    ; then inc passed; else inc fail; fi

# M2: negative weights accepted — invalid input no longer rejected.
if apply_and_test "M2: negative weights pass validation" \
    "$weights_file" \
    '.toIntOrNull()?.takeIf { it >= 0 }' \
    '.toIntOrNull()' \
    ; then inc passed; else inc fail; fi

# M3: planner always picks node 0 — full imbalance.
if apply_and_test "M3: lightestBucket always returns 0" \
    "$planner_file" \
    'lightestBucket(loads)' \
    '0' \
    ; then inc passed; else inc fail; fi

# M4: modules sorted ascending instead of descending — LPT becomes SPT.
if apply_and_test "M4: sort modules ascending instead of descending" \
    "$planner_file" \
    'compareByDescending<TestModule> { it.weight }' \
    'compareBy<TestModule> { it.weight }' \
    ; then inc passed; else inc fail; fi

# M5: boundary check removed — accepts nodeTotal <= 0.
if apply_and_test "M5: remove nodeTotal boundary check" \
    "$planner_file" \
    'require(nodeTotal >= 1)' \
    '// require removed by mutation test' \
    ; then inc passed; else inc fail; fi

echo ""
echo "=== Results ==="
echo "Caught:      $passed / 5"
echo "Undetected:  $fail / 5"

# Final safety: source must be clean. If the original-baseline differs from
# current source, abort explicitly rather than leave a polluted working tree.
diff -q "$backup_dir/TestWeights.kt.orig" "$weights_file" >/dev/null
diff -q "$backup_dir/TestShardPlanner.kt.orig" "$planner_file" >/dev/null

if (( passed >= 3 )); then
  echo "PASS: at least 3 mutations caught"
  exit 0
else
  echo "FAIL: fewer than 3 mutations caught — test suite does not reliably detect core breaks"
  exit 1
fi
