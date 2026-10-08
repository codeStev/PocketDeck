package dev.pocketdeck.adapter.ui.decks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import kotlinx.coroutines.launch

@Composable
internal fun DeckCodeDialog(
    code: DeckCodeUi,
    onClose: () -> Unit,
) {
    PdDialog(
        title = code.title,
        onDismissRequest = onClose,
        confirmText = stringResource(R.string.decks_code_close),
        onConfirm = onClose,
    ) {
        when (code) {
            is DeckCodeUi.Ready -> ReadyContent(code)
            is DeckCodeUi.Unavailable -> UnavailableContent(code)
        }
    }
}

@Composable
private fun ColumnScope.ReadyContent(code: DeckCodeUi.Ready) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val bitmap = remember(code.text) { QrMatrix.of(code.text).toBitmap() }
    var saved by remember(code.text) { mutableStateOf<Boolean?>(null) }
    // Scrolls inside the dialog, and leaves room for the dialog's own button on small screens.
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.decks_code_image, code.title),
            modifier = Modifier.align(Alignment.CenterHorizontally).size(QR_SIZE),
        )
        if (code.substitutes.isNotEmpty()) {
            Text(
                stringResource(R.string.decks_code_contains),
                style = PocketDesign.typography.titleSm,
                color = PocketDesign.colors.ink,
            )
            code.substitutes.forEach {
                Text(
                    stringResource(R.string.decks_substitute_line, it.copies, it.replacement, it.original),
                    style = PocketDesign.typography.body,
                    color = PocketDesign.colors.ink,
                )
            }
        }
        Text(
            stringResource(R.string.decks_code_howto),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.inkMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
            PdButton(
                stringResource(R.string.decks_code_save),
                { saved = bitmap.saveToGallery(context, fileName(code.title)) },
                Modifier.weight(1f),
                style = PdButtonStyle.Secondary,
                size = PdButtonSize.Small,
            )
            PdButton(
                stringResource(R.string.decks_code_copy),
                {
                    scope.launch {
                        clipboard.setClipEntry(
                            android.content.ClipData
                                .newPlainText(code.title, code.text)
                                .toClipEntry(),
                        )
                    }
                },
                Modifier.weight(1f),
                style = PdButtonStyle.Secondary,
                size = PdButtonSize.Small,
            )
        }
        when (saved) {
            true -> PdBanner(stringResource(R.string.decks_code_saved), kind = PdBannerKind.Success)
            false -> PdBanner(stringResource(R.string.decks_code_save_failed), kind = PdBannerKind.Danger)
            null -> Unit
        }
    }
}

@Composable
private fun UnavailableContent(code: DeckCodeUi.Unavailable) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space1)) {
        PdBanner(stringResource(R.string.decks_code_unavailable), kind = PdBannerKind.Warning)
        code.cards.forEach { Text("• $it", style = PocketDesign.typography.body, color = PocketDesign.colors.ink) }
    }
}

private fun fileName(title: String) = "deck-" + title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

private val QR_SIZE = 280.dp
