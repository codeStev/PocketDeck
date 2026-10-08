package dev.pocketdeck.adapter.ui.decks

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** The words around a shared deck, in the app's language; the deck's own card names come in already translated. */
internal class ShareLabels(
    val pokemon: String,
    val trainers: String,
    val energy: String,
    val importCode: String,
    val noCode: String,
    /** The caption under the QR in the picture. */
    val scanHint: String,
    /** "Made with PocketDeck, an unofficial fan tool"; the repository link follows on its own line. */
    val footer: String,
)

internal const val REPOSITORY_LINK = "https://github.com/codeStev/PocketDeck"

/** Everything a shared deck says. [code] is the game's import code, null when the game cannot import this deck. */
internal class SharedDeck(
    val title: String,
    val pokemon: List<String>,
    val trainers: List<String>,
    val energy: String,
    val code: String?,
)

/** A deck as plain text for a chat: grouped lists, then the import code the game's "Scan Code" or paste field takes. */
internal fun shareText(
    deck: SharedDeck,
    labels: ShareLabels,
): String =
    buildString {
        appendLine(deck.title)
        appendLine()
        appendSection(labels.pokemon, deck.pokemon)
        appendSection(labels.trainers, deck.trainers)
        appendLine("${labels.energy}: ${deck.energy}")
        appendLine()
        appendLine(if (deck.code != null) labels.importCode else labels.noCode)
        deck.code?.let { appendLine(it) }
        appendLine()
        appendLine(labels.footer)
        append(REPOSITORY_LINK)
    }

private fun StringBuilder.appendSection(
    heading: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    appendLine(heading)
    lines.forEach { appendLine(it) }
    appendLine()
}

/** Opens Android's share sheet with the deck as text. */
internal fun shareAsText(
    context: Context,
    title: String,
    text: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Opens Android's share sheet with the deck picture, handed over through the app's own file provider. */
internal fun shareAsImage(
    context: Context,
    title: String,
    image: Bitmap,
): Boolean {
    val folder = File(context.cacheDir, SHARED_FOLDER).apply { mkdirs() }
    val file = File(folder, "deck.png")
    try {
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    } catch (failure: IOException) {
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

private const val SHARED_FOLDER = "shared"
private const val PNG_QUALITY = 100
