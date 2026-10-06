package dev.pocketdeck.adapter.carddb

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.CardCatalogSource
import dev.pocketdeck.application.catalog.SourceUnavailable
import dev.pocketdeck.domain.card.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads the master card dataset (see `tools/master_dataset`) that ships with the app, so the catalog is
 * available offline. [openDataset] hands out a fresh stream per call; this adapter closes it.
 */
class MasterCatalogSource(
    private val openDataset: () -> InputStream,
) : CardCatalogSource {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun fetchAll(): Outcome<List<Card>, SourceUnavailable> =
        withContext(Dispatchers.IO) {
            try {
                val dataset = openDataset().use { json.decodeFromStream<MasterDatasetDto>(it) }
                check(dataset.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
                    "schema version ${dataset.schemaVersion} is not supported (expected $SUPPORTED_SCHEMA_VERSION)"
                }
                Outcome.Success(dataset.cards.map(MasterCardDto::toDomain))
            } catch (failure: IOException) {
                unavailable(failure.message ?: "dataset could not be read")
            } catch (failure: SerializationException) {
                unavailable("unreadable card dataset: ${failure.message}")
            } catch (failure: InvalidCardData) {
                unavailable(failure.message.orEmpty())
            } catch (failure: IllegalStateException) {
                unavailable(failure.message.orEmpty())
            }
        }

    private fun unavailable(reason: String) = Outcome.Failure(SourceUnavailable(reason))

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}
