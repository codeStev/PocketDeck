package dev.pocketdeck.adapter.ui.chase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.chase.RankCardsToChase
import dev.pocketdeck.domain.card.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A card the player should get next, in the language of the app. */
data class ChaseCardItem(
    val key: String,
    val name: String,
    val artworkUrl: String?,
    /** Tournament decks that only lack this card. */
    val decksFinished: Int,
    /** Near-complete tournament decks that play it. */
    val decksHelped: Int,
    val packs: List<String>,
)

/** A pack with the best of the cards the player lacks that it holds. */
data class ChasePackItem(
    val name: String,
    /** How many cards worth chasing the pack holds. */
    val cardCount: Int,
    /** The most valuable of them, best first. */
    val cardNames: List<String>,
)

data class ChaseUiState(
    val cards: List<ChaseCardItem> = emptyList(),
    val packs: List<ChasePackItem> = emptyList(),
)

/** Drives the "What to chase" dialog: follows the ranking and keeps only the best of it for the small screen. */
class ChaseViewModel(
    rankCardsToChase: RankCardsToChase,
    private val language: Language,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChaseUiState())
    val state: StateFlow<ChaseUiState> = mutableState

    init {
        viewModelScope.launch {
            rankCardsToChase().collect { mutableState.value = ChaseListing.build(it, language) }
        }
    }

    class Factory(
        private val rankCardsToChase: RankCardsToChase,
        private val language: Language,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChaseViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return ChaseViewModel(rankCardsToChase, language) as T
        }
    }
}
