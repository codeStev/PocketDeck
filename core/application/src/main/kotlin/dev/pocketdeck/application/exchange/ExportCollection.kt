package dev.pocketdeck.application.exchange

import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.profile.ProfileRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** A collection ready to be saved: the text, and the file name and format to save it under. */
data class ExportedFile(
    val fileName: String,
    val format: FileFormat,
    val text: String,
)

/** Inbound port: the active account's collection as a file, so it can be kept or moved to another phone. */
fun interface ExportCollection {
    suspend operator fun invoke(format: FileFormat): ExportedFile
}

class ExportCollectionService(
    private val collections: CollectionRepository,
    private val catalogs: CardCatalogRepository,
    private val profiles: ProfileRepository,
    private val codec: CollectionFileCodec,
    /** Time comes in from outside so tests can fix it. */
    private val clock: Clock,
) : ExportCollection {
    override suspend fun invoke(format: FileFormat): ExportedFile {
        val account =
            profiles
                .observe()
                .first()
                .active.name.value
        val catalog = catalogs.current()
        val rows =
            collections
                .current()
                .copiesByCard
                .map { (id, copies) -> OwnedCardRow(id, catalog[id]?.name?.value.orEmpty(), copies) }
                .sortedBy { it.id.value }
        val now = clock.instant()
        val text = codec.encode(CollectionFile(account, now, rows), format)
        return ExportedFile(fileNameOf(account, now, format), format, text)
    }

    /** The account name is the player's own words, so anything a file name cannot hold becomes a dash. */
    private fun fileNameOf(
        account: String,
        now: java.time.Instant,
        format: FileFormat,
    ): String {
        val slug =
            account
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifEmpty { "account" }
        return "pocketdeck-$slug-${LocalDate.ofInstant(now, ZoneOffset.UTC)}.${format.extension}"
    }
}
