package dev.pocketdeck.adapter.ui.chase

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.adapter.ui.components.FullScreenPanel

/** Tells the player which cards to get next to finish tournament decks, and which packs hold them. */
@Composable
fun ChaseDialog(
    viewModel: ChaseViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FullScreenPanel(stringResource(R.string.chase_title), onClose) { ChaseContent(state) }
}

@Composable
internal fun ChaseContent(state: ChaseUiState) {
    if (state.cards.isEmpty()) {
        Text(
            stringResource(R.string.chase_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(stringResource(R.string.chase_hint), style = MaterialTheme.typography.bodyMedium) }
        item { SectionTitle(R.string.chase_cards) }
        items(state.cards, key = { "card-${it.key}" }) { CardRow(it) }
        item { HorizontalDivider() }
        item { SectionTitle(R.string.chase_packs) }
        items(state.packs, key = { "pack-${it.name}" }) { PackRow(it) }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CardRow(item: ChaseCardItem) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 40.dp, height = 56.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(effectText(item), style = MaterialTheme.typography.bodySmall)
            Text(packsText(item.packs), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun effectText(item: ChaseCardItem): String =
    if (item.decksFinished > 0) {
        pluralStringResource(R.plurals.chase_finishes, item.decksFinished, item.decksFinished)
    } else {
        pluralStringResource(R.plurals.chase_helps, item.decksHelped, item.decksHelped)
    }

@Composable
private fun packsText(packs: List<String>): String =
    if (packs.isEmpty()) stringResource(R.string.chase_no_pack) else packs.joinToString(", ")

@Composable
private fun PackRow(item: ChasePackItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
        val line =
            pluralStringResource(
                R.plurals.chase_pack_line,
                item.cardCount,
                item.cardCount,
                item.cardNames.joinToString(", "),
            )
        Text(line, style = MaterialTheme.typography.bodySmall)
    }
}
