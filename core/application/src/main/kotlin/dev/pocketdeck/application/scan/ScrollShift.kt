package dev.pocketdeck.application.scan

import kotlin.math.abs

private const val COLUMN_SAMPLES = 12
private const val ROW_STEP = 2
private const val CHANNEL_TOLERANCE = 24
private const val MAX_MISMATCH_SHARE = 0.1
private const val MAX_SHIFT_SHARE = 0.8
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF

/**
 * How many pixels the content moved up between two pictures of the same list, looking only at the rows from [top] to
 * [bottom] and up to four fifths of that band; null when no such movement explains the new picture. The game's list keeps gliding after a swipe, so the
 * distance that was asked for is not the distance that was travelled, and the scanner needs the latter.
 */
fun measureScrollShift(
    before: ScreenFrame,
    after: ScreenFrame,
    top: Int,
    bottom: Int,
): Int? {
    if (before.width != after.width || before.height != after.height) return null
    val first = top.coerceIn(0, before.height)
    val last = bottom.coerceIn(first, before.height)
    // With little left to compare, a plain background row would match by chance.
    val longest = ((last - first) * MAX_SHIFT_SHARE).toInt()
    val best =
        (0..longest).minByOrNull { shift -> mismatchShare(before, after, first, last, shift) } ?: return null
    return best.takeIf { mismatchShare(before, after, first, last, it) <= MAX_MISMATCH_SHARE }
}

private fun mismatchShare(
    before: ScreenFrame,
    after: ScreenFrame,
    first: Int,
    last: Int,
    shift: Int,
): Double {
    var compared = 0
    var mismatches = 0
    for (y in first + shift until last step ROW_STEP) {
        for (sample in 0 until COLUMN_SAMPLES) {
            val x = (sample * before.width + before.width / 2) / COLUMN_SAMPLES
            val old = before.pixels[y * before.width + x].green()
            val new = after.pixels[(y - shift) * after.width + x].green()
            compared++
            if (abs(old - new) > CHANNEL_TOLERANCE) mismatches++
        }
    }
    return if (compared == 0) 1.0 else mismatches.toDouble() / compared
}

private fun Int.green() = (this shr GREEN_SHIFT) and CHANNEL_MASK
