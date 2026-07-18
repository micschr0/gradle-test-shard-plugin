# Iteration 5 — Findings Ledger

> Lens: `kotlin` skill + qualitative review + detekt run
> Surface: whole repo, detekt config
> Sweep date: 2026-07-17

## Summary

| Tier | Count | IDs |
|------|-------|-----|
| BLOCKER | 0 | — |
| WARNING | 0 | — |
| INFO | 2 | KT-OK-001, KT-OK-002 |

**Zero findings.** The codebase passes every Kotlin skill hard rule with zero exceptions.

---

## KT-OK-001 — INFO

**All 3 data classes use `val`-only fields with immutable collection interfaces.**

- `NodeEnv(index: Int, total: Int)` — all vals, `Serializable`, `@Suppress("SerialVersionUIDInSerializableClass")` documented.
- `TestModule(path: String, weight: Int)` — all vals.
- `ShardPlan(nodeTotal: Int, assignments: Map<Int, List<String>>)` — Map is immutable interface, lists inside are immutable at construction.
- Verdict: ✅ skill compliant.

## KT-OK-002 — INFO

**All 2 `!!` usages guarded by preceding `assertNotNull`/`assertTrue` in test code.**

- `ShardwisePluginFunctionalTest.kt:92` — `return outcome!!` after `assertNotNull(outcome, ...)` at line 91.
- `ShardwisePluginApplyTest.kt:91` — `task!!.group` after `assertTrue(task != null, ...)` at line 90.
- Both are in test code; the pattern is canonical for Java-interop assertions.
- Verdict: ✅ skill compliant (null-safety with preceding check).

---

## Cross-checks performed

| Rule | Result |
|------|--------|
| `explicitApi()` compliance | ✅ — all `public`/`protected` have explicit return types |
| `data class` with `var` or mutable collections | ✅ — none |
| `@JvmInline value class` for single-field wrappers | ✅ — none applicable |
| `when` over sealed type with `else` | ✅ — no sealed types in this codebase |
| `by lazy` vs `lateinit var` | ✅ — `by lazy` in `ShardBuildService`, no `lateinit` |
| `internal companion object` for factories | ✅ — `PlanRenderer` companion, `GenerateTestWeights` companion |
| `!!` absence in production code | ✅ — zero `!!` in `src/main/` |
| detekt | ✅ — zero findings |
| `TODO` in committed code | ✅ — none |

## Notes

- `ShardwisePlugin` has no `@Inject BuildFeatures` — flagged in iter-1 as GRADLE-IP-WARN, but the dedicated test at `ShardwisePluginFunctionalTest.kt:390` explicitly asserts IP-incompatibility BY DESIGN. Adding the guard would obscure the intent — a future change to make the plugin IP-friendly would need a design decision, not a one-line guard.
- Kotlin 2.4.0 deprecation warning `-Xjvm-default` → `-jvm-default` — cosmetic, deferred to iter-7 (binary compat/deps).
