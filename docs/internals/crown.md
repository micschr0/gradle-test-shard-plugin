<!-- authoring-audit: 2026-07-16 BLUF,ModePurity,ConceptBudget,Examples,AntiPatterns,Terminology -->

# The crown: why the banner is mostly empty

The crown atop the plan dashboard represents the time Shardwise saved. Understanding it keeps you able to maintain the banner without breaking its message.

A gain is a comparison; the drawing has two operands — the serial reference (all shards laid end to end) and the realized build (the same pieces stacked, ending at the slowest shard). The emptiness between the stack's edge and the reference's end **is** the message. Never fill it.

```
  ╭─ S H A R D W I S E ──────────────────────────────────
  │ ██████████████████████▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▒▒▒▒▒▒▒▒▒▒▒▒▒▒   serial: would have been
  │ ██████████████████████                                   slowest shard = realized edge
  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
  │ ▒▒▒▒▒▒▒▒▒▒▒▒▒▒
  ├─ test ────────────────────────────────────────────────   ← plan section continues here
```

## The geometry

The crown divides into two regions: drawn glyphs and the empty space that conveys the message.

### What's drawn

| Element | Value | Why |
|---------|-------|-----|
| Serial width | 53 (`SERIAL_ROW`), 57 rendered | Sets `FRAME_WIDTH` (55) — border rows end flush with the serial row |
| Shard pieces | █ 22 · ▓ 17 · ▒ 14 | Unequal on sight — Greedy-LPT packs by weight, never round-robin |
| Serial row | the three pieces concatenated = 53 | Physically honest: serial time = sum of shards |

The shard pieces are the three textured segments (█, ▓, ▒) at unequal widths. Together they form the serial row. The serial row spans exactly 53 characters and sets `FRAME_WIDTH` — top border, section divider, and bottom border all end flush with it, so crown and frame align precisely.

### What stays empty

| Element | Value | Why |
|---------|-------|-----|
| Realized edge | column 22 | Set by the *longest* piece, i.e. the slowest node |
| Delta | columns 22–53 across the three shard rows | The protagonist; stays empty |
| Seam | █ ends at column 22 where ▓ begins | Falls exactly on the realized edge — segment 1 *is* the slowest shard |

The realized edge is the right boundary of the longest shard piece (█ at column 22). It marks the wall time of the slowest CI node. Delta is the empty space from column 22 to column 53 across all three shard rows — that emptiness IS the time saved (absence of the serial tail). The seam is where █ meets ▓; it coincides with the realized edge because the slowest shard sits first in the serial row and at the top of the stack, so the alignment is intentional.

## Don't

- Don't place anything in the delta region — no label, no dots, no hatching. A glyph there turns absence into an object; the message becomes "something occupies this space" instead of "the serial tail was avoided."
- Don't use `████░░░░` rows — a solid bar with a lighter tail reads as a loading bar, suggesting unfinished work. `░` (U+2591) is banned from the crown entirely.
- Don't equalize shard lengths — pairwise distinct lengths (minimum difference ≥ 3) that sum to the serial row's width prevent the shape from claiming round-robin, which the planner does not do.
- Don't move the longest piece off the top — the realized edge must coincide with the first texture seam; the slowest shard stays first in the stack and first in the serial row.
- Don't close the frame on the right — a right border would wall in the delta; the open edge is what lets the emptiness read as saved time.
- Don't vary border widths — `PlanRendererTest` asserts top border, divider, and bottom border end flush with the serial row. Content lines are open on the right and may be any length. Count characters, not bytes.
- Don't use glyphs outside box-drawing, `█▓▒`, and plain ASCII — CI logs have no font guarantee and variable-width output would misalign the geometry.

## Verification

`PlanRendererTest > the crown folds the serial reference into stacked shards` pins the serial row, the three standalone shard rows, and the wordmark; the border-width test pins the flush-edge invariant. If a change breaks either, the change is wrong, not the test.
