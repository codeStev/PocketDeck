package dev.pocketdeck.adapter.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.catalog.ObserveCardCatalog
import dev.pocketdeck.application.collection.ObserveBackupAvailable
import dev.pocketdeck.application.collection.ObserveCollection
import dev.pocketdeck.application.collection.RestorePreviousCollection
import dev.pocketdeck.application.consent.Consent
import dev.pocketdeck.application.consent.GrantConsent
import dev.pocketdeck.application.consent.ObserveConsent
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

enum class ScanNotice { NOTHING_TO_REVIEW, SAVE_FAILED, RESTORED, NOTHING_TO_RESTORE }

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    val progress: ScanProgress? = null,
    val review: List<ReviewItem> = emptyList(),
    val savedCards: Int = 0,
    val notice: ScanNotice? = null,
    /** Whether the app scrolls the game by itself during the next scan. */
    val automatic: Boolean = true,
    /** Whether saving makes the scan the whole collection (cards it did not find become 0). */
    val replaceCollection: Boolean = false,
    /** The scan found far fewer cards than the collection holds, so it probably stopped early. */
    val looksIncomplete: Boolean = false,
    /** Unique cards in the collection before this scan was saved. */
    val ownedBefore: Int = 0,
    /** Whether the collection from before the last saved scan can be restored. */
    val backupAvailable: Boolean = false,
    /** Whether the player has agreed to the auto-scroll service swiping the game for them. */
    val autoScrollConsented: Boolean = false,
)

/** The inbound ports behind the player's consent to the things the app does in the game for them. */
class ConsentAccess(
    val observe: ObserveConsent,
    val grant: GrantConsent,
)

/** Drives the scan and review screens through the application's inbound ports only. */
class ScanViewModel(
    observeProgress: ObserveScanProgress,
    private val finishScan: FinishCollectionScan,
    private val confirmScan: ConfirmScanResults,
    private val observeCatalog: ObserveCardCatalog,
    private val observeCollection: ObserveCollection,
    observeBackup: ObserveBackupAvailable,
    private val restorePrevious: RestorePreviousCollection,
    private val language: Language,
    private val consent: ConsentAccess,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = mutableState

    init {
        viewModelScope.launch {
            consent.observe(Consent.AUTO_SCROLL).collect { given ->
                mutableState.update { it.copy(autoScrollConsented = given) }
            }
        }
        viewModelScope.launch {
            observeBackup().collect { available ->
                mutableState.update { it.copy(backupAvailable = available) }
            }
        }
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
    fun onReviewRequested(complete: Boolean) {
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
                    val owned = observeCollection().first().copiesByCard.size
                    // A scan that stopped halfway must never be able to wipe the rest of the collection.
                    val incomplete = owned > 0 && items.size < owned * MIN_SHARE_OF_OWNED
                    mutableState.update {
                        it.copy(
                            phase = ScanPhase.REVIEW,
                            progress = null,
                            review = items,
                            notice = null,
                            replaceCollection = complete && !incomplete,
                            looksIncomplete = incomplete,
                            ownedBefore = owned,
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

    fun onReplaceChanged(replace: Boolean) = mutableState.update { it.copy(replaceCollection = replace) }

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
        val replace = mutableState.value.replaceCollection
        viewModelScope.launch {
            val scanned =
                review.map {
                    ScannedCard(
                        it.cardId,
                        it.copies,
                        Confidence(it.confidence),
                        needsReview = it.needsReview,
                        reason = it.reason,
                    )
                }
            when (val outcome = confirmScan(scanned, replace)) {
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

    /** The player read the disclosure and agreed to the auto-scroll service. */
    fun onAutoScrollConsent() {
        viewModelScope.launch { consent.grant(Consent.AUTO_SCROLL) }
    }

    fun onAutomaticChanged(automatic: Boolean) = mutableState.update { it.copy(automatic = automatic) }

    fun onRestorePrevious() {
        viewModelScope.launch {
            val notice =
                when (restorePrevious()) {
                    is Outcome.Success -> ScanNotice.RESTORED
                    is Outcome.Failure -> ScanNotice.NOTHING_TO_RESTORE
                }
            mutableState.update { it.copy(notice = notice) }
        }
    }

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
        private val observeCollection: ObserveCollection,
        private val observeBackup: ObserveBackupAvailable,
        private val restorePrevious: RestorePreviousCollection,
        private val language: Language,
        private val consent: ConsentAccess,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScanViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return ScanViewModel(
                observeProgress,
                finishScan,
                confirmScan,
                observeCatalog,
                observeCollection,
                observeBackup,
                restorePrevious,
                language,
                consent,
            ) as T
        }
    }

    private companion object {
        const val MIN_COPIES = 1
        const val MIN_SHARE_OF_OWNED = 0.8
        val FOLLOWS_PROGRESS = setOf(ScanPhase.IDLE, ScanPhase.SCANNING)
    }
}
