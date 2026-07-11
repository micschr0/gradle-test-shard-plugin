# Shardwise configuration reference

## Overview

The `shardwise {}` extension applies to the **root project** only. It declares
which `Test` tasks to shard, an optional weights file for historical timing
data, a default weight for unlisted modules, and the plan-detail level for
build-start logging. The plugin reads `CI_NODE_INDEX` and `CI_NODE_TOTAL` from
the environment. When both are unset (local runs), the plugin is a no-op.

## Parameters

| Property | Type | Default | Description |
|---|---|---|---|
| `taskNames` | `SetProperty<String>` | `setOf("test")` | `Test` task names to shard; each gets its own plan |
| `weightsFile` | `RegularFileProperty` | none (`test-weights.properties` in the root project) | Per-module timing file; without it every module uses `defaultWeight` (count-based balancing). See [Weights file format](#weights-file-format). |
| `defaultWeight` | `Property<Int>` | `10` | Weight for modules not in the weights file |
| `planDetail` | `Property<PlanDetail>` | `FULL` | How much of the plan to log at build start |

## PlanDetail

`PlanDetail` (import `de.micschro.shardwise.PlanDetail`) controls the dashboard
logged once per sharded task at build start:

| Level | Output |
|---|---|
| `OFF` | Nothing |
| `SUMMARY` | Task name, module count, coverage status |
| `FULL` | Adds per-node module lists, load bars, and the imbalance ratio |

Nothing prints when `CI_NODE_TOTAL` is unset or `1` — there is nothing to
shard. The plugin emits colour only when stdout is a TTY, so CI logs stay free
of escape codes. The level affects output only: it never changes which module
runs on which shard.

<a id="weights-file-format"></a>

## Weights file format

```properties
# modulePath=millis (module path with '/' instead of ':', no leading ':'; root project = '.')
services/checkout/checkout-service=120000
common/common-domain=500
```

**Parser semantics:**

- Lines without `=` are ignored
- Non-numeric or negative values after `=` fall back to `defaultWeight`
- Modules not listed also receive `defaultWeight`
- The same per-module weight feeds every task type's plan
- Weights affect balance, never coverage: every module is assigned to exactly
  one shard
- Stale weights shift load but never lose tests

## Inspecting the plan

The plugin has no report task, but it can dump the full plan a node derived to a
file. Set the `shardwise.planDump` system property to a path:

```bash
CI_NODE_INDEX=2 CI_NODE_TOTAL=3 ./gradlew test -Dshardwise.planDump=plan.txt
```

Each sharded task's plan (node → modules) is written to that file. Run it once
per node index and diff the output to prove all nodes agree on the plan — the
one check task outcomes alone cannot show. Off unless the property is set.

## Examples

Generate a weights file from JUnit XML timings with the bundled task:

```bash
./gradlew test --no-build-cache
./gradlew generateTestWeights
```

The aggregator walks every `Test` task output declared in `taskNames` (default:
`test`), sums the `time=` attribute per module, and writes a sorted ISO-8859-1
properties file (millisecond totals, descending by weight) at the root project.

For automated weight maintenance — scheduled bot commits or
every-run-feeds-the-next — see [Self-updating weights](self-updating-weights.md).

## Notes (FAQ)

**My test tasks show `FROM-CACHE` or `UP-TO-DATE` — does the plugin still work?**
Yes. The sharding decision happens before task execution. `SKIPPED` means the
plugin decided the module does not belong on this node; `FROM-CACHE` or
`UP-TO-DATE` means the task ran but Gradle restored the result from cache. Both
are correct.

**A module ran on zero nodes or on multiple nodes. What happened?**
Usually the parallel nodes read different weights files. See
[troubleshooting: ran on zero nodes](troubleshooting.md#step-2--diagnose-ran-on-zero-nodes)
for the full diagnosis and fix.
