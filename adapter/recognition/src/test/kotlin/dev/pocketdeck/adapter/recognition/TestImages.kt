package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.application.scan.ScreenFrame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal object TestImages {
    /** The real, generated fingerprint index that the app ships. */
    val indexFile = File("../../app/src/main/assets/card_index.bin")

    fun loadIndex(): CardIndex {
        check(indexFile.exists()) { "Run tools/card_index/build_index.py first: ${indexFile.absolutePath} is missing" }
        return indexFile.inputStream().use(CardIndex::read)
    }

    fun frame(resource: String): ScreenFrame {
        val image =
            requireNotNull(TestImages::class.java.getResourceAsStream("/$resource")) { "$resource missing" }
                .use(ImageIO::read)
        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        return ScreenFrame(image.width, image.height, pixels)
    }

    /** [frame] as another phone would capture it: the same screen at [factor] times the resolution (smoothly resampled). */
    fun scaled(
        frame: ScreenFrame,
        factor: Double,
    ): ScreenFrame {
        val source = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, frame.width, frame.height, frame.pixels, 0, frame.width)
        val width = (frame.width * factor).roundToInt()
        val height = (frame.height * factor).roundToInt()
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        target.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(source, 0, 0, width, height, null)
            dispose()
        }
        val pixels = IntArray(width * height)
        target.getRGB(0, 0, width, height, pixels, 0, width)
        return ScreenFrame(width, height, pixels)
    }

    /** A copy of [frame] where everything at or below row [fromY] is painted with [color]. */
    fun blankedFrom(
        frame: ScreenFrame,
        fromY: Int,
        color: Int,
    ): ScreenFrame {
        val pixels = frame.pixels.copyOf()
        pixels.fill(color, fromY * frame.width, pixels.size)
        return ScreenFrame(frame.width, frame.height, pixels)
    }

    fun solid(
        width: Int,
        height: Int,
        color: Int,
    ) = ScreenFrame(width, height, IntArray(width * height) { color })

    /** The light blue-grey the game draws behind the card grid. */
    const val GRID_BACKGROUND = 0xFFE7F0F7.toInt()

    /** Writes the documented `card_index.bin` layout (see tools/card_index/cardindex/index_format.py). */
    fun indexBytes(
        entries: List<Pair<String, ByteArray>>,
        parameters: FingerprintParameters = FingerprintParameters(3, 2, 0.0625f, 0.125f, 0.9375f, 0.5f),
        magic: String = "PDIX",
        version: Int = 1,
    ): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).apply {
            writeBytes(magic)
            writeShort(version)
            writeShort(parameters.width)
            writeShort(parameters.height)
            listOf(parameters.x0, parameters.y0, parameters.x1, parameters.y1).forEach(::writeFloat)
            writeInt(entries.size)
            entries.forEach { (id, thumbnail) ->
                writeShort(id.length)
                writeBytes(id)
                write(thumbnail)
            }
        }
        return bytes.toByteArray()
    }
}
