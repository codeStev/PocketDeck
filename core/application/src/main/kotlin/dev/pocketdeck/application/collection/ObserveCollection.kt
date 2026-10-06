package dev.pocketdeck.application.collection

import dev.pocketdeck.domain.collection.CardCollection
import kotlinx.coroutines.flow.Flow

/** Inbound port: follow the player's collection as it changes. */
fun interface ObserveCollection {
    operator fun invoke(): Flow<CardCollection>
}
