package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pocketdeck.adapter.ui.R
import kotlinx.coroutines.launch

@Composable
internal fun DeckCodeDialog(
    code: DeckCodeUi,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(code.title) },
        text = {
            when (code) {
                is DeckCodeUi.Ready -> ReadyContent(code)
                is DeckCodeUi.Unavailable -> UnavailableContent(code)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.decks_code_close)) } },
    )
}

@Composable
private fun ReadyContent(code: DeckCodeUi.Ready) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val bitmap = remember(code.text) { QrMatrix.of(code.text).toBitmap() }
    var saved by remember(code.text) { mutableStateOf<Boolean?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.decks_code_image, code.title),
            modifier = Modifier.fillMaxWidth().size(QR_SIZE),
        )
        if (code.substitutes.isNotEmpty()) {
            Text(stringResource(R.string.decks_code_contains), style = MaterialTheme.typography.titleSmall)
            code.substitutes.forEach {
                Text(stringResource(R.string.decks_substitute_line, it.copies, it.replacement, it.original))
            }
        }
        Text(stringResource(R.string.decks_code_howto), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { saved = bitmap.saveToGallery(context, fileName(code.title)) }) {
                Text(stringResource(R.string.decks_code_save))
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            android.content.ClipData
                                .newPlainText(code.title, code.text)
                                .toClipEntry(),
                        )
                    }
                },
            ) { Text(stringResource(R.string.decks_code_copy)) }
        }
        when (saved) {
            true -> Text(stringResource(R.string.decks_code_saved))
            false -> Text(stringResource(R.string.decks_code_save_failed), color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
    }
}

@Composable
private fun UnavailableContent(code: DeckCodeUi.Unavailable) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.decks_code_unavailable))
        code.cards.forEach { Text("• $it") }
    }
}

private fun fileName(title: String) = "deck-" + title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

private val QR_SIZE = 280.dp
