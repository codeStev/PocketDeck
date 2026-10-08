package dev.pocketdeck.adapter.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codestev.pocketdesign.components.PdBanner
import dev.codestev.pocketdesign.components.PdBannerKind
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonSize
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.exchange.FileFormat
import dev.pocketdeck.application.exchange.ImportFailure
import dev.pocketdeck.application.exchange.ImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Larger than any real collection file (a few hundred kilobytes at most); anything bigger is not one. */
private const val MAX_FILE_BYTES = 5 * 1024 * 1024
private const val BUFFER_BYTES = 8 * 1024

private val IMPORT_TYPES = arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "*/*")

/** Settings section: save the active account's collection to a file, or replace it from one. */
@Composable
fun CollectionFilesSection(viewModel: CollectionFilesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.JSON.mimeType)) { uri ->
            saveTo(uri, state.exportReady?.text, viewModel, context, scope)
        }
    val csv =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.CSV.mimeType)) { uri ->
            saveTo(uri, state.exportReady?.text, viewModel, context, scope)
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch { viewModel.onFileRead(withContext(Dispatchers.IO) { readText(context, uri) }) }
            }
        }
    // The file exists once the application has made it; the picker then asks where to keep it.
    LaunchedEffect(state.exportReady) {
        val file = state.exportReady ?: return@LaunchedEffect
        if (file.format == FileFormat.JSON) json.launch(file.fileName) else csv.launch(file.fileName)
    }
    state.preview?.let { Confirmation(it, viewModel) }
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        PdSectionHeader(stringResource(R.string.files_title))
        Text(
            stringResource(R.string.files_hint),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.inkMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
            PdButton(
                stringResource(R.string.files_export_json),
                { viewModel.onExport(FileFormat.JSON) },
                Modifier.weight(1f),
                style = PdButtonStyle.Secondary,
                size = PdButtonSize.Small,
            )
            PdButton(
                stringResource(R.string.files_export_csv),
                { viewModel.onExport(FileFormat.CSV) },
                Modifier.weight(1f),
                style = PdButtonStyle.Secondary,
                size = PdButtonSize.Small,
            )
        }
        PdButton(
            stringResource(R.string.files_import),
            { open.launch(IMPORT_TYPES) },
            Modifier.fillMaxWidth(),
            style = PdButtonStyle.Tonal,
        )
        state.message?.let { Message(it, viewModel::onMessageShown) }
    }
}

private fun saveTo(
    uri: android.net.Uri?,
    text: String?,
    viewModel: CollectionFilesViewModel,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (uri == null || text == null) {
        viewModel.onExportHandled(null)
        return
    }
    scope.launch {
        val saved = withContext(Dispatchers.IO) { writeText(context, uri, text) }
        viewModel.onExportHandled(saved)
    }
}

private fun writeText(
    context: android.content.Context,
    uri: android.net.Uri,
    text: String,
): Boolean =
    try {
        context.contentResolver
            .openOutputStream(uri)
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { it.write(text) } != null
    } catch (failure: java.io.IOException) {
        false
    }

private fun readText(
    context: android.content.Context,
    uri: android.net.Uri,
): String? =
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readUpTo(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) null else String(bytes, Charsets.UTF_8)
        }
    } catch (failure: java.io.IOException) {
        null
    }

/** Reads at most [limit] bytes, so a huge file picked by mistake is not pulled into memory. */
private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

@Composable
private fun Confirmation(
    preview: ImportPreview,
    viewModel: CollectionFilesViewModel,
) {
    PdDialog(
        title = stringResource(R.string.files_replace_title, preview.accountName),
        onDismissRequest = viewModel::onImportCancelled,
        confirmText = stringResource(R.string.files_replace_confirm),
        onConfirm = viewModel::onImportConfirmed,
        dismissText = stringResource(R.string.profile_cancel),
        destructive = true,
    ) {
        Text(
            stringResource(R.string.files_replace_text, preview.currentCards, preview.cards, preview.copies),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.ink,
        )
        if (preview.unknownCards > 0) {
            PdBanner(
                pluralStringResource(R.plurals.files_unknown_cards, preview.unknownCards, preview.unknownCards),
                kind = PdBannerKind.Warning,
            )
        }
        Text(
            stringResource(R.string.files_replace_undo),
            style = PocketDesign.typography.bodySm,
            color = PocketDesign.colors.inkMuted,
        )
    }
}

@Composable
private fun Message(
    message: FilesMessage,
    onDismiss: () -> Unit,
) {
    val (text, kind) =
        when (message) {
            is FilesMessage.Exported -> {
                stringResource(R.string.files_exported, message.fileName) to
                    PdBannerKind.Success
            }

            FilesMessage.ExportFailed -> {
                stringResource(R.string.files_export_failed) to PdBannerKind.Danger
            }

            is FilesMessage.Imported -> {
                stringResource(R.string.files_imported, message.cards) to PdBannerKind.Success
            }

            FilesMessage.ReadFailed -> {
                stringResource(R.string.files_read_failed) to PdBannerKind.Danger
            }

            is FilesMessage.Refused -> {
                stringResource(message.failure.text()) to PdBannerKind.Danger
            }
        }
    PdBanner(text, kind = kind, actionLabel = stringResource(R.string.files_dismiss), onAction = onDismiss)
}

private fun ImportFailure.text() =
    when (this) {
        ImportFailure.NotACollectionFile -> R.string.files_not_a_collection
        is ImportFailure.UnsupportedVersion -> R.string.files_newer_version
        ImportFailure.NothingRecognised -> R.string.files_nothing_recognised
    }
