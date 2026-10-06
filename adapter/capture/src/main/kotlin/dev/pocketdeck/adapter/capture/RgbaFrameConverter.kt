package dev.pocketdeck.adapter.capture

import dev.pocketdeck.application.scan.ScreenFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Turns the pixel buffer of an Android `RGBA_8888` image into the framework-neutral [ScreenFrame]. */
object RgbaFrameConverter {
    private const val BYTES_PER_PIXEL = 4
    private const val OPAQUE = 0xFF shl 24
    private const val CHANNEL_MASK = 0xFF

    /**
     * @param rowStride bytes between the starts of two rows; Android pads rows, so it can exceed `width * 4`.
     */
    fun toScreenFrame(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
    ): ScreenFrame {
        require(rowStride >= width * BYTES_PER_PIXEL) { "rowStride $rowStride is smaller than a row of $width pixels" }
        require(buffer.capacity() >= rowStride * (height - 1) + width * BYTES_PER_PIXEL) {
            "Buffer of ${buffer.capacity()} bytes is too small for ${width}x$height with stride $rowStride"
        }
        val ints = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        val intStride = rowStride / BYTES_PER_PIXEL
        val pixels = IntArray(width * height)
        val row = IntArray(width)
        for (y in 0 until height) {
            ints.position(y * intStride)
            ints.get(row, 0, width)
            for (x in 0 until width) pixels[y * width + x] = toArgb(row[x])
        }
        return ScreenFrame(width, height, pixels)
    }

    /** A little-endian int read from R, G, B, A bytes holds A in the top byte and R in the bottom one. */
    private fun toArgb(littleEndianRgba: Int): Int {
        val red = littleEndianRgba and CHANNEL_MASK
        val green = (littleEndianRgba shr 8) and CHANNEL_MASK
        val blue = (littleEndianRgba shr 16) and CHANNEL_MASK
        return OPAQUE or (red shl 16) or (green shl 8) or blue
    }
}
