package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.domain.deck.SuggestionOrigin

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
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            onRenameSaved = viewModel::onRenameSavedRequested,
            onDeleteSaved = viewModel::onDeleteSavedRequested,
            onRenamed = viewModel::onSavedRenamed,
            onDeleteConfirmed = viewModel::onSavedDeleteConfirmed,
            onSavedDialogDismissed = viewModel::onSavedDialogDismissed,
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
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = kind.ordinal) {
            DeckKind.entries.forEach { entry ->
                Tab(
                    selected = kind == entry,
                    onClick = { kind = entry },
                    text = { Text(stringResource(entry.tabLabel())) },
                )
            }
        }
        val items = if (kind == DeckKind.SAVED) state.saved else state.items.ofKind(kind)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(kind, state, items.isEmpty(), actions) }
            items(items, key = { it.key }) { item -> DeckCard(item, kind, actions) }
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
    actions: DecksActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (kind) {
            DeckKind.TOURNAMENT -> TournamentHeader(state, empty, actions)
            DeckKind.GENERATED -> GeneratedHeader(state, empty, actions)
            DeckKind.SAVED -> SavedHeader(empty)
        }
        state.notice?.takeIf { DeckKind.of(it) == kind }?.let {
            Text(noticeText(it, state.noticeDetail), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TournamentHeader(
    state: DecksUiState,
    empty: Boolean,
    actions: DecksActions,
) {
    Text(stringResource(R.string.decks_intro_tournament), style = MaterialTheme.typography.bodyMedium)
    if (empty && !state.refreshing) Text(stringResource(R.string.decks_empty_tournament))
    OutlinedButton(onClick = actions.onRefresh, enabled = !state.refreshing, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.refreshing) R.string.decks_refreshing else R.string.decks_refresh))
    }
}

@Composable
private fun SavedHeader(empty: Boolean) {
    Text(stringResource(R.string.decks_intro_saved), style = MaterialTheme.typography.bodyMedium)
    if (empty) Text(stringResource(R.string.decks_empty_saved))
}

@Composable
private fun GeneratedHeader(
    state: DecksUiState,
    empty: Boolean,
    actions: DecksActions,
) {
    Text(stringResource(R.string.decks_intro_generated), style = MaterialTheme.typography.bodyMedium)
    if (empty && !state.building && !state.asking) Text(stringResource(R.string.decks_empty_generated))
    Button(
        onClick = actions.onOpenBuilder,
        enabled = !(state.building || state.asking),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val label =
            when {
                state.asking -> R.string.decks_asking
                state.building -> R.string.decks_building
                else -> R.string.decks_build
            }
        Text(stringResource(label))
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
private fun DeckCard(
    item: DeckItem,
    kind: DeckKind,
    actions: DecksActions,
) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = containerColor(item.origin)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title =
                if (item.substitutes.isEmpty()) {
                    item.title
                } else {
                    "${item.title} · ${stringResource(R.string.decks_with_substitutes)}"
                }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(originText(item.origin), style = MaterialTheme.typography.labelMedium)
            item.rationale?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            LinearProgressIndicator(progress = { item.ownedPercent / PERCENT_FULL }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.decks_owned, item.ownedPercent))
            if (item.substitutes.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.decks_substituted, item.substituteCopies, item.substituteCopies),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                item.substitutes.forEach { SubstituteText(it) }
            }
            if (item.missingCopies == 0) {
                Text(stringResource(R.string.decks_buildable), color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    pluralStringResource(R.plurals.decks_missing, item.missingCopies, item.missingCopies),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) item.cards.forEach { CardLine(it) }
            val showCode =
                if (item.substitutes.isEmpty()) R.string.decks_code_show else R.string.decks_code_show_substituted
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.onShowCode(item.key) }) { Text(stringResource(showCode)) }
                if (kind != DeckKind.SAVED) {
                    OutlinedButton(onClick = { actions.onSave(item.key) }) { Text(stringResource(R.string.decks_save)) }
                }
            }
            if (kind == DeckKind.SAVED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { actions.onRenameSaved(item) },
                    ) { Text(stringResource(R.string.decks_rename)) }
                    OutlinedButton(
                        onClick = { actions.onDeleteSaved(item) },
                    ) { Text(stringResource(R.string.decks_delete)) }
                }
            }
        }
    }
}

/** Tournament decks keep the plain card; what the builder or an AI made is tinted, so the two are never mixed up. */
@Composable
private fun containerColor(origin: SuggestionOrigin) =
    when (origin) {
        SuggestionOrigin.META -> MaterialTheme.colorScheme.surfaceVariant
        SuggestionOrigin.HEURISTIC -> MaterialTheme.colorScheme.tertiaryContainer
        SuggestionOrigin.AI -> MaterialTheme.colorScheme.secondaryContainer
    }

@Composable
private fun originText(origin: SuggestionOrigin) =
    stringResource(
        when (origin) {
            SuggestionOrigin.META -> R.string.decks_origin_meta
            SuggestionOrigin.HEURISTIC -> R.string.decks_origin_heuristic
            SuggestionOrigin.AI -> R.string.decks_origin_ai
        },
    )

@Composable
private fun SubstituteText(line: SubstituteLine) {
    val effects = line.effects.map { stringResource(it.label()) }.joinToString(", ")
    Column {
        Text(
            stringResource(R.string.decks_substitute_line, line.copies, line.replacement, line.original),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (effects.isNotEmpty()) {
            Text(
                stringResource(R.string.decks_substitute_alike, effects),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CardLine(line: DeckCardLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.decks_card_line, line.copies, line.name))
        if (line.missingCopies > 0) {
            Text(
                stringResource(R.string.decks_card_missing, line.missingCopies),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private const val PERCENT_FULL = 100f

@Composable
private fun SavedDeckDialogs(
    dialog: SavedDeckDialog,
    actions: DecksActions,
) {
    when (dialog) {
        is SavedDeckDialog.Rename -> {
            var title by rememberSaveable(dialog.id) { mutableStateOf(dialog.title) }
            AlertDialog(
                onDismissRequest = actions.onSavedDialogDismissed,
                title = { Text(stringResource(R.string.decks_rename)) },
                text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
                confirmButton = {
                    TextButton(
                        onClick = { actions.onRenamed(title) },
                    ) { Text(stringResource(R.string.decks_rename_save)) }
                },
                dismissButton = {
                    TextButton(onClick = actions.onSavedDialogDismissed) { Text(stringResource(R.string.decks_cancel)) }
                },
            )
        }

        is SavedDeckDialog.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = actions.onSavedDialogDismissed,
                title = { Text(stringResource(R.string.decks_delete_title, dialog.title)) },
                confirmButton = {
                    TextButton(
                        onClick = actions.onDeleteConfirmed,
                    ) { Text(stringResource(R.string.decks_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = actions.onSavedDialogDismissed) { Text(stringResource(R.string.decks_cancel)) }
                },
            )
        }
    }
}
