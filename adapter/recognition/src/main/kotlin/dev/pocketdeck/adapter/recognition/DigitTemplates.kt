package dev.pocketdeck.adapter.recognition

/**
 * The glyphs of the digits 0-9 used to read a count badge (`digit_templates.txt`, written by `tools/digit_templates`).
 * Digits seen in the real game carry the game's own glyph; the others are approximated by a similar font.
 */
class DigitTemplates private constructor(
    val templates: List<Template>,
) {
    class Template(
        val digit: Int,
        val synthetic: Boolean,
        val width: Int,
        val height: Int,
        /** Grayscale of the glyph at its natural size, row major. */
        val raw: ByteArray,
    ) {
        val normalized: FloatArray = GlyphImage.normalize(raw, width, height)
    }

    companion object {
        private const val RESOURCE = "/digit_templates.txt"
        private const val FIELDS = 5
        private const val HEX_RADIX = 16

        fun fromResource(): DigitTemplates {
            val stream =
                requireNotNull(DigitTemplates::class.java.getResourceAsStream(RESOURCE)) { "$RESOURCE missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun parse(text: String): DigitTemplates =
            DigitTemplates(text.lines().filter { it.isNotBlank() }.map(::parseLine))

        private fun parseLine(line: String): Template {
            val fields = line.split(" ")
            require(fields.size == FIELDS) { "Malformed digit template line" }
            val width = fields[2].toInt()
            val height = fields[3].toInt()
            val raw =
                ByteArray(fields[4].length / 2) { fields[4].substring(it * 2, it * 2 + 2).toInt(HEX_RADIX).toByte() }
            require(
                raw.size == width * height,
            ) { "Digit ${fields[0]}: expected ${width * height} bytes but got ${raw.size}" }
            return Template(fields[0].toInt(), fields[1] == "synthetic", width, height, raw)
        }
    }
}

/** Brings glyph bitmaps of any size to a common grid so they can be compared. */
internal object GlyphImage {
    const val WIDTH = 32
    const val HEIGHT = 40
    private const val BACKGROUND_LEVEL = 90f
    private const val INK_LEVEL = 255f
    private const val BLUR_PASSES = 2

    /**
     * Grayscale bytes to intensities in 0..1 (the slate tab and darker map to 0, white digit ink to 1), slightly
     * blurred so that stroke width and edge position differences between fonts cost little.
     */
    fun normalize(
        gray: ByteArray,
        width: Int,
        height: Int,
    ): FloatArray {
        val horizontal = AreaResampler(width, WIDTH)
        val vertical = AreaResampler(height, HEIGHT)
        val rows =
            Array(height) { y ->
                horizontal.apply(DoubleArray(width) { x -> (gray[y * width + x].toInt() and 0xFF).toDouble() })
            }
        val result = FloatArray(WIDTH * HEIGHT)
        for (x in 0 until WIDTH) {
            val column = vertical.apply(DoubleArray(height) { y -> rows[y][x] })
            for (y in 0 until HEIGHT) {
                val level = (column[y].toFloat() - BACKGROUND_LEVEL) / (INK_LEVEL - BACKGROUND_LEVEL)
                result[y * WIDTH + x] = level.coerceIn(0f, 1f)
            }
        }
        repeat(BLUR_PASSES) { blur(result) }
        return result
    }

    /** One pass of a separable [1 2 1] / 4 blur, in place. */
    private fun blur(values: FloatArray) {
        val copy = values.copyOf()
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val left = copy[y * WIDTH + maxOf(x - 1, 0)]
                val right = copy[y * WIDTH + minOf(x + 1, WIDTH - 1)]
                values[y * WIDTH + x] = (left + 2 * copy[y * WIDTH + x] + right) / 4
            }
        }
        val horizontal = values.copyOf()
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val up = horizontal[maxOf(y - 1, 0) * WIDTH + x]
                val down = horizontal[minOf(y + 1, HEIGHT - 1) * WIDTH + x]
                values[y * WIDTH + x] = (up + 2 * horizontal[y * WIDTH + x] + down) / 4
            }
        }
    }
}
