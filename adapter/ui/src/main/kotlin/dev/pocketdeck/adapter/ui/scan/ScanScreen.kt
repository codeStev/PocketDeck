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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onStartScan: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanContent(
        state = state,
        onStartScan = onStartScan,
        onAdjust = viewModel::onAdjust,
        onAccept = viewModel::onAccept,
        onRemove = viewModel::onRemove,
        onConfirm = viewModel::onConfirm,
        onDiscard = viewModel::onDiscard,
        onDone = viewModel::onDone,
    )
}

@Composable
internal fun ScanContent(
    state: ScanUiState,
    onStartScan: () -> Unit,
    onAdjust: (CardId, Int) -> Unit,
    onAccept: (CardId) -> Unit,
    onRemove: (CardId) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onDone: () -> Unit,
) {
    when (state.phase) {
        ScanPhase.IDLE, ScanPhase.SCANNING -> Instructions(state, onStartScan)
        ScanPhase.REVIEW -> Review(state, onAdjust, onAccept, onRemove, onConfirm, onDiscard)
        ScanPhase.SAVED -> Saved(state.savedCards, onDone)
    }
}

@Composable
private fun Instructions(
    state: ScanUiState,
    onStartScan: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.scan_intro))
        listOf(R.string.scan_step_view, R.string.scan_step_start, R.string.scan_step_scroll, R.string.scan_step_stop)
            .forEachIndexed { index, step -> Text("${index + 1}. ${stringResource(step)}") }
        Spacer(Modifier.height(8.dp))
        state.progress?.let {
            Text(
                pluralStringResource(R.plurals.scan_running, it.distinctCards, it.distinctCards),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.notice?.let { Text(noticeText(it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_start)) }
    }
}

@Composable
private fun noticeText(notice: ScanNotice) =
    when (notice) {
        ScanNotice.NOTHING_TO_REVIEW -> stringResource(R.string.scan_nothing_to_review)
        ScanNotice.SAVE_FAILED -> stringResource(R.string.scan_save_failed)
    }

@Composable
private fun Review(
    state: ScanUiState,
    onAdjust: (CardId, Int) -> Unit,
    onAccept: (CardId) -> Unit,
    onRemove: (CardId) -> Unit,
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
