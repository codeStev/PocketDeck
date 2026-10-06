package dev.pocketdeck.adapter.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.deckcode.DeckCodeFailure
import dev.pocketdeck.application.deckcode.ExportDeckCode
import dev.pocketdeck.application.meta.MetaSourceFailure
import dev.pocketdeck.application.meta.RefreshMetaSnapshot
import dev.pocketdeck.application.suggest.SuggestDecks
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.deck.DeckSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DecksNotice { NOT_REACHABLE, NO_DECKLISTS }

/** What the player sees when they ask for the import code of a deck. */
sealed interface DeckCodeUi {
    val title: String

    data class Ready(
        override val title: String,
        val text: String,
    ) : DeckCodeUi

    /** The game could not import this deck; [cards] are the cards it does not know (empty if it is the energy). */
    data class Unavailable(
        override val title: String,
        val cards: List<String>,
    ) : DeckCodeUi
}

data class DecksUiState(
    val items: List<DeckItem> = emptyList(),
    val refreshing: Boolean = false,
    val notice: DecksNotice? = null,
    val code: DeckCodeUi? = null,
)

/** Shows the suggested decks and refreshes the picture of the competitive field. */
class DecksViewModel(
    suggestDecks: SuggestDecks,
    private val refreshMeta: RefreshMetaSnapshot,
    private val exportDeckCode: ExportDeckCode,
    private val language: Language,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DecksUiState())
    val state: StateFlow<DecksUiState> = mutableState
    private var suggestions: List<DeckSuggestion> = emptyList()

    init {
        viewModelScope.launch {
            suggestDecks().collect { latest ->
                suggestions = latest
                mutableState.update { it.copy(items = DeckListing.build(latest, language)) }
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

    fun onShowCode(key: String) {
        val item = state.value.items.firstOrNull { it.key == key } ?: return
        val suggestion = suggestions.getOrNull(state.value.items.indexOf(item)) ?: return
        val code =
            when (val outcome = exportDeckCode(suggestion.deck)) {
                is Outcome.Success -> DeckCodeUi.Ready(item.title, outcome.value.text)
                is Outcome.Failure -> DeckCodeUi.Unavailable(item.title, outcome.reason.unknownCards())
            }
        mutableState.update { it.copy(code = code) }
    }

    fun onCloseCode() = mutableState.update { it.copy(code = null) }

    private fun DeckCodeFailure.unknownCards() =
        when (this) {
            is DeckCodeFailure.UnknownToGame -> cards.map { it.value }
            DeckCodeFailure.NoSelectableEnergy -> emptyList()
        }

    private fun MetaSourceFailure.toNotice() =
        when (this) {
            is MetaSourceFailure.NotReachable -> DecksNotice.NOT_REACHABLE
            MetaSourceFailure.NoDecklists -> DecksNotice.NO_DECKLISTS
        }

    class Factory(
        private val suggestDecks: SuggestDecks,
        private val refreshMeta: RefreshMetaSnapshot,
        private val exportDeckCode: ExportDeckCode,
        private val language: Language,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DecksViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return DecksViewModel(suggestDecks, refreshMeta, exportDeckCode, language) as T
        }
    }
}
