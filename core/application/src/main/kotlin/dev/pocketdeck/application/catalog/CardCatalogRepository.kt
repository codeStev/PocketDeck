package dev.pocketdeck.application.catalog

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import kotlinx.coroutines.flow.Flow

/** Outbound port: local storage of the card catalog. */
interface CardCatalogRepository {
    fun observe(): Flow<CardCatalog>

    suspend fun current(): CardCatalog

    /** Atomically replaces the whole stored catalog. */
    suspend fun replaceAll(cards: List<Card>)
}
