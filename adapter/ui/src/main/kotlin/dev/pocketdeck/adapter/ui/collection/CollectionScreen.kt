package dev.pocketdeck.adapter.ui.collection

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
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
import dev.codestev.pocketdesign.components.PdArtTile
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdFilterChip
import dev.codestev.pocketdesign.components.PdIconButton
import dev.codestev.pocketdesign.components.PdSpinner
import dev.codestev.pocketdesign.components.PdStepper
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.theme.tintKey
import dev.pocketdeck.adapter.ui.thumbnailOf
import dev.pocketdeck.application.catalog.CatalogSyncFailure
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType

private const val GRID_COLUMNS = 3
private const val MAX_COPIES = 99
private val DetailArtWidth = 160.dp

@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onOpenScan: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    CollectionContent(
        state = state,
        queryText = queryText,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onAdjust = viewModel::adjust,
        onSync = viewModel::sync,
        onOpenScan = onOpenScan,
    )
}

@Composable
internal fun CollectionContent(
    state: CollectionUiState,
    queryText: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CollectionFilter) -> Unit,
    onAdjust: (CardId, Int) -> Unit,
    onSync: () -> Unit,
    onOpenScan: () -> Unit,
) {
    // Kept by id, so the dialog shows the live number of copies while the player changes them.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    state.items.firstOrNull { it.id.value == openId }?.let { CardDialog(it, onAdjust) { openId = null } }
    Column(Modifier.fillMaxSize().padding(horizontal = PdSpacing.space4).animateContentSize()) {
        Title(state.sync, onSync)
        Filters(state, queryText, onQueryChange, onFilterChange)
        SyncBanner(state.sync, onSync)
        if (state.totalOwned == 0 && state.catalogSize > 0 && state.sync == SyncState.Idle) EmptyHint(onOpenScan)
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
                CardGrid(state.items) { openId = it.id.value }
            }
        }
    }
}

/** A new player sees 4,000 faded cards and no hint: say that scanning is how the collection gets filled. */
@Composable
private fun EmptyHint(onOpenScan: () -> Unit) {
    PdBanner(
        stringResource(R.string.collection_empty_hint),
        kind = PdBannerKind.Info,
        actionLabel = stringResource(R.string.collection_empty_action),
        onAction = onOpenScan,
    )
}

@Composable
private fun Title(
    sync: SyncState,
    onSync: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = PdSpacing.space2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.collection_title),
            style = PocketDesign.typography.titleLg,
            color = PocketDesign.colors.ink,
            modifier = Modifier.weight(1f),
        )
        PdIconButton(
            PdIcons.Refresh,
            stringResource(R.string.collection_sync),
            onSync,
            loading = sync == SyncState.Syncing,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(
    state: CollectionUiState,
    queryText: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CollectionFilter) -> Unit,
) {
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    if (panelOpen) CollectionFilterPanel(state.filter, state.options, onFilterChange) { panelOpen = false }
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        PdTextField(
            queryText,
            onQueryChange,
            Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.collection_search_hint),
            leadingIcon = PdIcons.Search,
        )
        // Wraps: at the largest system font the counts do not fit next to the chip and move below it.
        FlowRow(
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            PdFilterChip(
                stringResource(R.string.collection_owned_only),
                state.filter.ownedOnly,
                { onFilterChange(state.filter.copy(ownedOnly = it)) },
            )
            PdFilterChip(filtersLabel(state.filter.chosenCount), state.filter.chosenCount > 0, { panelOpen = true })
            Text(
                stringResource(R.string.collection_stats, state.uniqueOwned, state.catalogSize, state.totalOwned),
                style = PocketDesign.typography.bodySm,
                color = PocketDesign.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun filtersLabel(chosen: Int) =
    if (chosen ==
        0
    ) {
        stringResource(R.string.collection_filters)
    } else {
        stringResource(R.string.collection_filters_count, chosen)
    }

@Composable
private fun SyncBanner(
    sync: SyncState,
    onRetry: () -> Unit,
) {
    when (sync) {
        SyncState.Idle -> {
            // Nothing to report.
        }

        SyncState.Syncing -> {
            PdBanner(stringResource(R.string.collection_syncing), kind = PdBannerKind.Info)
        }

        is SyncState.Failed -> {
            PdBanner(
                failureText(sync.failure),
                kind = PdBannerKind.Danger,
                actionLabel = stringResource(R.string.collection_retry),
                onAction = onRetry,
            )
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
    onOpen: (CardItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(vertical = PdSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space4),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id.value }) { item -> CardTile(item, Modifier.animateItem()) { onOpen(item) } }
    }
}

/** The art is the hero; the name sits below, and the details wait in the dialog a tap opens. */
@Composable
private fun CardTile(
    item: CardItem,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PdSpacing.space1)) {
        PdArtTile(
            tint = PocketDesign.colors.tint(item.detail.tintKey()),
            contentDescription = tileDescription(item),
            copies = item.copies,
            owned = item.copies > 0,
            onClick = onOpen,
        ) {
            Art(item, small = true)
        }
        Text(
            item.name,
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun tileDescription(item: CardItem) =
    if (item.copies >
        0
    ) {
        pluralStringResource(R.plurals.collection_tile_owned, item.copies, item.name, item.copies)
    } else {
        item.name
    }

@Composable
private fun Art(
    item: CardItem,
    small: Boolean = false,
) {
    AsyncImage(
        model = if (small) thumbnailOf(item.artworkUrl) else item.artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun CardDetail.tintKey() =
    when (this) {
        is CardDetail.OfPokemon -> energy.tintKey()
        is CardDetail.OfTrainer -> EnergyType.COLORLESS.tintKey()
    }

@Composable
private fun CardDialog(
    item: CardItem,
    onAdjust: (CardId, Int) -> Unit,
    onClose: () -> Unit,
) {
    PdDialog(
        title = item.name,
        onDismissRequest = onClose,
        confirmText = stringResource(R.string.collection_done),
        onConfirm = onClose,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PdArtTile(
                tint = PocketDesign.colors.tint(item.detail.tintKey()),
                contentDescription = item.name,
                modifier = Modifier.width(DetailArtWidth),
                owned = item.copies > 0,
            ) {
                Art(item)
            }
        }
        Text(
            detailText(item.detail),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.inkMuted,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PdStepper(
                value = item.copies,
                onValueChange = { onAdjust(item.id, it - item.copies) },
                range = 0..MAX_COPIES,
                decrementDescription = stringResource(R.string.collection_remove_copy, item.name),
                incrementDescription = stringResource(R.string.collection_add_copy, item.name),
            )
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PdSpinner(size = 40.dp)
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = PocketDesign.typography.body, color = PocketDesign.colors.inkMuted)
    }
}
