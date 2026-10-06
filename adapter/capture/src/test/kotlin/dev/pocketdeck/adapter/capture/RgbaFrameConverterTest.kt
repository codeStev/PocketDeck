package dev.pocketdeck.adapter.capture

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class RgbaFrameConverterTest {
    /** A buffer in the layout of Android's RGBA_8888 images: R, G, B, A bytes per pixel, rows padded to [rowStride]. */
    private fun rgba(
        width: Int,
        height: Int,
        rowStride: Int,
        pixel: (x: Int, y: Int) -> Int,
    ): ByteBuffer {
        val buffer = ByteBuffer.allocate(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = pixel(x, y)
                val offset = y * rowStride + x * 4
                buffer.put(offset, (argb shr 16).toByte())
                buffer.put(offset + 1, (argb shr 8).toByte())
                buffer.put(offset + 2, argb.toByte())
                buffer.put(offset + 3, 0xFF.toByte())
            }
        }
        return buffer
    }

    @Test
    fun `converts rgba bytes to opaque argb pixels`() {
        val buffer = rgba(2, 1, rowStride = 8) { x, _ -> if (x == 0) 0xFF4C5C74.toInt() else 0xFF102030.toInt() }

        val frame = RgbaFrameConverter.toScreenFrame(buffer, width = 2, height = 1, rowStride = 8)

        frame.pixels.toList() shouldBe listOf(0xFF4C5C74.toInt(), 0xFF102030.toInt())
    }

    @Test
    fun `skips the padding at the end of each row`() {
        val buffer = rgba(3, 2, rowStride = 16) { x, y -> 0xFF000000.toInt() or (y * 100 + x) }

        val frame = RgbaFrameConverter.toScreenFrame(buffer, width = 3, height = 2, rowStride = 16)

        frame.width shouldBe 3
        frame.height shouldBe 2
        frame.pixels.map { it and 0xFFFFFF } shouldBe listOf(0, 1, 2, 100, 101, 102)
    }

    @Test
    fun `pixels are always opaque even if the buffer carries no alpha`() {
        val buffer = ByteBuffer.allocate(4)

        RgbaFrameConverter.toScreenFrame(buffer, width = 1, height = 1, rowStride = 4).pixels[0] shouldBe
            0xFF000000.toInt()
    }

    @Test
    fun `rejects a buffer that is too small for the stated size`() {
        shouldThrow<IllegalArgumentException> {
            RgbaFrameConverter.toScreenFrame(ByteBuffer.allocate(10), width = 2, height = 2, rowStride = 8)
        }
    }

    @Test
    fun `leaves the position of the source buffer alone`() {
        val buffer = rgba(2, 1, rowStride = 8) { _, _ -> 0xFFFFFFFF.toInt() }

        RgbaFrameConverter.toScreenFrame(buffer, width = 2, height = 1, rowStride = 8)

        buffer.position() shouldBe 0
    }
}
