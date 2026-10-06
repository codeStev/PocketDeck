package dev.pocketdeck.application.fakes

import dev.pocketdeck.application.scan.CardRecognizer
import dev.pocketdeck.application.scan.ScreenFrame
import dev.pocketdeck.domain.scan.TileRecognition

/** Returns the queued recognitions, one list per submitted frame. */
class FakeCardRecognizer(
    vararg perFrame: List<TileRecognition>,
) : CardRecognizer {
    private val queue = ArrayDeque(perFrame.toList())
    val framesSeen = mutableListOf<ScreenFrame>()

    override suspend fun recognize(frame: ScreenFrame): List<TileRecognition> {
        framesSeen += frame
        return queue.removeFirstOrNull().orEmpty()
    }
}
