package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.ScreenFrame
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Size of the thumbnail and the part of a card it is taken from; read from the index header. */
data class FingerprintParameters(
    val width: Int,
    val height: Int,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
)

/** Where a card tile sits in a captured frame, in pixels. */
data class TileBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * Reduces the illustration of a card to a small grayscale thumbnail. Mirrors `tools/card_index/cardindex/fingerprint.py`
 * step by step (integer luma, exact area averaging, round half to even), so thumbnails of on-screen tiles are
 * comparable with the ones in the index.
 */
class ArtFingerprinter(
    private val parameters: FingerprintParameters,
) {
    private val resamplers = HashMap<Pair<Int, Int>, AreaResampler>()

    fun fingerprint(
        frame: ScreenFrame,
        tile: TileBounds,
    ): ByteArray {
        val left = tile.x + (parameters.x0.toDouble() * tile.width).toInt()
        val top = tile.y + (parameters.y0.toDouble() * tile.height).toInt()
        val right = tile.x + (parameters.x1.toDouble() * tile.width).toInt()
        val bottom = tile.y + (parameters.y1.toDouble() * tile.height).toInt()
        val region = RegionView(frame, left, top, right - left, bottom - top)

        val horizontal = resampler(region.width, parameters.width)
        val vertical = resampler(region.height, parameters.height)
        // Resample columns first (region rows x output width), then rows.
        val rowsResampled =
            Array(region.height) { y -> horizontal.apply(DoubleArray(region.width) { x -> region.gray(x, y) }) }
        return ByteArray(parameters.width * parameters.height).also { out ->
            for (x in 0 until parameters.width) {
                val column = vertical.apply(DoubleArray(region.height) { y -> rowsResampled[y][x] })
                for (y in 0 until parameters.height) {
                    out[y * parameters.width + x] =
                        Math
                            .rint(column[y])
                            .toInt()
                            .coerceIn(0, MAX_LEVEL)
                            .toByte()
                }
            }
        }
    }

    private fun resampler(
        source: Int,
        target: Int,
    ) = resamplers.getOrPut(source to target) { AreaResampler(source, target) }

    private class RegionView(
        private val frame: ScreenFrame,
        private val left: Int,
        private val top: Int,
        val width: Int,
        val height: Int,
    ) {
        init {
            require(width > 0 && height > 0) { "Empty fingerprint region $width x $height" }
            require(left >= 0 && top >= 0 && left + width <= frame.width && top + height <= frame.height) {
                "Fingerprint region lies outside the frame"
            }
        }

        fun gray(
            x: Int,
            y: Int,
        ): Double {
            val argb = frame.pixels[(top + y) * frame.width + left + x]
            val red = (argb shr RED_SHIFT) and CHANNEL_MASK
            val green = (argb shr GREEN_SHIFT) and CHANNEL_MASK
            val blue = argb and CHANNEL_MASK
            return ((LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue + LUMA_ROUNDING) / LUMA_DIVISOR).toDouble()
        }
    }

    private companion object {
        const val MAX_LEVEL = 255
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val CHANNEL_MASK = 0xFF
        const val LUMA_RED = 299
        const val LUMA_GREEN = 587
        const val LUMA_BLUE = 114
        const val LUMA_ROUNDING = 500
        const val LUMA_DIVISOR = 1000
    }
}

/** Averages the source pixels covered by every target pixel, weighting partially covered pixels exactly. */
internal class AreaResampler(
    sourceSize: Int,
    private val targetSize: Int,
) {
    private val firstSource = IntArray(targetSize)
    private val weights: Array<DoubleArray>

    init {
        val scale = sourceSize.toDouble() / targetSize
        weights =
            Array(targetSize) { target ->
                val start = target * scale
                val end = (target + 1) * scale
                val first = start.toInt()
                val last = min(ceil(end).toInt(), sourceSize)
                firstSource[target] = first
                DoubleArray(last - first) { offset ->
                    val source = first + offset
                    max(min(end, source + 1.0) - max(start, source.toDouble()), 0.0) / scale
                }
            }
    }

    fun apply(values: DoubleArray): DoubleArray =
        DoubleArray(targetSize) { target ->
            var sum = 0.0
            val row = weights[target]
            for (offset in row.indices) sum += row[offset] * values[firstSource[target] + offset]
            sum
        }
}
