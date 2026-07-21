// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise.internal

import de.micschro.shardwise.PlanDetail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanRendererTest {

    private val plan = ShardPlan(
        nodeTotal = 2,
        assignments = mapOf(
            1 to listOf("mod-a", "mod-b"),
            2 to listOf("mod-c"),
        ),
    )
    private val modules = listOf("mod-a", "mod-b", "mod-c")

    private fun render(
        detail: PlanDetail,
        nodeIndex: Int = 1,
        plan: ShardPlan = this.plan,
        modules: List<String> = this.modules,
        taskName: String = "test",
        ansi: Boolean = false,
    ): List<String> = PlanRenderer(ansi = ansi).render(
        taskName = taskName,
        nodeIndex = nodeIndex,
        plan = plan,
        modules = modules,
        detail = detail,
    )

    @Test
    fun `OFF renders nothing`() {
        assertEquals(emptyList<String>(), render(PlanDetail.OFF))
    }

    @Test
    fun `a single node renders nothing because there is nothing to shard`() {
        val single = ShardPlan(nodeTotal = 1, assignments = mapOf(1 to modules))
        assertEquals(emptyList<String>(), render(PlanDetail.FULL, plan = single))
    }

    @Test
    fun `SUMMARY reports the node position`() {
        val out = render(PlanDetail.SUMMARY).joinToString("\n")
        assertTrue(out.contains("Node"), "summary must label the node line: $out")
        assertTrue(out.contains("1 of 2"), "summary must place this node among all nodes: $out")
    }

    @Test
    fun `SUMMARY reports how many modules run here and how many are skipped`() {
        val out = render(PlanDetail.SUMMARY).joinToString("\n")
        assertTrue(out.contains("2 of 3 modules"), "summary must count this node's share: $out")
        assertTrue(out.contains("Skipped here"), "summary must report the skipped share: $out")
        assertTrue(
            out.contains(":<module>:test SKIPPED"),
            "the skip hint must show how skipped tasks appear in the log: $out",
        )
    }

    @Test
    fun `the skip hint names the sharded task`() {
        val out = render(PlanDetail.SUMMARY, taskName = "integrationTest").joinToString("\n")
        assertTrue(
            out.contains(":<module>:integrationTest SKIPPED"),
            "the skip hint must use the actual task name: $out",
        )
    }

    @Test
    fun `the section divider names the task`() {
        val out = render(PlanDetail.SUMMARY)
        assertTrue(out.any { it.contains("$LEFT_TEE$H test ") }, "the divider must carry the task name: $out")
    }

    @Test
    fun `SUMMARY omits the module list that FULL includes`() {
        val summary = render(PlanDetail.SUMMARY).joinToString("\n")
        val full = render(PlanDetail.FULL).joinToString("\n")

        assertTrue(full.contains("Modules running here"), "FULL must announce the module list")
        assertFalse(summary.contains("Modules running here"), "SUMMARY must not list modules")
    }

    @Test
    fun `FULL lists each module running on this node on its own line`() {
        val out = render(PlanDetail.FULL, nodeIndex = 1)
        assertTrue(out.any { it.trimEnd().endsWith("mod-a") }, "mod-a must get its own line: $out")
        assertTrue(out.any { it.trimEnd().endsWith("mod-b") }, "mod-b must get its own line: $out")
    }

    @Test
    fun `FULL does not list modules that run on other nodes`() {
        val out = render(PlanDetail.FULL, nodeIndex = 1).joinToString("\n")
        assertFalse(out.contains("mod-c"), "modules of other nodes must not appear: $out")
    }

    @Test
    fun `a fully assigned plan stays quiet about coverage`() {
        val out = render(PlanDetail.SUMMARY).joinToString("\n")
        assertFalse(out.contains("WARNING"), "a complete plan must not warn: $out")
        assertFalse(out.contains("coverage"), "the happy path needs no coverage verdict: $out")
    }

    @Test
    fun `unassigned modules raise a warning because their tests would run nowhere`() {
        val gap = ShardPlan(nodeTotal = 2, assignments = mapOf(1 to listOf("mod-a")))
        val out = render(PlanDetail.SUMMARY, plan = gap).joinToString("\n")
        assertTrue(out.contains("WARNING"), "an incomplete plan must warn loudly: $out")
        assertTrue(out.contains("2 modules assigned to no node"), "the warning must count the gap: $out")
        assertTrue(out.contains("will not run"), "the warning must name the consequence: $out")
    }

    @Test
    fun `unassigned modules are not counted as skipped because they run nowhere, not elsewhere`() {
        // node 1 holds mod-a; mod-b and mod-c are assigned to no node at all. The skip
        // hint claims skipped modules run on other nodes — the unassigned two must not
        // be folded into that count, they already have their own WARNING row.
        val gap = ShardPlan(nodeTotal = 2, assignments = mapOf(1 to listOf("mod-a")))
        val out = render(PlanDetail.SUMMARY, plan = gap).joinToString("\n")
        assertTrue(out.contains("Skipped here  0"), "skipped must count only run-elsewhere modules: $out")
    }

    @Test
    fun `a single unassigned module is warned about in the singular`() {
        val gap = ShardPlan(nodeTotal = 2, assignments = mapOf(1 to listOf("mod-a", "mod-b")))
        val out = render(PlanDetail.SUMMARY, plan = gap).joinToString("\n")
        assertTrue(out.contains("1 module assigned to no node"), "one gap must read singular: $out")
    }

    @Test
    fun `the frame is open on the right and rounded on the left`() {
        val out = render(PlanDetail.FULL)
        assertTrue(out.first().trimStart().startsWith(TOP_LEFT), "the frame must open with $TOP_LEFT: $out")
        assertTrue(out.last().trimStart().startsWith(BOTTOM_LEFT), "the frame must close with $BOTTOM_LEFT: $out")
        assertTrue(
            out.none { line -> CLOSED_RIGHT.any { line.trimEnd().endsWith(it) } },
            "no line may carry a right corner: $out",
        )
        assertTrue(
            // A bare "│" is the left border of an empty spacer line, not a right border.
            out.none { line -> line.trim().length > 1 && line.trimEnd().endsWith("│") },
            "no content line may be closed by a right border: $out",
        )
    }

    @Test
    fun `top border, divider and bottom border share the serial row's width`() {
        val out = render(PlanDetail.FULL)
        val serialWidth = visibleWidth(out.first { it.contains(SERIAL_ROW) })
        val borders = out.filter { line ->
            listOf(TOP_LEFT, LEFT_TEE, BOTTOM_LEFT).any { line.trimStart().startsWith(it) }
        }
        assertTrue(borders.size >= 3, "frame must have top, divider and bottom: $out")
        borders.forEach { assertEquals(serialWidth, visibleWidth(it), "border width must match the crown: $it") }
    }

    @Test
    fun `an overlong task name still renders a divider`() {
        val out = render(PlanDetail.SUMMARY, taskName = "x".repeat(80))
        assertTrue(out.any { it.contains("x".repeat(80)) }, "the task name must survive: $out")
    }

    @Test
    fun `the crown folds the serial reference into stacked shards`() {
        val out = render(PlanDetail.FULL)
        val joined = out.joinToString("\n")
        assertTrue(joined.contains("S H A R D W I S E"), "the crown must show the wordmark: $joined")
        assertTrue(joined.contains(SERIAL_ROW), "the serial reference must span the full width: $joined")
        assertTrue(
            out.any { it.contains(SHARD_ONE) && !it.contains("▓") },
            "the slowest shard must stand alone as the realized edge: $joined",
        )
        assertTrue(
            out.any { it.contains(SHARD_TWO) && !it.contains("█") },
            "the second shard must fold onto its own row: $joined",
        )
        assertTrue(
            out.any { it.contains(SHARD_THREE) && !it.contains("█") && !it.contains("▓") },
            "the third shard must fold onto its own row: $joined",
        )
    }

    @Test
    fun `SUMMARY is crowned too`() {
        val out = render(PlanDetail.SUMMARY).joinToString("\n")
        assertTrue(out.contains("S H A R D W I S E"), "SUMMARY must be crowned: $out")
    }

    @Test
    fun `ansi output stripped of escapes is identical to plain output`() {
        val plain = render(PlanDetail.FULL, ansi = false)
        val colored = render(PlanDetail.FULL, ansi = true)

        assertEquals(plain.size, colored.size, "ANSI must not change the number of lines")
        colored.zip(plain).forEach { (c, p) ->
            assertEquals(p, c.replace(ANSI, ""), "colour must only add escapes, never change text")
        }
    }

    @Test
    fun `plain output contains no ansi escape codes`() {
        val out = render(PlanDetail.FULL, ansi = false).joinToString("\n")
        assertFalse(out.contains(ESC), "non-tty output must stay free of ANSI escapes")
    }

    private fun visibleWidth(s: String) = s.replace(ANSI, "").length

    private companion object {
        // The crown's fold: three unequal shards laid end to end form the serial
        // reference; the same pieces stack flush-left below it.
        val SHARD_ONE = "█".repeat(22)
        val SHARD_TWO = "▓".repeat(17)
        val SHARD_THREE = "▒".repeat(14)
        val SERIAL_ROW = SHARD_ONE + SHARD_TWO + SHARD_THREE

        const val TOP_LEFT = "╭"
        const val BOTTOM_LEFT = "╰"
        const val LEFT_TEE = "├"
        const val H = "─"
        val CLOSED_RIGHT = listOf("┐", "┘", "┤")

        const val ESC = "\u001B"
        val ANSI = Regex("\u001B\\[[0-9;]*m")
    }
}
