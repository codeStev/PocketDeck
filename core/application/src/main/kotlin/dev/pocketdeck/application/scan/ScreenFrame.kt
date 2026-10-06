package dev.pocketdeck.application.scan

/** A captured screenshot as framework-neutral ARGB pixels, so no Android type leaks into the core. */
class ScreenFrame(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "Frame dimensions must be positive but were ${width}x$height" }
        require(pixels.size == width * height) {
            "Expected ${width * height} pixels for ${width}x$height but got ${pixels.size}"
        }
    }
}

private const val SAMPLE_COUNT = 4096
private const val CHANNEL_TOLERANCE = 8
private const val MISMATCH_SHARE_PERCENT = 1
private const val CHANNEL_MASK = 0xFF

/**
 * Whether both frames show the same picture. Compares a fixed sample of pixels and tolerates a little noise (shimmering
 * foil, a blinking cursor), so a screen that rests is recognised as unchanged while any scroll is not.
 */
fun ScreenFrame.looksLike(other: ScreenFrame): Boolean {
    if (width != other.width || height != other.height) return false
    val samples = minOf(SAMPLE_COUNT, pixels.size)
    var mismatches = 0
    for (i in 0 until samples) {
        val index = (i.toLong() * pixels.size / samples).toInt()
        if (differs(pixels[index], other.pixels[index])) mismatches++
    }
    return mismatches * 100 <= samples * MISMATCH_SHARE_PERCENT
}

private fun differs(
    a: Int,
    b: Int,
): Boolean {
    for (shift in intArrayOf(0, 8, 16)) {
        val delta = ((a shr shift) and CHANNEL_MASK) - ((b shr shift) and CHANNEL_MASK)
        if (delta > CHANNEL_TOLERANCE || delta < -CHANNEL_TOLERANCE) return true
    }
    return false
}
