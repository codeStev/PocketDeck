package dev.pocketdeck.adapter.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.advice.AdvisorFailure
import dev.pocketdeck.application.advice.AdvisorProvider
import dev.pocketdeck.application.advice.AdvisorSettings
import dev.pocketdeck.application.advice.AskAiFailure
import dev.pocketdeck.application.deckcode.DeckCodeFailure
import dev.pocketdeck.application.deckcode.ExportDeckCode
import dev.pocketdeck.application.meta.MetaSourceFailure
import dev.pocketdeck.application.meta.RefreshMetaSnapshot
import dev.pocketdeck.application.opening.AnalyseDeckOpening
import dev.pocketdeck.application.savedeck.KeptDeck
import dev.pocketdeck.application.suggest.BuildDeck
import dev.pocketdeck.application.suggest.ListDeckCentrepieces
import dev.pocketdeck.application.suggest.SuggestDecks
import dev.pocketdeck.domain.builder.DeckFocus
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.deck.DeckSuggestion
import dev.pocketdeck.domain.deck.SavedDeckId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DecksNotice {
    NOT_REACHABLE,
    NO_DECKLISTS,
    BUILD_FAILED,
    AI_NEEDS_KEY,
    AI_INVALID_KEY,
    AI_BUSY,
    AI_UNREACHABLE,
    AI_REJECTED,
    AI_UNUSABLE,
    AI_NO_VALID_DECK,
}

/** A Pokémon the player can build a deck around. */
data class CentrepieceItem(
    val cardId: String,
    val name: String,
    val artworkUrl: String?,
)

/** What the player sees when they ask for the import code of a deck. */
sealed interface DeckCodeUi {
    val title: String

    data class Ready(
        override val title: String,
        val text: String,
        /** The cards of the deck that are not in the tournament list, so the player knows what the code contains. */
        val substitutes: List<SubstituteLine>,
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
    /** The Pokémon to choose from while the builder is open; null when it is closed. */
    val centrepieces: List<CentrepieceItem>? = null,
    val building: Boolean = false,
    /** More about [notice], as the service said it. */
    val noticeDetail: String? = null,
    /** Whether a key is set up for at least one AI service. */
    val aiConfigured: Boolean = false,
    val asking: Boolean = false,
    /** The kept decks of the active account, newest first. */
    val saved: List<DeckItem> = emptyList(),
    val savedDialog: SavedDeckDialog? = null,
    /** The opening odds asked for so far, by [DeckItem.deckId]. */
    val openings: Map<Int, OpeningState> = emptyMap(),
    /** The [DeckItem.deckId] of every kept deck, so a deck that is already kept can say so. */
    val savedDeckIds: Set<Int> = emptySet(),
    /** False until the first list of suggestions has arrived, so an empty list is not shown as "nothing here" while loading. */
    val loaded: Boolean = false,
)

/** Shows the suggested decks and refreshes the picture of the competitive field. */
class DecksViewModel(
    suggestDecks: SuggestDecks,
    private val refreshMeta: RefreshMetaSnapshot,
    private val exportDeckCode: ExportDeckCode,
    private val buildDeck: BuildDeck,
    private val listCentrepieces: ListDeckCentrepieces,
    private val ai: AiAssistant,
    private val savedDecks: SavedDecks,
    private val analyseOpening: AnalyseDeckOpening,
    private val language: Language,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DecksUiState())
    val state: StateFlow<DecksUiState> = mutableState
    private var suggestions: List<DeckSuggestion> = emptyList()
    private var builtDecks: List<DeckSuggestion> = emptyList()
    private var shown: List<DeckSuggestion> = emptyList()
    private var kept: List<KeptDeck> = emptyList()
    private var centres: Map<String, PokemonCard> = emptyMap()
    private var advisorSettings = AdvisorSettings()

    init {
        viewModelScope.launch {
            suggestDecks().collect { latest ->
                suggestions = latest
                publish()
                mutableState.update { it.copy(loaded = true) }
            }
        }
        viewModelScope.launch {
            savedDecks.observe().collect { latest ->
                kept = latest
                val ids = latest.map { DeckListing.deckIdOf(it.suggestion.deck) }.toSet()
                mutableState.update { it.copy(saved = DeckListing.buildSaved(latest, language), savedDeckIds = ids) }
            }
        }
        viewModelScope.launch {
            ai.observeSettings().collect { settings ->
                advisorSettings = settings
                mutableState.update { it.copy(aiConfigured = settings.usable().isNotEmpty()) }
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
        val item = (state.value.items + state.value.saved).firstOrNull { it.key == key } ?: return
        val suggestion = suggestionOf(item) ?: return
        val code =
            when (val outcome = exportDeckCode(suggestion.deck)) {
                is Outcome.Success -> DeckCodeUi.Ready(item.title, outcome.value.text, item.substitutes)
                is Outcome.Failure -> DeckCodeUi.Unavailable(item.title, outcome.reason.unknownCards())
            }
        mutableState.update { it.copy(code = code) }
    }

    /** Works out, once per deck, how likely its Pokémon are to be attacking in the first turns. */
    fun onShowOpening(deckId: Int) {
        if (deckId in state.value.openings) return
        val deck =
            (shown + kept.map { it.suggestion }).firstOrNull { DeckListing.deckIdOf(it.deck) == deckId }?.deck ?: return
        mutableState.update { it.copy(openings = it.openings + (deckId to OpeningState.Loading)) }
        viewModelScope.launch {
            val odds = OpeningListing.build(analyseOpening(deck), language)
            mutableState.update { it.copy(openings = it.openings + (deckId to OpeningState.Ready(odds))) }
        }
    }

    private fun suggestionOf(item: DeckItem): DeckSuggestion? =
        if (item.savedId != null) {
            kept.firstOrNull { it.id.value == item.savedId }?.suggestion
        } else {
            shown.getOrNull(state.value.items.indexOf(item))
        }

    /** Keeps a generated deck. Keeping the same cards twice leaves one entry, so there is nothing to explain. */
    fun onSave(key: String) {
        val item = state.value.items.firstOrNull { it.key == key } ?: return
        val suggestion = suggestionOf(item) ?: return
        viewModelScope.launch { savedDecks.save(suggestion) }
    }

    fun onRenameSavedRequested(item: DeckItem) = askAboutSaved(item) { id -> SavedDeckDialog.Rename(id, item.title) }

    fun onDeleteSavedRequested(item: DeckItem) =
        askAboutSaved(item) { id -> SavedDeckDialog.ConfirmDelete(id, item.title) }

    private fun askAboutSaved(
        item: DeckItem,
        dialog: (Long) -> SavedDeckDialog,
    ) {
        val id = item.savedId ?: return
        mutableState.update { it.copy(savedDialog = dialog(id)) }
    }

    fun onSavedDialogDismissed() = mutableState.update { it.copy(savedDialog = null) }

    fun onSavedRenamed(title: String) {
        val dialog = state.value.savedDialog as? SavedDeckDialog.Rename ?: return
        viewModelScope.launch {
            savedDecks.rename(SavedDeckId(dialog.id), title)
            onSavedDialogDismissed()
        }
    }

    fun onSavedDeleteConfirmed() {
        val dialog = state.value.savedDialog as? SavedDeckDialog.ConfirmDelete ?: return
        viewModelScope.launch {
            savedDecks.delete(SavedDeckId(dialog.id))
            onSavedDialogDismissed()
        }
    }

    /** Decks the player asked for come first; they stay until the app closes. */
    private fun publish() {
        shown = builtDecks + suggestions
        mutableState.update { it.copy(items = DeckListing.build(shown, language)) }
    }

    fun onOpenBuilder() {
        viewModelScope.launch {
            val list = listCentrepieces()
            centres = list.associateBy { it.id.value }
            val items = list.map { CentrepieceItem(it.id.value, it.nameIn(language).value, it.artwork?.value) }
            mutableState.update { it.copy(centrepieces = items) }
        }
    }

    /** Asks the AI for a deck. Without a key there is nothing to ask; the screen then points to the settings. */
    fun onAskAi(wish: String?) {
        if (!state.value.aiConfigured) {
            mutableState.update { it.copy(centrepieces = null, notice = DecksNotice.AI_NEEDS_KEY, noticeDetail = null) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(centrepieces = null, asking = true, notice = null, noticeDetail = null) }
            when (val outcome = ai.ask(wish?.trim()?.takeIf { it.isNotEmpty() })) {
                is Outcome.Success -> {
                    builtDecks = (listOf(outcome.value) + builtDecks).take(MAX_BUILT_DECKS)
                    publish()
                    mutableState.update { it.copy(asking = false) }
                }

                is Outcome.Failure -> {
                    val (notice, detail) = outcome.reason.toNotice()
                    mutableState.update { it.copy(asking = false, notice = notice, noticeDetail = detail) }
                }
            }
        }
    }

    private fun AskAiFailure.toNotice(): Pair<DecksNotice, String?> =
        when (this) {
            is AskAiFailure.NoValidDeck -> {
                DecksNotice.AI_NO_VALID_DECK to problems.firstOrNull()
            }

            is AskAiFailure.AdvisorFailed -> {
                when (val reason = failure) {
                    AdvisorFailure.MissingKey -> DecksNotice.AI_NEEDS_KEY to null
                    AdvisorFailure.InvalidKey -> DecksNotice.AI_INVALID_KEY to null
                    is AdvisorFailure.Busy -> DecksNotice.AI_BUSY to reason.reason
                    is AdvisorFailure.NotReachable -> DecksNotice.AI_UNREACHABLE to reason.reason
                    is AdvisorFailure.Rejected -> DecksNotice.AI_REJECTED to reason.reason
                    is AdvisorFailure.Unusable -> DecksNotice.AI_UNUSABLE to reason.reason
                }
            }
        }

    fun onCloseBuilder() = mutableState.update { it.copy(centrepieces = null) }

    /** Builds a deck around the chosen Pokémon, or the best one the collection allows if none is chosen. */
    fun onBuild(centrepieceId: String?) {
        viewModelScope.launch {
            mutableState.update { it.copy(centrepieces = null, building = true, notice = null) }
            val focus = DeckFocus(core = centrepieceId?.let(centres::get))
            when (val outcome = buildDeck(focus)) {
                is Outcome.Success -> {
                    builtDecks = (listOf(outcome.value) + builtDecks).take(MAX_BUILT_DECKS)
                    publish()
                    mutableState.update { it.copy(building = false) }
                }

                is Outcome.Failure -> {
                    mutableState.update { it.copy(building = false, notice = DecksNotice.BUILD_FAILED) }
                }
            }
        }
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

    private companion object {
        const val MAX_BUILT_DECKS = 10
    }

    class Factory(
        private val suggestDecks: SuggestDecks,
        private val refreshMeta: RefreshMetaSnapshot,
        private val exportDeckCode: ExportDeckCode,
        private val buildDeck: BuildDeck,
        private val listCentrepieces: ListDeckCentrepieces,
        private val ai: AiAssistant,
        private val savedDecks: SavedDecks,
        private val analyseOpening: AnalyseDeckOpening,
        private val language: Language,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DecksViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return DecksViewModel(
                suggestDecks,
                refreshMeta,
                exportDeckCode,
                buildDeck,
                listCentrepieces,
                ai,
                savedDecks,
                analyseOpening,
                language,
            ) as T
        }
    }
}
