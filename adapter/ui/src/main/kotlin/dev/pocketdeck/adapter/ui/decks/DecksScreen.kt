package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

@Composable
fun DecksScreen(viewModel: DecksViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DecksContent(state, viewModel::onRefresh, viewModel::onShowCode, viewModel::onCloseCode)
}

@Composable
internal fun DecksContent(
    state: DecksUiState,
    onRefresh: () -> Unit,
    onShowCode: (String) -> Unit,
    onCloseCode: () -> Unit,
) {
    state.code?.let { DeckCodeDialog(it, onCloseCode) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state, onRefresh) }
        items(state.items, key = { it.key }) { item -> DeckCard(item, onShowCode) }
    }
}

@Composable
private fun Header(
    state: DecksUiState,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.decks_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.decks_intro), style = MaterialTheme.typography.bodyMedium)
        state.notice?.let { Text(noticeText(it), color = MaterialTheme.colorScheme.error) }
        if (state.items.isEmpty() && !state.refreshing) Text(stringResource(R.string.decks_empty))
        Button(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.refreshing) R.string.decks_refreshing else R.string.decks_refresh))
        }
    }
}

@Composable
private fun noticeText(notice: DecksNotice) =
    stringResource(
        when (notice) {
            DecksNotice.NOT_REACHABLE -> R.string.decks_not_reachable
            DecksNotice.NO_DECKLISTS -> R.string.decks_no_decklists
        },
    )

@Composable
private fun DeckCard(
    item: DeckItem,
    onShowCode: (String) -> Unit,
) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(originText(item.origin), style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { item.ownedPercent / PERCENT_FULL }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.decks_owned, item.ownedPercent))
            if (item.substitutes.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.decks_substituted, item.substituteCopies, item.substituteCopies),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (item.missingCopies == 0) {
                Text(stringResource(R.string.decks_buildable), color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    pluralStringResource(R.plurals.decks_missing, item.missingCopies, item.missingCopies),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) {
                item.substitutes.forEach { SubstituteText(it) }
                item.cards.forEach { CardLine(it) }
            }
            OutlinedButton(onClick = { onShowCode(item.key) }) { Text(stringResource(R.string.decks_code_show)) }
        }
    }
}

@Composable
private fun originText(origin: SuggestionOrigin) =
    stringResource(
        when (origin) {
            SuggestionOrigin.META -> R.string.decks_origin_meta
            SuggestionOrigin.HEURISTIC -> R.string.decks_origin_heuristic
        },
    )

@Composable
private fun SubstituteText(line: SubstituteLine) {
    Text(
        stringResource(R.string.decks_substitute_line, line.copies, line.replacement, line.original),
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.bodyMedium,
    )
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
