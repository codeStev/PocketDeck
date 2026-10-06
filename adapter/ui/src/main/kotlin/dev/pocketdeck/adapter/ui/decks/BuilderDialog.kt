package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.pocketdeck.adapter.ui.R

/** Lets the player choose what a deck is built around, or leave it to the builder. */
@Composable
internal fun BuilderDialog(
    centrepieces: List<CentrepieceItem>,
    onBuild: (String?) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.decks_build_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.decks_build_hint), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.decks_build_best),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onBuild(null) }.padding(vertical = 8.dp),
                )
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = LIST_HEIGHT)) {
                    items(centrepieces, key = { it.cardId }) { item -> CentrepieceRow(item, onBuild) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.decks_code_close)) } },
    )
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

private val LIST_HEIGHT = 360.dp
