package dev.pocketdeck.adapter.capture

import dev.pocketdeck.application.scan.ScreenFrame

/**
 * Spots frames that are identical to the previous one, so a screen that rests while the player reads it is not
 * analysed over and over. Compares a fixed sample of pixels, which is enough to see scrolling and any fade-in.
 */
class FrameChangeDetector {
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastSample: IntArray? = null

    fun isNew(frame: ScreenFrame): Boolean {
        val sample = IntArray(SAMPLES) { frame.pixels[(it.toLong() * frame.pixels.size / SAMPLES).toInt()] }
        val unchanged = frame.width == lastWidth && frame.height == lastHeight && sample.contentEquals(lastSample)
        lastWidth = frame.width
        lastHeight = frame.height
        lastSample = sample
        return !unchanged
    }

    fun reset() {
        lastSample = null
    }

    private companion object {
        const val SAMPLES = 4096
    }
}
