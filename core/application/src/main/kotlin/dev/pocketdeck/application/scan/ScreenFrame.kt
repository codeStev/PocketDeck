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
