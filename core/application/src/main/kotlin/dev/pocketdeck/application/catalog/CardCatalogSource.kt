package dev.pocketdeck.application.catalog

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.domain.card.Card

data class SourceUnavailable(
    val reason: String,
)

/** Outbound port: where the list of all existing cards comes from (a remote database, a bundled file, ...). */
interface CardCatalogSource {
    suspend fun fetchAll(): Outcome<List<Card>, SourceUnavailable>
}
