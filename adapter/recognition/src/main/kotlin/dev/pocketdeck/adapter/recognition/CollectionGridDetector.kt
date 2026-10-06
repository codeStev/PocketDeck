package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.ScreenFrame
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Finds the card tiles of the game's "My Cards" screen, whatever the zoom level (3 or 5 cards per row, ...).
 *
 * The grid sits on a light blue-grey background, so the gaps between tiles show up as columns and rows that are
 * almost entirely background. Card artwork and info plates can have a similar colour, which makes single rows
 * unreliable; the structure is what is dependable: tile rows repeat at a fixed pitch (tile height plus gap), the
 * header above and the navigation bar below are large background-coloured zones, and the tile height follows from
 * the tile width. Only completely visible tiles are reported; a scrolled-off tile shows up fully in another frame.
 */
class CollectionGridDetector {
    fun detect(frame: ScreenFrame): List<TileBounds> {
        val background = BooleanArray(frame.pixels.size) { isGridBackground(frame.pixels[it]) }
        val rowBackground = rowBackgroundFractions(frame, background)
        val columns = tileColumns(frame, background, rowBackground)
        if (columns.size < MIN_COLUMNS) return emptyList()
        val tileWidth = columns.map { it.length }.sorted()[columns.size / 2]
        val gap = gapBetween(columns)
        return tileTops(frame, rowBackground, tileWidth, gap).flatMap { top ->
            val height = (tileWidth / CARD_ASPECT_RATIO).roundToInt()
            columns.map { column -> TileBounds(column.start, top, column.length, height) }
        }
    }

    private fun rowBackgroundFractions(
        frame: ScreenFrame,
        background: BooleanArray,
    ) = DoubleArray(frame.height) { y ->
        var count = 0
        for (x in 0 until frame.width) if (background[y * frame.width + x]) count++
        count.toDouble() / frame.width
    }

    private fun tileColumns(
        frame: ScreenFrame,
        background: BooleanArray,
        rowBackground: DoubleArray,
    ): List<Run> {
        // Judge columns only on rows that hold something: when most of the screen is empty (end of the list), the
        // tile columns would otherwise look like background too.
        val rows = (0 until frame.height).filter { rowBackground[it] < CONTENT_ROW_BACKGROUND_FRACTION }
        if (rows.isEmpty()) return emptyList()
        val isGap =
            BooleanArray(frame.width) { x ->
                rows.count { y -> background[y * frame.width + x] } >= GAP_FRACTION * rows.size
            }
        val min = (frame.width * MIN_TILE_WIDTH_FRACTION).toInt()
        val max = (frame.width * MAX_TILE_WIDTH_FRACTION).toInt()
        val widths = runsOf(frame.width) { !isGap[it] }.filter { it.length in min..max }
        if (widths.isEmpty()) return emptyList()
        val median = widths.map { it.length }.sorted()[widths.size / 2]
        return widths.filter { abs(it.length - median) <= median * WIDTH_TOLERANCE }
    }

    private fun gapBetween(columns: List<Run>): Int {
        val gaps = columns.zipWithNext { left, right -> right.start - (left.start + left.length) }
        return gaps.sorted()[gaps.size / 2]
    }

    private fun tileTops(
        frame: ScreenFrame,
        rowBackground: DoubleArray,
        tileWidth: Int,
        gap: Int,
    ): List<Int> {
        val tileHeight = (tileWidth / CARD_ASPECT_RATIO).roundToInt()
        val bands = runsOf(frame.height) { rowBackground[it] >= GAP_FRACTION }
        val zones = bands.filter { it.length > gap * ZONE_FACTOR }
        val contentTop = zones.filter { it.start < frame.height / 2 }.maxOfOrNull { it.start + it.length } ?: 0
        val contentBottom = zones.filter { it.start >= frame.height / 2 }.minOfOrNull { it.start } ?: frame.height
        val separators = bands.filter { it.length in (gap * MIN_BAND_FACTOR).toInt()..(gap * ZONE_FACTOR).toInt() }
        val pitch = tileHeight + gap
        val anchor =
            latticeAnchor(separators.map { it.start + it.length }, pitch, contentTop, contentBottom) ?: contentTop
        var top = anchor
        while (top - pitch >= contentTop) top -= pitch
        val tops = mutableListOf<Int>()
        while (top + tileHeight <= contentBottom) {
            if (top >= contentTop && isFilled(rowBackground, top, tileHeight)) tops += top
            top += pitch
        }
        return tops
    }

    /** The tile top that most separator bands agree on, given tiles repeat every [pitch] pixels. */
    private fun latticeAnchor(
        candidates: List<Int>,
        pitch: Int,
        contentTop: Int,
        contentBottom: Int,
    ): Int? =
        candidates
            .filter { it in contentTop until contentBottom }
            .maxByOrNull { anchor -> candidates.count { phaseDistance(it - anchor, pitch) <= PHASE_TOLERANCE } }

    private fun phaseDistance(
        offset: Int,
        pitch: Int,
    ): Int {
        val remainder = ((offset % pitch) + pitch) % pitch
        return minOf(remainder, pitch - remainder)
    }

    /** A real tile is not mostly background; the header zone, for instance, is. */
    private fun isFilled(
        rowBackground: DoubleArray,
        top: Int,
        height: Int,
    ) = (top until top + height).map { rowBackground[it] }.average() < MAX_TILE_BACKGROUND

    private fun runsOf(
        size: Int,
        inRun: (Int) -> Boolean,
    ): List<Run> {
        val runs = mutableListOf<Run>()
        var start = -1
        for (i in 0..size) {
            val inside = i < size && inRun(i)
            if (inside && start < 0) start = i
            if (!inside && start >= 0) {
                runs += Run(start, i - start)
                start = -1
            }
        }
        return runs
    }

    private data class Run(
        val start: Int,
        val length: Int,
    )

    private companion object {
        const val CARD_ASPECT_RATIO = 0.7194
        const val MIN_COLUMNS = 2

        // Tile interiors are at most ~20% background, true gaps ~99%. The threshold sits low so that overlays such as
        // the game's floating scroll button, which covers part of a gap, do not hide it.
        const val GAP_FRACTION = 0.6
        const val CONTENT_ROW_BACKGROUND_FRACTION = 0.9
        const val MIN_TILE_WIDTH_FRACTION = 0.10
        const val MAX_TILE_WIDTH_FRACTION = 0.50
        const val WIDTH_TOLERANCE = 0.04
        const val ZONE_FACTOR = 2.5
        const val MIN_BAND_FACTOR = 0.5
        const val PHASE_TOLERANCE = 4
        const val MAX_TILE_BACKGROUND = 0.6

        // The grid background is a light, slightly bluish grey (about 231/240/247, darker in shadows); white
        // card bodies lack the blue tint and must not count as background.
        const val MIN_LUMA = 190
        const val MAX_LUMA = 248
        const val MIN_BLUE_TINT = 8
        const val MAX_BLUE_TINT = 32

        fun isGridBackground(argb: Int): Boolean {
            val red = (argb shr 16) and 0xFF
            val green = (argb shr 8) and 0xFF
            val blue = argb and 0xFF
            val luma = (red + green + blue) / 3
            return luma in MIN_LUMA..MAX_LUMA && blue - red in MIN_BLUE_TINT..MAX_BLUE_TINT && blue >= green
        }
    }
}
