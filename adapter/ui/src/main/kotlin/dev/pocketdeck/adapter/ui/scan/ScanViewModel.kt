package dev.pocketdeck.adapter.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.ObserveCardCatalog
import dev.pocketdeck.application.scan.ConfirmScanResults
import dev.pocketdeck.application.scan.FinishCollectionScan
import dev.pocketdeck.application.scan.ObserveScanProgress
import dev.pocketdeck.application.scan.ScanProgress
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.Language
import dev.pocketdeck.domain.scan.Confidence
import dev.pocketdeck.domain.scan.ScannedCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanPhase { IDLE, SCANNING, REVIEW, SAVED }

enum class ScanNotice { NOTHING_TO_REVIEW, SAVE_FAILED }

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val progress: ScanProgress? = null,
    val review: List<ReviewItem> = emptyList(),
    val savedCards: Int = 0,
    val notice: ScanNotice? = null,
    /** Whether the app scrolls the game by itself during the next scan. */
    val automatic: Boolean = true,
)

/** Drives the scan and review screens through the application's inbound ports only. */
class ScanViewModel(
    observeProgress: ObserveScanProgress,
    private val finishScan: FinishCollectionScan,
    private val confirmScan: ConfirmScanResults,
    private val observeCatalog: ObserveCardCatalog,
    private val language: Language,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = mutableState

    init {
        viewModelScope.launch {
            observeProgress().collect { progress ->
                mutableState.update { current ->
                    when {
                        progress != null && current.phase in FOLLOWS_PROGRESS -> {
                            current.copy(phase = ScanPhase.SCANNING, progress = progress, notice = null)
                        }

                        progress == null && current.phase == ScanPhase.SCANNING -> {
                            current.copy(phase = ScanPhase.IDLE, progress = null)
                        }

                        else -> {
                            current
                        }
                    }
                }
            }
        }
    }

    /** The scan was stopped (from the floating button): fetch its result for the player to check. */
    fun onReviewRequested() {
        viewModelScope.launch {
            when (val outcome = finishScan()) {
                is Outcome.Failure -> {
                    mutableState.update {
                        it.copy(
                            phase = ScanPhase.IDLE,
                            progress = null,
                            notice = ScanNotice.NOTHING_TO_REVIEW,
                        )
                    }
                }

                is Outcome.Success -> {
                    val items = ReviewListing.build(outcome.value, observeCatalog().first(), language)
                    mutableState.update {
                        it.copy(
                            phase = ScanPhase.REVIEW,
                            progress = null,
                            review = items,
                            notice = null,
                        )
                    }
                }
            }
        }
    }

    fun onAdjust(
        cardId: CardId,
        delta: Int,
    ) = updateItem(cardId) {
        it.copy(copies = (it.copies + delta).coerceAtLeast(MIN_COPIES), needsReview = false, reason = null)
    }

    fun onAccept(cardId: CardId) = updateItem(cardId) { it.copy(needsReview = false, reason = null) }

    fun onRemove(cardId: CardId) =
        mutableState.update {
            it.copy(
                review =
                    it.review.filterNot { item ->
                        item.cardId ==
                            cardId
                    },
            )
        }

    fun onConfirm() {
        val review = mutableState.value.review
        viewModelScope.launch {
            val scanned =
                review.map {
                    ScannedCard(
                        it.cardId,
                        it.copies,
                        Confidence(it.confidence),
                        needsReview = false,
                    )
                }
            when (val outcome = confirmScan(scanned)) {
                is Outcome.Success -> {
                    mutableState.update {
                        it.copy(
                            phase = ScanPhase.SAVED,
                            review = emptyList(),
                            savedCards = outcome.value.updatedCards,
                        )
                    }
                }

                is Outcome.Failure -> {
                    mutableState.update { it.copy(notice = ScanNotice.SAVE_FAILED) }
                }
            }
        }
    }

    fun onDiscard() = mutableState.update { it.copy(phase = ScanPhase.IDLE, review = emptyList(), notice = null) }

    fun onAutomaticChanged(automatic: Boolean) = mutableState.update { it.copy(automatic = automatic) }

    fun onDone() = mutableState.update { ScanUiState(automatic = it.automatic) }

    private fun updateItem(
        cardId: CardId,
        change: (ReviewItem) -> ReviewItem,
    ) = mutableState.update { state ->
        state.copy(review = state.review.map { if (it.cardId == cardId) change(it) else it })
    }

    class Factory(
        private val observeProgress: ObserveScanProgress,
        private val finishScan: FinishCollectionScan,
        private val confirmScan: ConfirmScanResults,
        private val observeCatalog: ObserveCardCatalog,
        private val language: Language,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScanViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return ScanViewModel(observeProgress, finishScan, confirmScan, observeCatalog, language) as T
        }
    }

    private companion object {
        const val MIN_COPIES = 1
        val FOLLOWS_PROGRESS = setOf(ScanPhase.IDLE, ScanPhase.SCANNING)
    }
}
