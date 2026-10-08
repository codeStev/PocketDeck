package dev.pocketdeck.adapter.ui.decks

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.File
import java.io.IOException

/** The words around a shared deck, in the app's language; the deck's own card names come in already translated. */
internal class ShareLabels(
    val pokemon: String,
    val trainers: String,
    val energy: String,
    val importCode: String,
    val noCode: String,
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

private const val IMAGE_WIDTH = 1080
private const val MARGIN = 64f
private const val TITLE_SIZE = 64f
private const val HEADING_SIZE = 40f
private const val LINE_SIZE = 44f
private const val LINE_HEIGHT = 62f
private const val FOOTER_SIZE = 30f
private const val FOOTER_LINE_HEIGHT = 42f
private const val FOOTER_LINES = 2
private const val QR_SIDE = 620
private const val GAP = 40f
private const val MUTED = 0xFF4F5A6B.toInt()
private const val PNG_QUALITY = 100

/**
 * The deck as one picture: name, the card list and the import QR. The picture is white with black type, so the QR
 * stays pure black on white and the game's "Scan Saved Image" can read it from a screenshot or a chat.
 */
internal fun renderDeckImage(
    deck: SharedDeck,
    labels: ShareLabels,
    qr: Bitmap?,
): Bitmap {
    val title = paint(TITLE_SIZE, Color.BLACK, bold = true)
    val heading = paint(HEADING_SIZE, MUTED, bold = true)
    val line = paint(LINE_SIZE, Color.BLACK, bold = false)
    val footer = paint(FOOTER_SIZE, MUTED, bold = false)
    val rows = deck.pokemon.size + deck.trainers.size
    val headings = listOf(deck.pokemon, deck.trainers).count { it.isNotEmpty() } + 1
    val height =
        (
            MARGIN + TITLE_SIZE + GAP + headings * (HEADING_SIZE + GAP * 3 / 2) + rows * LINE_HEIGHT + GAP +
                (if (qr != null) QR_SIDE + GAP else 0f) + FOOTER_LINES * FOOTER_LINE_HEIGHT + MARGIN
        ).toInt()
    val bitmap = createBitmap(IMAGE_WIDTH, height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    var y = MARGIN + TITLE_SIZE
    canvas.drawText(deck.title, MARGIN, y, title)
    y += GAP

    fun section(
        name: String,
        lines: List<String>,
    ) {
        if (lines.isEmpty()) return
        y += GAP + HEADING_SIZE
        canvas.drawText(name, MARGIN, y, heading)
        y += GAP / 2
        lines.forEach {
            y += LINE_HEIGHT
            canvas.drawText(it, MARGIN, y, line)
        }
    }
    section(labels.pokemon, deck.pokemon)
    section(labels.trainers, deck.trainers)
    y += GAP + HEADING_SIZE
    canvas.drawText("${labels.energy}: ${deck.energy}", MARGIN, y, heading)
    y += GAP
    qr?.let {
        val scaled = it.scale(QR_SIDE, QR_SIDE, false)
        canvas.drawBitmap(scaled, (IMAGE_WIDTH - QR_SIDE) / 2f, y, null)
        y += QR_SIDE + GAP
    }
    canvas.drawText(labels.footer, MARGIN, y + FOOTER_SIZE, footer)
    canvas.drawText(REPOSITORY_LINK, MARGIN, y + FOOTER_SIZE + FOOTER_LINE_HEIGHT, footer)
    return bitmap
}

private fun paint(
    size: Float,
    color: Int,
    bold: Boolean,
) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    this.color = color
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
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
