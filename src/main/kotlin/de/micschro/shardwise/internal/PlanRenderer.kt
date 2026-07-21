// SPDX-License-Identifier: Apache-2.0

package de.micschro.shardwise.internal

import de.micschro.shardwise.PlanDetail

/**
 * Renders a [ShardPlan] as the console dashboard shown at build start.
 *
 * The frame is rounded on the left and open on the right — content lines carry no
 * right border, so nothing ever needs padding or truncation to stay aligned.
 *
 * Pure: takes resolved values, returns lines. Colour is opt-in via [ansi] so that
 * CI logs (no TTY) stay free of escape codes.
 */
@Suppress("TooManyFunctions") // one small named function per frame element reads better than one big one
internal class PlanRenderer(private val ansi: Boolean) {

    fun render(
        taskName: String,
        nodeIndex: Int,
        plan: ShardPlan,
        modules: List<String>,
        detail: PlanDetail,
    ): List<String> {
        val total = plan.nodeTotal
        if (detail == PlanDetail.OFF || total <= 1) return emptyList()

        val here = plan.assignments[nodeIndex].orEmpty()
        val unassigned = modules.count { module ->
            plan.assignments.values.none { module in it }
        }
        // Only modules that actually run elsewhere — the skip hint promises exactly that.
        // Unassigned modules run nowhere and are reported by the WARNING row instead.
        val skipped = modules.size - here.size - unassigned

        val lines = mutableListOf<String>()
        lines += crown()
        lines += titledBorder(LEFT_TEE, taskName)
        lines += nodeRow(nodeIndex, total)
        lines += runningRow(here.size, modules.size)
        if (unassigned > 0) lines += warningRow(unassigned)
        lines += skippedRow(skipped, taskName)
        if (detail == PlanDetail.FULL) {
            lines += contentLine("")
            lines += contentLine("Modules running here")
            here.forEach { lines += contentLine("$MODULE_INDENT$it") }
        }
        lines += bottomBorder()
        return lines
    }

    /**
     * The crown that opens the frame: the serial reference — the three shard pieces laid
     * end to end across the full width — folds into the same pieces stacked below it.
     * Everything right of the longest piece is the time the fold never spent; that
     * region must stay empty — any glyph placed there erases the message.
     * The full design argument lives in docs/crown.md.
     */
    private fun crown(): List<String> = listOf(
        titledBorder(TOP_LEFT, WORDMARK),
        contentLine(SERIAL_ROW, dim(SERIAL_ROW)),
        contentLine(SHARD_ONE),
        contentLine(SHARD_TWO),
        contentLine(SHARD_THREE),
    )

    private fun nodeRow(nodeIndex: Int, total: Int): String {
        val label = "Node".padEnd(LABEL_COLUMN)
        return contentLine("$label$nodeIndex of $total", "$label${bold("$nodeIndex of $total")}")
    }

    private fun runningRow(here: Int, total: Int): String {
        val label = "Running here".padEnd(LABEL_COLUMN)
        return contentLine("$label$here of $total modules")
    }

    /**
     * Only rendered when the plan violates the coverage invariant (a module assigned to
     * no node). Success stays quiet; this line exists to make a planner bug impossible
     * to miss in the CI log.
     */
    private fun warningRow(unassigned: Int): String {
        val label = "WARNING".padEnd(LABEL_COLUMN)
        val text = if (unassigned == 1) {
            "1 module assigned to no node — its tests will not run"
        } else {
            "$unassigned modules assigned to no node — their tests will not run"
        }
        return contentLine("$label$text", "$label${paint(text, RED)}")
    }

    private fun skippedRow(skipped: Int, taskName: String): String {
        val label = "Skipped here".padEnd(LABEL_COLUMN)
        val hint = "(run on other nodes, shown as ':<module>:$taskName SKIPPED')"
        return contentLine("$label$skipped $hint", "$label$skipped ${dim(hint)}")
    }

    /** A border row with a title threaded in: `╭─ S H A R D W I S E ───…` or `├─ test ───…`. */
    private fun titledBorder(corner: String, title: String): String {
        val fill = HORIZONTAL.repeat((FRAME_WIDTH - "$corner$HORIZONTAL $title ".length).coerceAtLeast(1))
        return "$INDENT${dim("$corner$HORIZONTAL ")}${bold(title)}${dim(" $fill")}"
    }

    private fun bottomBorder() =
        "$INDENT${dim(BOTTOM_LEFT + HORIZONTAL.repeat(FRAME_WIDTH - 1))}"

    private fun contentLine(plain: String, body: String = plain): String {
        val v = dim(VERTICAL)
        return if (plain.isEmpty()) "$INDENT$v" else "$INDENT$v $body"
    }

    /** Wraps [s] in the given escape codes, or returns it untouched when colour is off. */
    private fun paint(s: String, vararg codes: String) =
        if (ansi) codes.joinToString("") + s + RESET else s

    private fun dim(s: String) = paint(s, DIM)
    private fun bold(s: String) = paint(s, BOLD)

    private companion object {
        const val INDENT = "  "
        const val MODULE_INDENT = "  "
        const val LABEL_COLUMN = 14

        const val WORDMARK = "S H A R D W I S E"

        // Deliberately unequal (Greedy-LPT packs by weight, never round-robin):
        // the serial row is the shards laid end to end. The longest piece leads —
        // its edge is the realized build length.
        val SHARD_ONE = "█".repeat(22)
        val SHARD_TWO = "▓".repeat(17)
        val SHARD_THREE = "▒".repeat(14)
        val SERIAL_ROW = SHARD_ONE + SHARD_TWO + SHARD_THREE

        /** Border rows match the serial row plus its `│ ` prefix, so frame and crown align. */
        val FRAME_WIDTH = SERIAL_ROW.length + 2

        const val TOP_LEFT = "╭"
        const val BOTTOM_LEFT = "╰"
        const val HORIZONTAL = "─"
        const val VERTICAL = "│"
        const val LEFT_TEE = "├"

        const val RESET = "\u001B[0m"
        const val BOLD = "\u001B[1m"
        const val DIM = "\u001B[2m"
        const val RED = "\u001B[31m"
    }
}
