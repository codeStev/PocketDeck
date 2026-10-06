package dev.pocketdeck

import android.util.Log
import dev.pocketdeck.application.scan.PageReport
import dev.pocketdeck.application.scan.ScanDiagnostics
import dev.pocketdeck.domain.scan.ReviewReason
import dev.pocketdeck.domain.scan.ScannedCard

/** Writes what the scanner saw to the log (tag `PocketDeckScan`), to find where counts or cards go missing. */
class LogScanDiagnostics : ScanDiagnostics {
    override fun pageSettled(report: PageReport) {
        val analysis = report.analysis
        Log.i(
            TAG,
            "Page ${report.page}: tiles=${analysis?.tilesRecognized} expectedCounts=${analysis?.countsExpected} " +
                "readCounts=${analysis?.countsRead} waited=${report.waitedPolls * POLL_MS} ms timedOut=${report.timedOut}",
        )
    }

    override fun scanFinished(review: List<ScannedCard>) {
        val byReason = review.groupBy { it.reason }
        Log.i(TAG, "Scan finished: ${review.size} cards, copies ${review.sumOf { it.copies }}")
        ReviewReason.entries.forEach { reason ->
            val cards = byReason[reason].orEmpty()
            if (cards.isNotEmpty()) {
                val detail =
                    cards.joinToString(
                        " ",
                    ) { "${it.cardId.value}x${it.copies}@${"%.2f".format(it.confidence.value)}" }
                Log.i(TAG, "$reason (${cards.size}): $detail")
            }
        }
    }

    private companion object {
        const val TAG = "PocketDeckScan"
        const val POLL_MS = 250
    }
}
