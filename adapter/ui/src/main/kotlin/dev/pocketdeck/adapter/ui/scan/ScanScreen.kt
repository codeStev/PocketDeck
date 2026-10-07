package dev.pocketdeck.adapter.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.collection.semanticsLabel
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.scan.ReviewReason

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    autoScrollEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onStartScan: (automatic: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanContent(
        state = state,
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
                autoScrollEnabled,
                onAutomaticChanged,
                onOpenAccessibilitySettings,
                onStartScan,
                onAutoScrollConsent,
                onRestorePrevious,
            )
        }

        ScanPhase.REVIEW -> {
            Review(state, onAdjust, onAccept, onRemove, onReplaceChanged, onConfirm, onDiscard)
        }

        ScanPhase.SAVED -> {
            Saved(state.savedCards, onDone)
        }
    }
}

@Composable
private fun Instructions(
    state: ScanUiState,
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.scan_intro))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = state.automatic, onCheckedChange = onAutomaticChanged)
            Text(stringResource(R.string.scan_automatic_label), style = MaterialTheme.typography.titleMedium)
        }
        if (state.automatic) {
            val hint = if (autoScrollEnabled) R.string.scan_automatic_on else R.string.scan_automatic_needs_service
            Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium)
        }
        val steps =
            if (state.automatic) {
                listOf(R.string.scan_step_view, R.string.scan_step_start)
            } else {
                listOf(
                    R.string.scan_step_view,
                    R.string.scan_step_start,
                    R.string.scan_step_scroll,
                    R.string.scan_step_stop,
                )
            }
        steps.forEachIndexed { index, step -> Text("${index + 1}. ${stringResource(step)}") }
        Spacer(Modifier.height(8.dp))
        state.progress?.let {
            Text(
                pluralStringResource(R.plurals.scan_running, it.distinctCards, it.distinctCards),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.notice?.let { Text(noticeText(it), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { if (state.automatic && !state.autoScrollConsented) asking = true else proceed() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (needsService) R.string.scan_open_accessibility else R.string.scan_start))
        }
        if (state.backupAvailable) {
            OutlinedButton(onClick = onRestorePrevious, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scan_restore_previous))
            }
        }
    }
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
    onAdjust: (CardId, Int) -> Unit,
    onAccept: (CardId) -> Unit,
    onRemove: (CardId) -> Unit,
    onReplaceChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.review_summary, state.review.size, state.review.count { it.needsReview }))
            if (state.review.any { it.needsReview }) {
                Text(stringResource(R.string.review_check_hint), style = MaterialTheme.typography.bodySmall)
            }
            state.notice?.let { Text(noticeText(it), color = MaterialTheme.colorScheme.error) }
            if (state.looksIncomplete) {
                Text(
                    stringResource(R.string.review_incomplete_warning, state.review.size, state.ownedBefore),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = state.replaceCollection, onCheckedChange = onReplaceChanged)
                Text(stringResource(R.string.review_replace), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    enabled = state.review.isNotEmpty(),
                ) { Text(stringResource(R.string.review_save)) }
                OutlinedButton(onClick = onDiscard) { Text(stringResource(R.string.review_discard)) }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(state.review, key = { it.cardId.value }) { item -> ReviewRow(item, onAdjust, onAccept, onRemove) }
        }
    }
}

@Composable
private fun ReviewRow(
    item: ReviewItem,
    onAdjust: (CardId, Int) -> Unit,
    onAccept: (CardId) -> Unit,
    onRemove: (CardId) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 56.dp, height = 78.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                item.reason?.let {
                    Text(
                        reasonText(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (item.needsReview) {
                    TextButton(onClick = { onAccept(item.cardId) }) {
                        Text(
                            "${stringResource(R.string.review_check)} · ${stringResource(R.string.review_looks_right)}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            FilledTonalIconButton(onClick = { onAdjust(item.cardId, -1) }, enabled = item.copies > 1) { Text("−") }
            Text("${item.copies}", style = MaterialTheme.typography.titleMedium)
            FilledTonalIconButton(onClick = { onAdjust(item.cardId, 1) }) { Text("+") }
            TextButton(onClick = { onRemove(item.cardId) }) {
                Text("✕", modifier = Modifier.semanticsLabel(stringResource(R.string.review_remove, item.name)))
            }
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.headlineSmall)
        Text(pluralStringResource(R.plurals.saved_summary, cards, cards))
        Button(onClick = onDone) { Text(stringResource(R.string.saved_done)) }
    }
}
