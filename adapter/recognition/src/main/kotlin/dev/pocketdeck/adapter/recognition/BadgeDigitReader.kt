package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.ScreenFrame
import kotlin.math.abs
import kotlin.math.min

/**
 * Reads the copy count from the dark slate badge in the bottom-left corner of a tile.
 *
 * The game fades the badges out while the list scrolls and back in once it stops, so a tile without the solid tab
 * has no readable count yet and yields null (never a guess). Geometry and colours are measured from real
 * screenshots and mirrored in `tools/digit_templates/digittemplates/glyphs.py`.
 */
class BadgeDigitReader(
    private val templates: DigitTemplates,
) : CopyCountReader {
    override fun read(
        frame: ScreenFrame,
        tile: TileBounds,
    ): CopyCountReading? {
        val ink = digitInk(frame, tile) ?: return null
        val glyphs = splitGlyphs(ink)
        if (glyphs.isEmpty() || glyphs.size > MAX_DIGITS) return null
        val digits = glyphs.map(::classify)
        val copies = digits.fold(0) { total, digit -> total * 10 + digit.digit }
        if (copies < 1) return null
        return CopyCountReading(copies, digits.minOf { it.confidence })
    }

    internal class Ink(
        val width: Int,
        val height: Int,
        val levels: IntArray,
    )

    private class DigitGuess(
        val digit: Int,
        val confidence: Double,
    )

    internal fun digitInk(
        frame: ScreenFrame,
        tile: TileBounds,
    ): Ink? {
        val tab = tabColor(frame, tile) ?: return null
        val windowTop = tile.y + (WINDOW_TOP * tile.height).toInt()
        val windowBottom = tile.y + tile.height
        val windowRight = tile.x + (WINDOW_RIGHT * tile.width).toInt()

        fun matches(
            x: Int,
            y: Int,
        ) = isTabColor(frame.pixels[y * frame.width + x], tab)

        // The tab is the longest run of rows in which a large share of the tile width has the tab colour; single
        // look-alike pixels in the artwork above it must not move its edges.
        val rowIsTab =
            (windowTop until windowBottom).map { y ->
                (tile.x until windowRight).count { x -> matches(x, y) } >= MIN_TAB_ROW_FRACTION * tile.width
            }
        val run = longestRun(rowIsTab) ?: return null
        if (run.length < MIN_TAB_HEIGHT_FRACTION * tile.height) return null
        val tabTop = windowTop + run.start
        val tabBottom = tabTop + run.length - 1
        val tabLeft =
            (tile.x until windowRight).first { x -> (tabTop..tabBottom).count { y -> matches(x, y) } >= run.length / 2 }

        val inkLeft = tabLeft + TAB_MARGIN_LEFT
        val inkRight = tile.x + (DIGIT_AREA_RIGHT * tile.width).toInt()
        val inkTop = tabTop + TAB_MARGIN_TOP
        val inkBottom = tabBottom - TAB_MARGIN_BOTTOM
        if (inkRight <= inkLeft || inkBottom <= inkTop) return null
        val width = inkRight - inkLeft
        val height = inkBottom - inkTop
        val levels =
            IntArray(width * height) {
                minChannel(
                    frame.pixels[
                        (inkTop + it / width) * frame.width + inkLeft +
                            it % width,
                    ],
                )
            }
        return Ink(width, height, levels)
    }

    private class Run(
        val start: Int,
        val length: Int,
    )

    private fun longestRun(flags: List<Boolean>): Run? {
        var best: Run? = null
        var start = -1
        for (i in 0..flags.size) {
            val inside = i < flags.size && flags[i]
            if (inside && start < 0) start = i
            if (!inside && start >= 0) {
                if (best == null || i - start > best.length) best = Run(start, i - start)
                start = -1
            }
        }
        return best
    }

    /** One cropped glyph per run of columns that contain ink, left to right. */
    internal fun splitGlyphs(ink: Ink): List<Ink> {
        val hasInk =
            BooleanArray(ink.width) { x ->
                (0 until ink.height).any { y ->
                    ink.levels[y * ink.width + x] >
                        GLYPH_THRESHOLD
                }
            }
        val glyphs = mutableListOf<Ink>()
        var start = -1
        for (x in 0..ink.width) {
            val inside = x < ink.width && hasInk[x]
            if (inside && start < 0) start = x
            if (!inside && start >= 0) {
                crop(ink, start, x)?.let(glyphs::add)
                start = -1
            }
        }
        return glyphs
    }

    private fun crop(
        ink: Ink,
        from: Int,
        until: Int,
    ): Ink? {
        val rows =
            (0 until ink.height).filter { y ->
                (from until until).any { x ->
                    ink.levels[y * ink.width + x] >
                        GLYPH_THRESHOLD
                }
            }
        if (rows.isEmpty() || until - from < MIN_GLYPH_SIZE || rows.size < MIN_GLYPH_SIZE) return null
        val width = until - from
        val first = rows.first()
        val height = rows.last() - first + 1
        return Ink(
            width,
            height,
            IntArray(width * height) {
                ink.levels[
                    (first + it / width) * ink.width + from +
                        it % width,
                ]
            },
        )
    }

    /** Every digit with the distance of its best template to [glyph], closest first (for diagnostics and tests). */
    internal fun rank(glyph: Ink): List<Pair<Int, Float>> {
        val gray = ByteArray(glyph.levels.size) { glyph.levels[it].toByte() }
        val candidate = GlyphImage.normalize(gray, glyph.width, glyph.height)
        val bestPerDigit = HashMap<Int, Float>()
        for (template in templates.templates) {
            val distance = distance(candidate, template.normalized)
            bestPerDigit[template.digit] = minOf(distance, bestPerDigit[template.digit] ?: Float.MAX_VALUE)
        }
        return bestPerDigit.entries.sortedBy { it.value }.map { it.key to it.value }
    }

    private fun classify(glyph: Ink): DigitGuess {
        val gray = ByteArray(glyph.levels.size) { glyph.levels[it].toByte() }
        val candidate = GlyphImage.normalize(gray, glyph.width, glyph.height)
        val bestPerDigit = HashMap<Int, Pair<Float, Boolean>>()
        for (template in templates.templates) {
            val distance = distance(candidate, template.normalized)
            val known = bestPerDigit[template.digit]
            if (known == null || distance < known.first) bestPerDigit[template.digit] = distance to template.synthetic
        }
        val ranked = bestPerDigit.entries.sortedBy { it.value.first }
        val best = ranked[0]
        val margin = ranked[1].value.first - best.value.first
        val certainty = (margin / CERTAIN_MARGIN).toDouble().coerceIn(0.0, 1.0)
        return DigitGuess(best.key, if (best.value.second) certainty * SYNTHETIC_DISCOUNT else certainty)
    }

    private fun distance(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        var sum = 0f
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum / a.size
    }

    /** The slate colour of this tile's tab, sampled where the tab always is; null when that spot is not slate. */
    private fun tabColor(
        frame: ScreenFrame,
        tile: TileBounds,
    ): Int? {
        val x = tile.x + (ANCHOR_X * tile.width).toInt()
        val y = tile.y + (ANCHOR_Y * tile.height).toInt()
        val argb = frame.pixels[y * frame.width + x]
        val red = (argb shr RED_SHIFT) and CHANNEL
        val green = (argb shr GREEN_SHIFT) and CHANNEL
        val blue = argb and CHANNEL
        val isSlate = red in SLATE_RED && blue - red in SLATE_BLUE_TINT && green - red in SLATE_GREEN_TINT
        return argb.takeIf { isSlate }
    }

    /** The tab is a flat colour, lightened a little by the fade towards the navigation bar; match it closely. */
    private fun isTabColor(
        argb: Int,
        tab: Int,
    ): Boolean =
        abs(((argb shr RED_SHIFT) and CHANNEL) - ((tab shr RED_SHIFT) and CHANNEL)) <= TAB_TOLERANCE &&
            abs(((argb shr GREEN_SHIFT) and CHANNEL) - ((tab shr GREEN_SHIFT) and CHANNEL)) <= TAB_TOLERANCE &&
            abs((argb and CHANNEL) - (tab and CHANNEL)) <= TAB_TOLERANCE

    private fun minChannel(argb: Int): Int =
        minOf(
            (argb shr RED_SHIFT) and CHANNEL,
            (argb shr GREEN_SHIFT) and CHANNEL,
            argb and CHANNEL,
        )

    private companion object {
        const val ANCHOR_X = 0.05
        const val ANCHOR_Y = 0.965
        const val TAB_TOLERANCE = 8
        val SLATE_RED = 60..110
        val SLATE_BLUE_TINT = 28..56
        val SLATE_GREEN_TINT = 10..26
        const val WINDOW_TOP = 0.84
        const val WINDOW_RIGHT = 0.55
        const val DIGIT_AREA_RIGHT = 0.40
        const val MIN_TAB_ROW_FRACTION = 0.25
        const val MIN_TAB_HEIGHT_FRACTION = 0.06
        const val TAB_MARGIN_TOP = 6
        const val TAB_MARGIN_BOTTOM = 2
        const val TAB_MARGIN_LEFT = 3
        const val GLYPH_THRESHOLD = 140
        const val MIN_GLYPH_SIZE = 4
        const val MAX_DIGITS = 3
        const val CERTAIN_MARGIN = 0.15f
        const val SYNTHETIC_DISCOUNT = 0.85
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val CHANNEL = 0xFF
    }
}
