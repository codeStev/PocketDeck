package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.GridGeometry
import dev.pocketdeck.application.scan.GridLocator
import dev.pocketdeck.application.scan.ScreenFrame
import kotlin.math.roundToInt

/** Tells the auto-scroller where the card grid is by looking at the tiles the detector finds. */
class DetectorGridLocator(
    private val detector: CollectionGridDetector,
) : GridLocator {
    override fun locate(frame: ScreenFrame): GridGeometry? {
        val tiles = detector.detect(frame)
        if (tiles.isEmpty()) return null
        val rowTops = tiles.map { it.y }.distinct().sorted()
        val tileHeight = tiles.first().height
        return GridGeometry(
            firstRowTop = rowTops.first(),
            lastRowBottom = tiles.maxOf { it.y + it.height },
            rowPitch = rowPitch(rowTops, tileHeight, tiles.first().width),
            visibleRows = rowTops.size,
            centerX = frame.width / 2,
        )
    }

    /** The median distance between row tops; with a single row, the tile height plus the usual gap. */
    private fun rowPitch(
        rowTops: List<Int>,
        tileHeight: Int,
        tileWidth: Int,
    ): Int {
        val distances = rowTops.zipWithNext { upper, lower -> lower - upper }.sorted()
        return if (distances.isNotEmpty()) {
            distances[distances.size / 2]
        } else {
            tileHeight +
                (tileWidth * GAP_PER_TILE_WIDTH).roundToInt()
        }
    }

    private companion object {
        /** On a real screen the gap between rows is about 4.7% of the tile width. */
        const val GAP_PER_TILE_WIDTH = 0.047
    }
}
