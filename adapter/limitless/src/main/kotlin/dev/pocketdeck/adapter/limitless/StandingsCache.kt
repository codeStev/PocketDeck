package dev.pocketdeck.adapter.limitless

import java.io.File
import java.io.IOException

/**
 * Keeps the raw standings answer of finished tournaments: they never change, so asking again only costs Limitless requests
 * from a rate limit shared by everybody. Without a directory nothing is kept.
 */
internal class StandingsCache(
    private val directory: File?,
) {
    fun read(tournamentId: String): String? = fileFor(tournamentId)?.takeIf { it.isFile }?.readText()

    fun write(
        tournamentId: String,
        body: String,
    ) {
        val file = fileFor(tournamentId) ?: return
        try {
            file.parentFile.mkdirs()
            file.writeText(body)
        } catch (failure: IOException) {
            // The cache is an optimisation: a full or read-only disk must not fail a refresh that already has its data.
            file.delete()
        }
    }

    // Ids come from the network; keep only what is safe in a file name.
    private fun fileFor(tournamentId: String): File? =
        directory?.let { File(it, "standings-${tournamentId.filter { c -> c.isLetterOrDigit() }}.json") }
}
