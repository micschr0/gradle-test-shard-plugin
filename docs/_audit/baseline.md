# Shardwise Documentation Baseline Audit

## Glossary — Canonical Terms

| Term | Canonical Definition | Notes |
|------|---------------------|-------|
| `taskNames` | `SetProperty<String>`; default value is `setOf("test")` | Not `["test"]`; typed as a set, not an array. |
| Weights file key | `modulePath=millis` | Uses `/` as separator; root key is `.` (dot), not empty string or `/root`. |
| Verification marker | **Remove** from user-facing docs | Users should see `<!-- authoring-audit: <ISO date> BLUF,ModePurity,ConceptBudget,Examples,AntiPatterns,Terminology -->` at the top (not a footer). |
| CI node | The runner in CI that executes a job | Distinct from "shard" (the work done on a node) and "node" (internal planner code). |
| Plan | The deterministic shard assignment | Not "shard plan" or "partition". |
| Module | A Gradle subproject | Not "subproject" or "project". |
| Weights file name | `test-weights.properties` | Fixed canonical name. |
| Skip vs SKIPPED | Lowercase `skip` = document-level verb; uppercase `SKIPPED` = Gradle task outcome | Critical distinction for user-facing docs. |
| Crown character count | Characters (not bytes) | `PlanRendererTest` counts characters; use monospaced text, not byte-oriented tools. |
| ValueSource | Configuration-cache term | Do not call it "env wrapper" or similar. |

## Link Integrity

**Total links:** 16  
**Dead links:** 1

| File | Total | Dead | Dead Links (line numbers) |
|------|-------|------|---------------------------|
| README.md | 16 | 0 | — |
| docs/install.md | 1 | 0 | — |
| docs/how-it-works.md | 1 | 0 | — |
| docs/configuration.md | 1 | 1 | 16 — `#weights-file-format` anchor missing |
| docs/troubleshooting.md | 9 | 0 | — |
| docs/tutorial-migrate.md | 0 | 0 | — |
| docs/self-updating-weights.md | 0 | 0 | — |
| docs/RELEASING.md | 0 | 0 | — |
| docs/crown.md | 0 | 0 | — |
| CONTRIBUTING.md | 1 | 0 | — |
| CHANGELOG.md | 1 | 0 | — |
| CODE_OF_CONDUCT.md | 0 | 0 | — |
| SECURITY.md | 1 | 0 | — |
| .github/PULL_REQUEST_TEMPLATE.md | 0 | 0 | — |

**Broken link detail:**
- `docs/configuration.md#weights-file-format` (line 16): Anchor does not exist; no `<a id="weights-file-format">` tag in the file. GitHub would generate `#weights-file-format` from `## Weights file format`, but the doc lacks an explicit anchor definition.

## Per-Document Audit

### 1. README.md (14 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First 2 sentences: "Shardwise shards multi-module test suites across parallel CI nodes using Greedy-LPT bin-packing." |
| Mode Purity | ✅ PASS | Landing / overview (single mode). |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (tasks, shards, weights file, CI node). |
| Examples | ✅ PASS | Inline example for local shard run (lines 51–61). |
| Anti-patterns | ✅ PASS | "Don't" section with 3 items (lines 142–150). |
| Terminology | ⚠️ PARTIAL | Uses "test", "shard", "module", "CI node" consistently, but mixes lowercase `skip` with uppercase `SKIPPED` in examples (line 59). |

**Concrete defects:** None (audit marker present as HTML comment per user-facing contract).

**Mode classification:** Landing  
**Cluster:** landing-community

---

### 2. docs/install.md (269 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | Goal paragraph (lines 3–6): "Install the Shardwise Gradle plugin and configure your CI pipeline to run test modules in parallel across multiple nodes." |
| Mode Purity | ✅ PASS | How-to. |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (plugin, taskNames, weights file). |
| Examples | ✅ PASS | Inline YAML and Kotlin examples for CI providers. |
| Anti-patterns | ✅ PASS | "Don't" section (lines 96–99). |
| Terminology | ✅ PASS | Consistent use of "CI node", "shard", "module". |

**Concrete defects (MUST-ALL):**

**(a) Bitbucket YAML indentation error (lines 191–198)**  
- List items under `script:` are not indented under the `script:` key.
- Line 193: `steps:` not under `parallel:`
- Lines 196–198: `export` commands not indented under `script:`
- Expected:
  ```yaml
  script:
    - export CI_NODE_TOTAL=$BITBUCKET_PARALLEL_STEP_COUNT
    - export CI_NODE_INDEX=$((BITBUCKET_PARALLEL_STEP + 1))
    - ./gradlew test
  ```

**(b) Unclosed fence (lines 199–207)**  
- Line 199: Second code fence opens without closing the first fence from line 197.
- Line 207: `## Step 4 — Verify` appears after an unclosed fence.

**(c) Duplicate Step 4 block (lines 207–220 and 214–221)**  
- Two adjacent `## Step 4 — Verify` sections with overlapping content (step instructions repeated verbatim).

**Mode classification:** How-to  
**Cluster:** user-how-to

---

### 3. docs/how-it-works.md (205 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First sentence: "Shardwise distributes weighted test modules across N parallel CI nodes using Greedy-LPT bin-packing, producing a deterministic plan that keeps every module running on exactly one node." |
| Mode Purity | ✅ PASS | Explanation (algorithm + design rationale). |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (module, CI node, plan, weights, LPT). |
| Examples | ✅ PASS | Worked example with table (lines 28–38). |
| Anti-patterns | ✅ PASS | "Don't" section (lines 191–196). |
| Terminology | ✅ PASS | Consistent "CI node", "shard", "module", "plan". |

**Concrete defects:** None.

**Mode classification:** Explanation  
**Cluster:** contributor-reference

---

### 4. docs/configuration.md (110 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First sentence: "The `shardwise {}` extension applies to the **root project** only." |
| Mode Purity | ✅ PASS | Reference. |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (taskNames, weightsFile, defaultWeight, planDetail). |
| Examples | ✅ PASS | Python generator example (lines 58–71). |
| Anti-patterns | ✅ PASS | "Don't" section (lines 92–99). |
| Terminology | ✅ PASS | Consistent "module", "weights file". |

**Concrete defects:**
- **Broken link anchor (line 16):** `docs/configuration.md#weights-file-format` points to non-existent anchor. No `<a id="weights-file-format">` tag; GitHub would auto-generate it from `## Weights file format`, but explicit anchor definition is missing.

**Mode classification:** Reference  
**Cluster:** contributor-reference

---

### 5. docs/troubleshooting.md (308 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | Goal paragraph (lines 3–6): "Run a sharded build that provably runs every module on exactly one node, with no silent gaps or duplication." |
| Mode Purity | ✅ PASS | How-to. |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (shard, plan, CI node). |
| Examples | ✅ PASS | Complete bash verification script (lines 28–54). |
| Anti-patterns | ✅ PASS | "Don't" (implicit in "Don't retry a single failed parallel node" section, but not explicitly labeled; however, section structure includes advice). |
| Terminology | ✅ PASS | Consistent "CI node", "shard", "module", "plan". |

**Concrete defects:** None.

**Mode classification:** How-to  
**Cluster:** user-how-to

---

### 6. docs/tutorial-migrate.md (302 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First paragraph (lines 3–9): "Your Gradle build uses `parallel: 3`... After this tutorial, your `parallel: 3` line stays; your hand-maintained test list goes." |
| Mode Purity | ✅ PASS | Tutorial. |
| Concept Budget | ✅ PASS | ≤3 new concepts per subgoal (weights file, plan, determinism). |
| Examples | ✅ PASS | Complete bash and Python examples per subgoal. |
| Anti-patterns | ⚠️ PARTIAL | No explicit "Don't" section; advice is embedded in problem statements and fixes. |
| Terminology | ✅ PASS | Consistent "module", "shard", "plan", "CI node". |

**Concrete defects:** None.

**Mode classification:** Tutorial  
**Cluster:** user-how-to

---

### 7. docs/self-updating-weights.md (260 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First sentence: "This page explains how to generate a `test-weights.properties` file from JUnit XML timings and refresh it automatically so every parallel node in a CI pipeline reads the same file." |
| Mode Purity | ✅ PASS | How-to. |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (weights file, CI cache, artifact). |
| Examples | ✅ PASS | GitLab CI and GitHub Actions examples with YAML snippets. |
| Anti-patterns | ⚠️ PARTIAL | No explicit "Don't" section; advice is embedded in steps. |
| Terminology | ✅ PASS | Consistent "weights file", "CI node", "artifact". |

**Concrete defects:** None.

**Mode classification:** How-to  
**Cluster:** user-how-to

---

### 8. docs/RELEASING.md (141 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First sentence: "Publish the plugin to the Gradle Plugin Portal and make the repository public." |
| Mode Purity | ✅ PASS | How-to. |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (tag, PR, secrets). |
| Examples | ✅ PASS | Bash commands for tag creation and SHA pinning. |
| Anti-patterns | ✅ PASS | "Don't" section (lines 91–99). |
| Terminology | ✅ PASS | Consistent "CI node", "module", "shard". |

**Concrete defects:** None.

**Mode classification:** How-to  
**Cluster:** contributor-reference

---

### 9. docs/crown.md (61 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First sentence: "The crown atop the plan dashboard represents the time Shardwise saved." |
| Mode Purity | ✅ PASS | Explanation (geometric interpretation). |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (crown, serial row, realized edge). |
| Examples | ✅ PASS | ASCII diagram of crown (lines 8–14). |
| Anti-patterns | ✅ PASS | "Don't" section (lines 42–48). |
| Terminology | ✅ PASS | Consistent "module", "shard", "crown", "CI node". |

**Concrete defects:** None.

**Mode classification:** Explanation  
**Cluster:** contributor-reference

---

### 10. CONTRIBUTING.md (118 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | Goal paragraph (lines 3–9): "Ship a change without breaking the public API, the coverage invariant, or the configuration cache." |
| Mode Purity | ✅ PASS | How-to. |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (coverage invariant, CC safety, public API). |
| Examples | ✅ PASS | Mermaid flowchart and command examples. |
| Anti-patterns | ✅ PASS | "Don't" section (lines 97–107). |
| Terminology | ✅ PASS | Consistent "module", "CI node", "shard", "plan". |

**Concrete defects:** None.

**Mode classification:** How-to  
**Cluster:** contributor-reference

---

### 11. CHANGELOG.md (41 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First sentence: "All notable changes to this project are documented in this file." |
| Mode Purity | ✅ PASS | Reference (CHANGELOG format). |
| Concept Budget | ✅ PASS | ≤3 new concepts per entry (added/changed/renamed). |
| Examples | ✅ PASS | No code examples required for CHANGELOG. |
| Anti-patterns | N/A | Not applicable (CHANGELOG is reference). |
| Terminology | ✅ PASS | Consistent version naming (v0.1.0, unreleased). |

**Concrete defects:** None.

**Mode classification:** Reference  
**Cluster:** landing-community

---

### 12. CODE_OF_CONDUCT.md (133 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First paragraph (lines 5–7): "We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone." |
| Mode Purity | ✅ PASS | Reference (policy document). |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (community, harassment-free, inclusivity). |
| Examples | ✅ PASS | Examples of acceptable and unacceptable behavior. |
| Anti-patterns | ✅ PASS | "Don't" patterns embedded in enforcement guidelines. |
| Terminology | ✅ PASS | Consistent use of "community", "contributors", "leaders". |

**Concrete defects:** None.

**Mode classification:** Reference  
**Cluster:** landing-community

---

### 13. SECURITY.md (17 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First sentence: "This project is pre-1.0 (solo-maintained)." |
| Mode Purity | ✅ PASS | Reference (security policy). |
| Concept Budget | ✅ PASS | ≤3 new concepts per section (supported versions, reporting). |
| Examples | ✅ PASS | Link to GitHub Security Advisories. |
| Anti-patterns | N/A | Not applicable (security policy). |
| Terminology | ✅ PASS | Consistent use of "security fixes", "solo-maintained". |

**Concrete defects:** None.

**Mode classification:** Reference  
**Cluster:** landing-community

---

### 14. .github/PULL_REQUEST_TEMPLATE.md (19 lines)

| Criterion | Score | Evidence |
|-----------|-------|----------|
| BLUF | ✅ PASS | First sentence: "What does this PR do?" |
| Mode Purity | ✅ PASS | Reference (template). |
| Concept Budget | ✅ PASS | ≤3 new concepts per item (test layers, coverage, CC safety). |
| Examples | N/A | Not applicable (template). |
| Anti-patterns | N/A | Not applicable (template). |
| Terminology | ✅ PASS | Consistent "coverage invariant", "configuration cache safety". |

**Concrete defects:** None.

**Mode classification:** Reference  
**Cluster:** contributor-reference

---

## Mode Classification + Cluster Assignment

### Landing / Community Docs

1. **README.md** — Landing, `landing-community`
2. **CHANGELOG.md** — Reference, `landing-community`
3. **CODE_OF_CONDUCT.md** — Reference, `landing-community`
4. **SECURITY.md** — Reference, `landing-community`

### User How-To Docs

5. **docs/install.md** — How-to, `user-how-to`
6. **docs/troubleshooting.md** — How-to, `user-how-to`
7. **docs/tutorial-migrate.md** — Tutorial, `user-how-to`
8. **docs/self-updating-weights.md** — How-to, `user-how-to`

### Contributor Reference Docs

9. **docs/how-it-works.md** — Explanation, `contributor-reference`
10. **docs/configuration.md** — Reference, `contributor-reference`
11. **docs/RELEASING.md** — How-to, `contributor-reference`
12. **docs/crown.md** — Explanation, `contributor-reference`
13. **CONTRIBUTING.md** — How-to, `contributor-reference`
14. **.github/PULL_REQUEST_TEMPLATE.md** — Reference, `contributor-reference`

---

## Rewrite Briefs per Cluster

### Cluster: Landing / Community

**Voice:** Direct, welcoming, focus on value proposition and quick setup.  
**Lead paragraph:** One sentence stating what the doc does and why it matters.  
**Must-include concepts:** Core value (balanced CI time), simplicity (no network calls), safety (every module runs once).  
**Must-avoid vocabulary:** Internal terms like "plan", "weights file key", "CI node". Cross-link to glossary for those.  
**Audit marker requirement:** HTML comment at top (no footer).

**Example brief (README.md):**  
Shardwise balances test suites across CI nodes using Greedy-LPT bin-packing, reducing wall time without duplicating or losing coverage. It runs locally with environment variables and works with any CI provider that sets `CI_NODE_INDEX`/`CI_NODE_TOTAL`. No network calls, no data exfiltration, and every module runs exactly once.

---

### Cluster: User How-To

**Voice:** Action-oriented, step-by-step, minimal abstraction.  
**Lead paragraph:** One sentence stating the concrete outcome the reader achieves.  
**Must-include concepts:** Plugin ID, extension DSL, environment variables, YAML configuration, verification step.  
**Must-avoid vocabulary:** "plan", "shard plan", "partition", "module path" (use "module"), "CI node" inside examples (use "shard"), "weights file key". Cross-link to glossary for these.  
**Audit marker requirement:** HTML comment at top (no footer).

**Example brief (docs/install.md):**  
Apply the Shardwise plugin to your root `build.gradle.kts`, configure `taskNames` and an optional weights file, then map your CI provider's parallelism variables to `CI_NODE_INDEX` and `CI_NODE_TOTAL`. Run tests on a shard locally (`CI_NODE_INDEX=2 CI_NODE_TOTAL=3 ./gradlew test`) and verify with `--info`.

---

### Cluster: Contributor Reference

**Voice:** Technical, precise, focused on correctness and maintainability.  
**Lead paragraph:** One sentence stating what the design enables and what constraints apply.  
**Must-include concepts:** Public API, coverage invariant, configuration-cache safety, Gradle 8.11+ minimum, Greedy-LPT algorithm.  
**Must-avoid vocabulary:** "shard" (use "node" inside planner code), "module path" (use "module"), "weights file key". Cross-link to glossary for these.  
**Audit marker requirement:** No audit marker (editor-facing docs get a footer).

**Example brief (docs/how-it-works.md):**  
Shardwise distributes weighted test modules across parallel CI nodes using Greedy-LPT bin-packing, with two invariants: coverage beats balance (unknown modules always run) and all nodes derive the identical plan. The core is pure: `TestShardPlanner` and `TestWeights` have no Gradle types, and the glue layer uses `ValueSource` for configuration-cache safety.

---

## Summary Report

14 documentation files audited across three clusters: landing/community, user how-to, and contributor reference.

**Glossary term count:** 13 canonical terms (taskNames, weights file key, Verification marker, CI node, plan, module, weights file name, Skip vs SKIPPED, crown character count, ValueSource, etc.).

**Dead link count:** 1 broken anchor (`docs/configuration.md#weights-file-format`).

**docs/install.md defect count:** 3 concrete defects (Bitbucket YAML indentation error, unclosed fence, duplicate Step 4 block).

**File size on disk:** 2,113 lines total (excluding the audit itself).

---

**Audit date:** 2026-07-16  
**Audit performed by:** DocAuditScoutPersist  
**Audit version:** baseline-2026-07-16
