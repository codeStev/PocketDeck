package dev.pocketdeck.adapter.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.meta.MetaSourceFailure
import dev.pocketdeck.application.meta.RefreshMetaSnapshot
import dev.pocketdeck.application.suggest.SuggestDecks
import dev.pocketdeck.domain.card.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DecksNotice { NOT_REACHABLE, NO_DECKLISTS }

data class DecksUiState(
    val items: List<DeckItem> = emptyList(),
    val refreshing: Boolean = false,
    val notice: DecksNotice? = null,
)

/** Shows the suggested decks and refreshes the picture of the competitive field. */
class DecksViewModel(
    suggestDecks: SuggestDecks,
    private val refreshMeta: RefreshMetaSnapshot,
    private val language: Language,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DecksUiState())
    val state: StateFlow<DecksUiState> = mutableState

    init {
        viewModelScope.launch {
            suggestDecks().collect { suggestions ->
                mutableState.update { it.copy(items = DeckListing.build(suggestions, language)) }
            }
        }
        onRefresh()
    }

    fun onRefresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(refreshing = true) }
            val notice =
                when (val outcome = refreshMeta()) {
                    is Outcome.Success -> null
                    is Outcome.Failure -> outcome.reason.toNotice()
                }
            mutableState.update { it.copy(refreshing = false, notice = notice) }
        }
    }

    private fun MetaSourceFailure.toNotice() =
        when (this) {
            is MetaSourceFailure.NotReachable -> DecksNotice.NOT_REACHABLE
            MetaSourceFailure.NoDecklists -> DecksNotice.NO_DECKLISTS
        }

    class Factory(
        private val suggestDecks: SuggestDecks,
        private val refreshMeta: RefreshMetaSnapshot,
        private val language: Language,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DecksViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return DecksViewModel(suggestDecks, refreshMeta, language) as T
        }
    }
}
