package dev.pocketdeck.application.scan

import dev.pocketdeck.domain.scan.TileRecognition

/** Outbound port: finds the cards (and their copy counts) visible in one screenshot of the collection grid. */
fun interface CardRecognizer {
    suspend fun recognize(frame: ScreenFrame): List<TileRecognition>
}
