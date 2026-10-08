package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.components.FullScreenPanel

/** Lets the player choose what a deck is built around, or leave it to the builder. */
@Composable
internal fun BuilderDialog(
    centrepieces: List<CentrepieceItem>,
    aiConfigured: Boolean,
    actions: DecksActions,
) {
    val onBuild = actions.onBuild
    FullScreenPanel(stringResource(R.string.decks_build_title), actions.onCloseBuilder) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(R.string.decks_build_hint), style = MaterialTheme.typography.bodyMedium) }
            item {
                Button(onClick = { onBuild(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.decks_build_best))
                }
            }
            item { HorizontalDivider() }
            item { AskAiSection(aiConfigured, actions) }
            item { HorizontalDivider() }
            items(centrepieces, key = { it.cardId }) { item -> CentrepieceRow(item, onBuild) }
        }
    }
}

/** Asks an AI for a deck, with a wish in the player's own words; needs a key for one of the AI services. */
@Composable
private fun AskAiSection(
    aiConfigured: Boolean,
    actions: DecksActions,
) {
    var wish by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.decks_ai_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = wish,
            onValueChange = { wish = it },
            label = { Text(stringResource(R.string.decks_ai_wish)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (aiConfigured) {
                Button(onClick = { actions.onAskAi(wish) }) { Text(stringResource(R.string.decks_ai_ask)) }
            } else {
                Button(onClick = actions.onOpenSettings) { Text(stringResource(R.string.decks_ai_set_up)) }
            }
        }
    }
}

@Composable
private fun CentrepieceRow(
    item: CentrepieceItem,
    onBuild: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onBuild(item.cardId) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 40.dp, height = 56.dp),
        )
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
    }
}
