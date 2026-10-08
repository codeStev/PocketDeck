package dev.pocketdeck.adapter.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.ObserveCardCatalog
import dev.pocketdeck.application.catalog.SyncCardCatalog
import dev.pocketdeck.application.collection.AdjustOwnedCount
import dev.pocketdeck.application.collection.ObserveCollection
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.Language
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Talks to the application only through its inbound ports. */
@OptIn(FlowPreview::class)
class CollectionViewModel(
    observeCatalog: ObserveCardCatalog,
    observeCollection: ObserveCollection,
    private val adjustOwnedCount: AdjustOwnedCount,
    private val syncCardCatalog: SyncCardCatalog,
    private val language: Language,
    listingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val query = MutableStateFlow("")

    /** What the player has typed so far; updates immediately so the text field never lags behind the keyboard. */
    val queryText: StateFlow<String> = query

    // Everything the filter panel and the owned-only switch set; the search text is kept apart so typing never waits for it.
    private val choices = MutableStateFlow(CollectionFilter())
    private val syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    val state: StateFlow<CollectionUiState> =
        combine(
            observeCatalog(),
            observeCollection(),
            query,
            choices,
            syncState,
        ) { catalog, collection, q, chosen, sync ->
            val filter = chosen.copy(query = q)
            val listing = CollectionListing.build(catalog, collection, filter, language)
            CollectionUiState(
                items = listing.items,
                uniqueOwned = listing.uniqueOwned,
                totalOwned = listing.totalOwned,
                catalogSize = listing.catalogSize,
                filter = filter,
                options = listing.options,
                sync = sync,
            )
        }.flowOn(listingDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CollectionUiState())

    init {
        viewModelScope.launch {
            val catalog = observeCatalog().first()
            if (catalog.size == 0 || catalog.lacksCardDetails) sync()
        }
    }

    fun onQueryChange(newQuery: String) = query.update { newQuery }

    fun onFilterChange(filter: CollectionFilter) = choices.update { filter.copy(query = "") }

    fun adjust(
        cardId: CardId,
        delta: Int,
    ) {
        viewModelScope.launch {
            when (adjustOwnedCount(cardId, delta)) {
                is Outcome.Success -> Unit

                // The card vanished in a concurrent catalog sync; its tile disappears with the next emission.
                is Outcome.Failure -> Unit
            }
        }
    }

    fun sync() {
        if (syncState.value == SyncState.Syncing) return
        viewModelScope.launch {
            syncState.value = SyncState.Syncing
            syncState.value =
                when (val outcome = syncCardCatalog()) {
                    is Outcome.Success -> SyncState.Idle
                    is Outcome.Failure -> SyncState.Failed(outcome.reason)
                }
        }
    }

    /** Lets the composition root create the view model with its ports without exposing them to the UI. */
    class Factory(
        private val observeCatalog: ObserveCardCatalog,
        private val observeCollection: ObserveCollection,
        private val adjustOwnedCount: AdjustOwnedCount,
        private val syncCardCatalog: SyncCardCatalog,
        private val language: Language,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CollectionViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return CollectionViewModel(
                observeCatalog,
                observeCollection,
                adjustOwnedCount,
                syncCardCatalog,
                language,
            ) as T
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val QUERY_DEBOUNCE_MS = 200L
    }
}
