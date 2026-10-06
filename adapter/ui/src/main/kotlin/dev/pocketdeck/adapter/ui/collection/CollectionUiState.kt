package dev.pocketdeck.adapter.ui.collection

import dev.pocketdeck.application.catalog.CatalogSyncFailure
import dev.pocketdeck.domain.card.CardId

/** One tile of the collection grid, already formatted for display. */
data class CardItem(
    val id: CardId,
    val name: String,
    val detail: String,
    val artworkUrl: String?,
    val copies: Int,
)

sealed interface SyncState {
    data object Idle : SyncState

    data object Syncing : SyncState

    data class Failed(
        val failure: CatalogSyncFailure,
    ) : SyncState
}

data class CollectionUiState(
    val items: List<CardItem> = emptyList(),
    val uniqueOwned: Int = 0,
    val totalOwned: Int = 0,
    val catalogSize: Int = 0,
    val query: String = "",
    val ownedOnly: Boolean = false,
    val sync: SyncState = SyncState.Idle,
)
