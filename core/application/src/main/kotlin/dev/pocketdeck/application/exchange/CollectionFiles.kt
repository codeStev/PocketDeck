package dev.pocketdeck.application.exchange

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.CardId
import java.time.Instant

/** The file formats a collection can be written to and read from. */
enum class FileFormat(
    val extension: String,
    val mimeType: String,
) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
}

/** One owned card as a file lists it; [name] is only there for people reading the file in a spreadsheet. */
data class OwnedCardRow(
    val id: CardId,
    val name: String,
    val copies: Int,
)

/** Everything a collection file says. */
data class CollectionFile(
    val accountName: String,
    val exportedAt: Instant,
    val rows: List<OwnedCardRow>,
)

/** Why a file cannot be read as a collection. */
sealed interface FileProblem {
    /** Not JSON or CSV of ours, or a row that cannot be understood. */
    data object NotACollectionFile : FileProblem

    /** A file from a newer version of the app. */
    data class UnsupportedVersion(
        val version: Int,
    ) : FileProblem
}

/**
 * Outbound port: writes a collection as the text of a file and reads such text back. Reading gives the copies by card id as
 * the file spells them; whether the app knows an id is for the application to decide.
 */
interface CollectionFileCodec {
    fun encode(
        file: CollectionFile,
        format: FileFormat,
    ): String

    fun decode(text: String): Outcome<Map<String, Int>, FileProblem>
}
