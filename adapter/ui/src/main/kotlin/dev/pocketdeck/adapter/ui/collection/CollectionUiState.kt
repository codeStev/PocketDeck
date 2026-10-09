package dev.pocketdeck.adapter.ui.collection

import dev.pocketdeck.application.catalog.CatalogSyncFailure
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerKind

/** One tile of the collection grid, already formatted for display. */
data class CardItem(
    val id: CardId,
    val name: String,
    val detail: CardDetail,
    val artworkUrl: String?,
    val copies: Int,
)

/** What a tile says about its card besides the name. The screen turns it into text, so it follows the app's language. */
sealed interface CardDetail {
    data class OfPokemon(
        val energy: EnergyType,
        val stage: Stage,
        val hp: Int,
    ) : CardDetail

    data class OfTrainer(
        val kind: TrainerKind,
    ) : CardDetail
}

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
    val filter: CollectionFilter = CollectionFilter(),
    val options: FilterOptions = FilterOptions(),
    val sync: SyncState = SyncState.Idle,
)
