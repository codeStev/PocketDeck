package dev.pocketdeck.adapter.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.codestev.pocketdesign.components.PdAction
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCard
import dev.codestev.pocketdesign.components.PdChip
import dev.codestev.pocketdesign.components.PdChipKind
import dev.codestev.pocketdesign.components.PdEmptyState
import dev.codestev.pocketdesign.components.PdIconButton
import dev.codestev.pocketdesign.components.PdIconButtonStyle
import dev.codestev.pocketdesign.components.PdStepper
import dev.codestev.pocketdesign.components.PdSwitch
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.scan.ReviewReason

private val ThumbWidth = 56.dp
private val ThumbHeight = 78.dp
private val ThumbCorner = 6.dp
private val StepBadge = 28.dp
private const val MAX_COPIES = 99

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    accountName: String?,
    autoScrollEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onStartScan: (automatic: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanContent(
        state = state,
        accountName = accountName,
        autoScrollEnabled = autoScrollEnabled,
        onAutomaticChanged = viewModel::onAutomaticChanged,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onStartScan = onStartScan,
        onAutoScrollConsent = viewModel::onAutoScrollConsent,
        onAdjust = viewModel::onAdjust,
        onAccept = viewModel::onAccept,
        onRemove = viewModel::onRemove,
        onReplaceChanged = viewModel::onReplaceChanged,
        onRestorePrevious = viewModel::onRestorePrevious,
        onConfirm = viewModel::onConfirm,
        onDiscard = viewModel::onDiscard,
        onDone = viewModel::onDone,
    )
}

@Composable
internal fun ScanContent(
    state: ScanUiState,
    accountName: String?,
    autoScrollEnabled: Boolean,
    onAutomaticChanged: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartScan: (Boolean) -> Unit,
    onAutoScrollConsent: () -> Unit,
    onAdjust: (CardId, Int) -> Unit,
    onAccept: (CardId) -> Unit,
    onRemove: (CardId) -> Unit,
    onReplaceChanged: (Boolean) -> Unit,
    onRestorePrevious: () -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onDone: () -> Unit,
) {
    when (state.phase) {
        ScanPhase.IDLE, ScanPhase.SCANNING -> {
            Instructions(
                state,
                accountName,
                autoScrollEnabled,
                onAutomaticChanged,
                onOpenAccessibilitySettings,
                onStartScan,
                onAutoScrollConsent,
                onRestorePrevious,
            )
        }

        ScanPhase.REVIEW -> {
            Review(state, accountName, onAdjust, onAccept, onRemove, onReplaceChanged, onConfirm, onDiscard)
        }

        ScanPhase.SAVED -> {
            Saved(state.savedCards, onDone)
        }
    }
}

@Composable
private fun Instructions(
    state: ScanUiState,
    accountName: String?,
    autoScrollEnabled: Boolean,
    onAutomaticChanged: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartScan: (Boolean) -> Unit,
    onAutoScrollConsent: () -> Unit,
    onRestorePrevious: () -> Unit,
) {
    val needsService = state.automatic && !autoScrollEnabled
    var asking by rememberSaveable { mutableStateOf(false) }
    val proceed = { if (needsService) onOpenAccessibilitySettings() else onStartScan(state.automatic) }
    if (asking) {
        AutoScrollConsentDialog(
            onAgree = {
                asking = false
                onAutoScrollConsent()
                proceed()
            },
            onDecline = { asking = false },
        )
    }
    // Scrolls, so large system fonts and the German texts never push the start button off screen.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PdSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space4),
    ) {
        Title(R.string.scan_title, accountName)
        Text(
            stringResource(R.string.scan_intro),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.inkMuted,
        )
        AutomaticSwitch(state.automatic, autoScrollEnabled, onAutomaticChanged)
        Steps(if (state.automatic) AUTOMATIC_STEPS else MANUAL_STEPS)
        state.progress?.let {
            PdChip(
                pluralStringResource(R.plurals.scan_running, it.distinctCards, it.distinctCards),
                kind = PdChipKind.Success,
            )
        }
        // Said before the player taps Start, with its own button, instead of Start silently opening system settings.
        if (needsService) {
            PdBanner(
                stringResource(R.string.scan_automatic_needs_service),
                kind = PdBannerKind.Warning,
                actionLabel = stringResource(R.string.scan_open_accessibility),
                onAction = onOpenAccessibilitySettings,
            )
        }
        state.notice?.let { Notice(it) }
        PdButton(
            stringResource(R.string.scan_start),
            { if (state.automatic && !state.autoScrollConsented) asking = true else proceed() },
            Modifier.fillMaxWidth(),
            enabled = !needsService,
            leadingIcon = PdIcons.Scan,
        )
        if (state.backupAvailable) {
            PdButton(
                stringResource(R.string.scan_restore_previous),
                onRestorePrevious,
                Modifier.fillMaxWidth(),
                style = PdButtonStyle.Secondary,
            )
        }
    }
}

private val AUTOMATIC_STEPS = listOf(R.string.scan_step_view, R.string.scan_step_start)
private val MANUAL_STEPS =
    listOf(R.string.scan_step_view, R.string.scan_step_start, R.string.scan_step_scroll, R.string.scan_step_stop)

/** The screen title and, because both game instances look alike on screen, the account the scan is for. */
@Composable
private fun Title(
    title: Int,
    accountName: String?,
) {
    Column(Modifier.padding(top = PdSpacing.space2), verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        Text(stringResource(title), style = PocketDesign.typography.titleLg, color = PocketDesign.colors.ink)
        accountName?.let { PdChip(stringResource(R.string.scan_account, it), kind = PdChipKind.Selected) }
    }
}

@Composable
private fun AutomaticSwitch(
    automatic: Boolean,
    serviceOn: Boolean,
    onChange: (Boolean) -> Unit,
) {
    PdCard {
        PdSwitch(
            checked = automatic,
            onCheckedChange = onChange,
            label = stringResource(R.string.scan_automatic_label),
            supportingText = if (automatic && serviceOn) stringResource(R.string.scan_automatic_on) else null,
        )
    }
}

@Composable
private fun Steps(steps: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3), verticalAlignment = Alignment.Top) {
                StepNumber(index + 1)
                Text(
                    stringResource(step),
                    style = PocketDesign.typography.body,
                    color = PocketDesign.colors.ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StepNumber(number: Int) {
    val colors = PocketDesign.colors
    Row(
        Modifier
            .size(StepBadge)
            .clip(PocketDesign.shapes.pill)
            .background(colors.accentSoft),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            number.toString(),
            style = PocketDesign.typography.numeric.copy(fontSize = 14.sp),
            color = colors.onAccentSoft,
        )
    }
}

@Composable
private fun Notice(notice: ScanNotice) {
    val kind = if (notice == ScanNotice.RESTORED) PdBannerKind.Success else PdBannerKind.Danger
    PdBanner(noticeText(notice), kind = kind)
}

@Composable
private fun noticeText(notice: ScanNotice) =
    when (notice) {
        ScanNotice.NOTHING_TO_REVIEW -> stringResource(R.string.scan_nothing_to_review)
        ScanNotice.SAVE_FAILED -> stringResource(R.string.scan_save_failed)
        ScanNotice.RESTORED -> stringResource(R.string.scan_restored)
        ScanNotice.NOTHING_TO_RESTORE -> stringResource(R.string.scan_nothing_to_restore)
    }

@Composable
private fun Review(
    state: ScanUiState,
    accountName: String?,
    onAdjust: (CardId, Int) -> Unit,
    onAccept: (CardId) -> Unit,
    onRemove: (CardId) -> Unit,
    onReplaceChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ReviewHeader(state, accountName, onReplaceChanged)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
            contentPadding = PaddingValues(horizontal = PdSpacing.space4, vertical = PdSpacing.space2),
        ) {
            items(state.review, key = { it.cardId.value }) { item -> ReviewRow(item, onAdjust, onAccept, onRemove) }
        }
        // Pinned, so saving never needs a scroll to the end of a long list.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PdSpacing.space4, vertical = PdSpacing.space3),
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        ) {
            PdButton(
                stringResource(R.string.review_discard),
                onDiscard,
                Modifier.weight(1f),
                style = PdButtonStyle.Secondary,
            )
            PdButton(
                stringResource(R.string.review_save),
                onConfirm,
                Modifier.weight(2f),
                enabled = state.review.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun ReviewHeader(
    state: ScanUiState,
    accountName: String?,
    onReplaceChanged: (Boolean) -> Unit,
) {
    val toCheck = state.review.count { it.needsReview }
    Column(
        Modifier.padding(horizontal = PdSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
    ) {
        Title(R.string.review_title, accountName)
        Text(
            stringResource(R.string.review_summary, state.review.size, toCheck),
            style = PocketDesign.typography.titleSm,
            color = PocketDesign.colors.ink,
        )
        if (toCheck > 0) PdBanner(stringResource(R.string.review_check_hint), kind = PdBannerKind.Warning)
        state.notice?.let { Notice(it) }
        if (state.looksIncomplete) {
            PdBanner(
                stringResource(R.string.review_incomplete_warning, state.review.size, state.ownedBefore),
                kind = PdBannerKind.Danger,
            )
        }
        PdSwitch(state.replaceCollection, onReplaceChanged, stringResource(R.string.review_replace))
    }
}

@Composable
private fun ReviewRow(
    item: ReviewItem,
    onAdjust: (CardId, Int) -> Unit,
    onAccept: (CardId) -> Unit,
    onRemove: (CardId) -> Unit,
) {
    PdCard(Modifier.fillMaxWidth(), contentPadding = PdSpacing.space3) {
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(
                            width = ThumbWidth,
                            height = ThumbHeight,
                        ).clip(RoundedCornerShape(ThumbCorner)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PdSpacing.space1)) {
                Text(
                    item.name,
                    style = PocketDesign.typography.titleSm,
                    color = PocketDesign.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.reason?.let {
                    Text(reasonText(it), style = PocketDesign.typography.bodySm, color = PocketDesign.colors.warning)
                }
                if (item.needsReview) {
                    PdButton(
                        "${stringResource(R.string.review_check)} · ${stringResource(R.string.review_looks_right)}",
                        { onAccept(item.cardId) },
                        style = PdButtonStyle.Tonal,
                        size = PdButtonSize.Small,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PdStepper(
                value = item.copies,
                onValueChange = { onAdjust(item.cardId, it - item.copies) },
                range = 1..MAX_COPIES,
                decrementDescription = stringResource(R.string.review_fewer, item.name),
                incrementDescription = stringResource(R.string.review_more, item.name),
            )
            Spacer(Modifier.weight(1f))
            PdIconButton(
                PdIcons.Close,
                stringResource(R.string.review_remove, item.name),
                { onRemove(item.cardId) },
                style = PdIconButtonStyle.Plain,
            )
        }
    }
}

@Composable
private fun reasonText(reason: ReviewReason) =
    stringResource(
        when (reason) {
            ReviewReason.COUNT_NEVER_READ -> R.string.review_reason_never_read
            ReviewReason.COUNT_DISAGREES -> R.string.review_reason_disagrees
            ReviewReason.LOW_CONFIDENCE -> R.string.review_reason_low_confidence
        },
    )

@Composable
private fun Saved(
    cards: Int,
    onDone: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PdEmptyState(
            title = stringResource(R.string.saved_title),
            message = pluralStringResource(R.plurals.saved_summary, cards, cards),
            primaryAction = PdAction(stringResource(R.string.saved_done), onDone),
        )
    }
}
