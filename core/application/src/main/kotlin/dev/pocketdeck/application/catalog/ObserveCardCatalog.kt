package dev.pocketdeck.application.catalog

import dev.pocketdeck.domain.card.CardCatalog
import kotlinx.coroutines.flow.Flow

/** Inbound port: follow the card catalog as it is (re)synchronised. */
fun interface ObserveCardCatalog {
    operator fun invoke(): Flow<CardCatalog>
}
