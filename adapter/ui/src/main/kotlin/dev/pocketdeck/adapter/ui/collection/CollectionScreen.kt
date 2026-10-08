package dev.pocketdeck.adapter.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.catalog.CatalogSyncFailure
import dev.pocketdeck.domain.card.CardId

private const val CARD_ASPECT_RATIO = 0.716f
private const val UNOWNED_ALPHA = 0.35f
private const val DETAIL_LINES = 2

@Composable
fun CollectionScreen(viewModel: CollectionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    CollectionContent(
        state = state,
        queryText = queryText,
        onQueryChange = viewModel::onQueryChange,
        onOwnedOnlyChange = viewModel::onOwnedOnlyChange,
        onAdjust = viewModel::adjust,
        onSync = viewModel::sync,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionContent(
    state: CollectionUiState,
    queryText: String,
    onQueryChange: (String) -> Unit,
    onOwnedOnlyChange: (Boolean) -> Unit,
    onAdjust: (CardId, Int) -> Unit,
    onSync: () -> Unit,
) {
    // The app's own Scaffold already clears the status bar and the account row, so this one must not add it again.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.collection_title)) },
                actions = {
                    TextButton(onClick = onSync, enabled = state.sync != SyncState.Syncing) {
                        Text(stringResource(R.string.collection_sync))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Filters(state, queryText, onQueryChange, onOwnedOnlyChange)
            SyncBanner(state.sync, onSync)
            when {
                state.items.isEmpty() && state.sync == SyncState.Syncing -> {
                    CenteredProgress()
                }

                state.items.isEmpty() && state.catalogSize > 0 -> {
                    CenteredText(
                        stringResource(R.string.collection_no_results),
                    )
                }

                else -> {
                    CardGrid(state.items, onAdjust)
                }
            }
        }
    }
}

@Composable
private fun Filters(
    state: CollectionUiState,
    queryText: String,
    onQueryChange: (String) -> Unit,
    onOwnedOnlyChange: (Boolean) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = queryText,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.collection_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = state.ownedOnly,
                onClick = { onOwnedOnlyChange(!state.ownedOnly) },
                label = { Text(stringResource(R.string.collection_owned_only)) },
            )
            Text(
                text =
                    stringResource(
                        R.string.collection_stats,
                        state.uniqueOwned,
                        state.catalogSize,
                        state.totalOwned,
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SyncBanner(
    sync: SyncState,
    onRetry: () -> Unit,
) {
    when (sync) {
        SyncState.Idle -> {
            return
        }

        SyncState.Syncing -> {
            Text(
                stringResource(R.string.collection_syncing),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        is SyncState.Failed -> {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = failureText(sync.failure),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.collection_retry)) }
            }
        }
    }
}

@Composable
private fun failureText(failure: CatalogSyncFailure): String =
    when (failure) {
        is CatalogSyncFailure.SourceNotReachable -> stringResource(R.string.collection_sync_unreachable, failure.reason)
        CatalogSyncFailure.EmptyCatalog -> stringResource(R.string.collection_sync_empty)
    }

@Composable
private fun CardGrid(
    items: List<CardItem>,
    onAdjust: (CardId, Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id.value }) { item -> CardTile(item, onAdjust) }
    }
}

@Composable
private fun CardTile(
    item: CardItem,
    onAdjust: (CardId, Int) -> Unit,
) {
    Card {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT_RATIO)) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).alpha(
                            if (item.copies >
                                0
                            ) {
                                1f
                            } else {
                                UNOWNED_ALPHA
                            },
                        ),
                )
                if (item.copies > 0) CopyBadge(item.copies, Modifier.align(Alignment.TopEnd))
            }
            Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detailText(item.detail),
                style = MaterialTheme.typography.labelSmall,
                minLines = DETAIL_LINES,
                maxLines = DETAIL_LINES,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalIconButton(onClick = { onAdjust(item.id, -1) }, enabled = item.copies > 0) {
                    Text(
                        "−",
                        modifier = Modifier.semanticsLabel(stringResource(R.string.collection_remove_copy, item.name)),
                    )
                }
                FilledTonalIconButton(onClick = { onAdjust(item.id, 1) }) {
                    Text(
                        "+",
                        modifier = Modifier.semanticsLabel(stringResource(R.string.collection_add_copy, item.name)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyBadge(
    copies: Int,
    modifier: Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(topEnd = 6.dp, bottomStart = 8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.collection_copies, copies),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}
