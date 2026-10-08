package dev.pocketdeck.application.exchange

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogRepository
import dev.pocketdeck.application.collection.CollectionBackup
import dev.pocketdeck.application.collection.CollectionRepository
import dev.pocketdeck.application.profile.ProfileRepository
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.flow.first

/** What reading a file found, shown to the player before anything is replaced. */
data class ImportPreview(
    val accountName: String,
    val collection: CardCollection,
    val cards: Int,
    val copies: Int,
    /** Ids in the file that this version's card database does not know; they are left out. */
    val unknownCards: Int,
    val currentCards: Int,
)

sealed interface ImportFailure {
    data object NotACollectionFile : ImportFailure

    data class UnsupportedVersion(
        val version: Int,
    ) : ImportFailure

    /** The file is a collection but none of its cards is known, so importing it would empty the account. */
    data object NothingRecognised : ImportFailure
}

/** Inbound port: read a collection file and say what importing it would do, without changing anything. */
fun interface ReadCollectionFile {
    suspend operator fun invoke(text: String): Outcome<ImportPreview, ImportFailure>
}

/** Inbound port: replace the active account's collection with what a file said. The old one can be restored. */
fun interface ReplaceCollection {
    suspend operator fun invoke(preview: ImportPreview)
}

class ReadCollectionFileService(
    private val codec: CollectionFileCodec,
    private val catalogs: CardCatalogRepository,
    private val collections: CollectionRepository,
    private val profiles: ProfileRepository,
) : ReadCollectionFile {
    override suspend fun invoke(text: String): Outcome<ImportPreview, ImportFailure> =
        when (val decoded = codec.decode(text)) {
            is Outcome.Failure -> Outcome.Failure(decoded.reason.toFailure())
            is Outcome.Success -> preview(decoded.value)
        }

    private suspend fun preview(copiesById: Map<String, Int>): Outcome<ImportPreview, ImportFailure> {
        val catalog = catalogs.current()
        val known = copiesById.filterKeys { CardId(it) in catalog }
        if (known.isEmpty()) return Outcome.Failure(ImportFailure.NothingRecognised)
        val collection =
            known.entries.fold(
                CardCollection.EMPTY,
            ) { acc, (id, copies) -> acc.withCopies(CardId(id), copies) }
        return Outcome.Success(
            ImportPreview(
                accountName =
                    profiles
                        .observe()
                        .first()
                        .active.name.value,
                collection = collection,
                cards = collection.copiesByCard.size,
                copies = collection.copiesByCard.values.sum(),
                unknownCards = copiesById.size - known.size,
                currentCards = collections.current().copiesByCard.size,
            ),
        )
    }

    private fun FileProblem.toFailure(): ImportFailure =
        when (this) {
            FileProblem.NotACollectionFile -> ImportFailure.NotACollectionFile
            is FileProblem.UnsupportedVersion -> ImportFailure.UnsupportedVersion(version)
        }
}

class ReplaceCollectionService(
    private val collections: CollectionRepository,
    private val backup: CollectionBackup,
) : ReplaceCollection {
    override suspend fun invoke(preview: ImportPreview) {
        // The same backup a scan keeps, so "Restore the collection from before the last scan" also undoes an import.
        backup.save(collections.current())
        collections.save(preview.collection)
    }
}
