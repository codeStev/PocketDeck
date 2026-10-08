package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdIconButton
import dev.codestev.pocketdesign.components.PdSegmentedTabs
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.chase.ChaseDialog
import dev.pocketdeck.adapter.ui.chase.ChaseViewModel

/** What the player can do on the decks screen. */
internal class DecksActions(
    val onRefresh: () -> Unit,
    val onShowCode: (String) -> Unit,
    val onCloseCode: () -> Unit,
    val onOpenBuilder: () -> Unit,
    val onCloseBuilder: () -> Unit,
    val onBuild: (String?) -> Unit,
    val onAskAi: (String?) -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenChase: () -> Unit,
    val onShowOpening: (Int) -> Unit,
    val onSave: (String) -> Unit,
    val onRenameSaved: (DeckItem) -> Unit,
    val onDeleteSaved: (DeckItem) -> Unit,
    val onRenamed: (String) -> Unit,
    val onDeleteConfirmed: () -> Unit,
    val onSavedDialogDismissed: () -> Unit,
)

@Composable
fun DecksScreen(
    viewModel: DecksViewModel,
    chaseViewModel: ChaseViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var chasing by rememberSaveable { mutableStateOf(false) }
    if (chasing) ChaseDialog(chaseViewModel) { chasing = false }
    DecksContent(
        state,
        DecksActions(
            onRefresh = viewModel::onRefresh,
            onShowCode = viewModel::onShowCode,
            onCloseCode = viewModel::onCloseCode,
            onOpenBuilder = viewModel::onOpenBuilder,
            onCloseBuilder = viewModel::onCloseBuilder,
            onBuild = viewModel::onBuild,
            onAskAi = viewModel::onAskAi,
            onSave = viewModel::onSave,
            onShowOpening = viewModel::onShowOpening,
            onRenameSaved = viewModel::onRenameSavedRequested,
            onDeleteSaved = viewModel::onDeleteSavedRequested,
            onRenamed = viewModel::onSavedRenamed,
            onDeleteConfirmed = viewModel::onSavedDeleteConfirmed,
            onSavedDialogDismissed = viewModel::onSavedDialogDismissed,
            onOpenChase = { chasing = true },
            onOpenSettings = {
                viewModel.onCloseBuilder()
                onOpenSettings()
            },
        ),
    )
}

@Composable
internal fun DecksContent(
    state: DecksUiState,
    actions: DecksActions,
) {
    var kind by rememberSaveable { mutableStateOf(DeckKind.TOURNAMENT) }
    state.code?.let { DeckCodeDialog(it, actions.onCloseCode) }
    state.centrepieces?.let { BuilderDialog(it, state.aiConfigured, actions) }
    state.savedDialog?.let { SavedDeckDialogs(it, actions) }
    val labels = DeckKind.entries.map { stringResource(it.tabLabel()) }
    Column(Modifier.fillMaxSize()) {
        PdSegmentedTabs(
            labels,
            kind.ordinal,
            { kind = DeckKind.entries[it] },
            Modifier.fillMaxWidth().padding(horizontal = PdSpacing.space4),
        )
        ActionBar(kind, state, actions)
        val items = if (kind == DeckKind.SAVED) state.saved else state.items.ofKind(kind)
        val listState = rememberLazyListState()
        // A deck the player just asked for is shown, not left for them to find: go to its tab and to the top.
        LaunchedEffect(state.newDeck?.token) {
            if (state.newDeck != null) {
                kind = DeckKind.GENERATED
                listState.scrollToItem(0)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = PdSpacing.space4, vertical = PdSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space4),
        ) {
            item { Header(kind, state, items.isEmpty()) }
            items(items, key = { it.key }) { item ->
                DeckCard(
                    item,
                    kind,
                    DeckCardState(state.openings[item.deckId], item.deckId in state.savedDeckIds),
                    actions,
                )
            }
        }
    }
}

private fun DeckKind.tabLabel() =
    when (this) {
        DeckKind.TOURNAMENT -> R.string.decks_tab_tournament
        DeckKind.GENERATED -> R.string.decks_tab_generated
        DeckKind.SAVED -> R.string.decks_tab_saved
    }

@Composable
private fun Header(
    kind: DeckKind,
    state: DecksUiState,
    empty: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        when (kind) {
            DeckKind.TOURNAMENT -> {
                Intro(R.string.decks_intro_tournament)
                if (empty && state.loaded && !state.refreshing) Hint(R.string.decks_empty_tournament)
            }

            DeckKind.GENERATED -> {
                Intro(R.string.decks_intro_generated)
                if (empty && state.loaded && !state.building && !state.asking) Hint(R.string.decks_empty_generated)
            }

            DeckKind.SAVED -> {
                Intro(R.string.decks_intro_saved)
                if (empty) Hint(R.string.decks_empty_saved)
            }
        }
        state.newDeck?.takeIf { kind == DeckKind.GENERATED }?.let {
            PdBanner(stringResource(R.string.decks_just_built, it.title), kind = PdBannerKind.Success)
        }
        state.notice?.takeIf { DeckKind.of(it) == kind }?.let {
            val severity = if (it == DecksNotice.RATE_LIMITED) PdBannerKind.Warning else PdBannerKind.Danger
            PdBanner(noticeText(it, state.noticeDetail), kind = severity)
        }
    }
}

@Composable
private fun Intro(text: Int) =
    Text(stringResource(text), style = PocketDesign.typography.body, color = PocketDesign.colors.inkMuted)

@Composable
private fun Hint(text: Int) = PdBanner(stringResource(text), kind = PdBannerKind.Info)

/** The actions of a tab stay in view while its list scrolls. */
@Composable
private fun ActionBar(
    kind: DeckKind,
    state: DecksUiState,
    actions: DecksActions,
) {
    val modifier = Modifier.fillMaxWidth().padding(horizontal = PdSpacing.space4, vertical = PdSpacing.space3)
    when (kind) {
        DeckKind.TOURNAMENT -> {
            Row(
                modifier,
                horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PdButton(
                    stringResource(R.string.chase_open),
                    actions.onOpenChase,
                    Modifier.weight(1f),
                    style = PdButtonStyle.Secondary,
                    leadingIcon = PdIcons.Target,
                )
                // An icon is enough: the tab already says "tournament decks". The label is for screen readers.
                PdIconButton(
                    PdIcons.Refresh,
                    stringResource(if (state.refreshing) R.string.decks_refreshing else R.string.decks_refresh),
                    actions.onRefresh,
                    loading = state.refreshing,
                )
            }
        }

        DeckKind.GENERATED -> {
            val label =
                when {
                    state.asking -> R.string.decks_asking
                    state.building -> R.string.decks_building
                    else -> R.string.decks_build
                }
            PdButton(
                stringResource(label),
                actions.onOpenBuilder,
                modifier,
                enabled = !(state.building || state.asking),
                loading = state.building || state.asking,
                leadingIcon = PdIcons.Plus,
            )
        }

        DeckKind.SAVED -> {
            // Kept decks only have per-deck actions.
        }
    }
}

@Composable
private fun noticeText(
    notice: DecksNotice,
    detail: String?,
): String {
    val more = detail.orEmpty()
    return when (notice) {
        DecksNotice.NOT_REACHABLE -> stringResource(R.string.decks_not_reachable)
        DecksNotice.RATE_LIMITED -> stringResource(R.string.decks_rate_limited)
        DecksNotice.NO_DECKLISTS -> stringResource(R.string.decks_no_decklists)
        DecksNotice.BUILD_FAILED -> stringResource(R.string.decks_build_failed)
        DecksNotice.AI_NEEDS_KEY -> stringResource(R.string.decks_ai_needs_key)
        DecksNotice.AI_INVALID_KEY -> stringResource(R.string.decks_ai_invalid_key)
        DecksNotice.AI_BUSY -> stringResource(R.string.decks_ai_busy, more)
        DecksNotice.AI_UNREACHABLE -> stringResource(R.string.decks_ai_unreachable, more)
        DecksNotice.AI_REJECTED -> stringResource(R.string.decks_ai_rejected, more)
        DecksNotice.AI_UNUSABLE -> stringResource(R.string.decks_ai_unusable, more)
        DecksNotice.AI_NO_VALID_DECK -> stringResource(R.string.decks_ai_no_valid_deck, more)
    }
}

@Composable
private fun SavedDeckDialogs(
    dialog: SavedDeckDialog,
    actions: DecksActions,
) {
    when (dialog) {
        is SavedDeckDialog.Rename -> {
            var title by rememberSaveable(dialog.id) { mutableStateOf(dialog.title) }
            PdDialog(
                title = stringResource(R.string.decks_rename),
                onDismissRequest = actions.onSavedDialogDismissed,
                confirmText = stringResource(R.string.decks_rename_save),
                onConfirm = { actions.onRenamed(title) },
                dismissText = stringResource(R.string.decks_cancel),
            ) {
                PdTextField(title, { title = it }, Modifier.fillMaxWidth())
            }
        }

        is SavedDeckDialog.ConfirmDelete -> {
            PdDialog(
                title = stringResource(R.string.decks_delete_title, dialog.title),
                onDismissRequest = actions.onSavedDialogDismissed,
                confirmText = stringResource(R.string.decks_delete),
                onConfirm = actions.onDeleteConfirmed,
                dismissText = stringResource(R.string.decks_cancel),
                destructive = true,
            ) {}
        }
    }
}
