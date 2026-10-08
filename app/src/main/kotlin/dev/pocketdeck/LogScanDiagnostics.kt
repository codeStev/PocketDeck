package dev.pocketdeck

import android.util.Log
import dev.pocketdeck.adapter.ui.about.DiagnosticsLog
import dev.pocketdeck.application.scan.PageReport
import dev.pocketdeck.application.scan.ScanDiagnostics
import dev.pocketdeck.domain.scan.ReviewReason
import dev.pocketdeck.domain.scan.ScannedCard
import dev.pocketdeck.domain.scan.TileRecognition

/** Writes what the scanner saw to the log (tag `PocketDeckScan`), to find where counts or cards go missing. */
class LogScanDiagnostics(
    private val diagnostics: DiagnosticsLog,
) : ScanDiagnostics {
    override fun pageSettled(report: PageReport) {
        val analysis = report.analysis
        val summary =
            "Page ${report.page}: tiles=${analysis?.tilesRecognized} expectedCounts=${analysis?.countsExpected} " +
                "readCounts=${analysis?.countsRead} waited=${report.waitedPolls * POLL_MS} ms timedOut=${report.timedOut}"
        Log.i(TAG, summary)
        // Only the numbers go into the diagnostics a player copies: the tile lines name cards, which are their collection.
        diagnostics.note("Scan: $summary")
        analysis?.let {
            Log.i(
                TAG,
                "Page ${report.page} tiles: ${it.tiles.joinToString(" ") { tile -> describe(tile) }}",
            )
        }
    }

    override fun scanFinished(review: List<ScannedCard>) {
        val byReason = review.groupBy { it.reason }
        val finished = "Scan finished: ${review.size} cards, copies ${review.sumOf { it.copies }}"
        Log.i(TAG, finished)
        diagnostics.note("Scan: $finished")
        ReviewReason.entries.forEach { reason ->
            val cards = byReason[reason].orEmpty()
            if (cards.isNotEmpty()) {
                val detail =
                    cards.joinToString(
                        " ",
                    ) { "${it.cardId.value}x${it.copies}@${"%.2f".format(it.confidence.value)}" }
                Log.i(TAG, "$reason (${cards.size}): $detail")
                diagnostics.note("Scan: $reason: ${cards.size} cards")
            }
        }
    }

    private fun describe(tile: TileRecognition) = "${tile.cardId.value}x${tile.copies ?: "?"}p${tile.parts}"

    private companion object {
        const val TAG = "PocketDeckScan"
        const val POLL_MS = 250
    }
}
