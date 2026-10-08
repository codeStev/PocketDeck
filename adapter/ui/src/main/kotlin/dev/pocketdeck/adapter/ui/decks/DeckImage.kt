package dev.pocketdeck.adapter.ui.decks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import dev.codestev.pocketdesign.brand.SignalBrand
import java.io.File
import java.io.IOException
import dev.codestev.pocketdesign.R as DesignR

/** The deck's look in the picture: the brand's light-theme tint of its type, whatever theme the phone is in. */
internal class DeckImageStyle(
    tintKey: String,
) {
    private val light = SignalBrand.brand.light
    val strong = light.tint(tintKey).strong.toArgb()
    val soft = light.tint(tintKey).soft.toArgb()
    val accent = light.accent.toArgb()
}

private const val WIDTH = 1080
private const val MARGIN = 64f
private const val BAND_HEIGHT = 560f
private const val WORDMARK_SIZE = 40f
private const val TITLE_MAX_SIZE = 76f
private const val CHIP_SIZE = 36f
private const val HEADING_SIZE = 38f
private const val LINE_SIZE = 44f
private const val LINE_HEIGHT = 62f
private const val GAP = 40f
private const val QR_SIDE = 560
private const val QR_PANEL = 700f
private const val PANEL_WIDTH = 920f
private const val CAPTION_SIZE = 32f
private const val CAPTION_MIN = 22f
private const val FOOTER_HEIGHT = 170f
private const val CARD_ASPECT = 0.7194f
private const val CARD_HEIGHT = 440f
private const val SIDE_CARD_HEIGHT = 380f
private const val TILT = 7f
private const val CORNER = 26f
private const val OUTLINE = 6f
private const val INK = 0xFF131822.toInt()
private const val MUTED = 0xFF4F5A6B.toInt()

/** Loads nothing: [heroes] are the key cards' pictures, already fetched, most important first (it stands in the middle). */
internal fun renderDeckImage(
    context: Context,
    deck: SharedDeck,
    labels: ShareLabels,
    style: DeckImageStyle,
    heroes: List<Bitmap>,
    qr: Bitmap?,
): Bitmap {
    val fonts = Fonts(context)
    // The same walk twice: once on no canvas to learn how tall the picture is, then on the bitmap.
    val height = DeckPainter(null, fonts, style, deck, labels, heroes, qr).paint().toInt()
    val bitmap = createBitmap(WIDTH, height)
    DeckPainter(Canvas(bitmap), fonts, style, deck, labels, heroes, qr).paint()
    return bitmap
}

private class Fonts(
    private val context: Context,
) {
    /** Fredoka, the app's display face; the plain bold system face if it cannot be loaded. */
    val display: Typeface = variable(DesignR.font.fredoka, "fredoka", DISPLAY_WEIGHT) ?: Typeface.DEFAULT_BOLD
    val body: Typeface = variable(DesignR.font.nunito, "nunito", BODY_WEIGHT) ?: Typeface.DEFAULT
    val bodyBold: Typeface = variable(DesignR.font.nunito, "nunito", BODY_BOLD_WEIGHT) ?: Typeface.DEFAULT_BOLD

    /**
     * The fonts are variable files; Android only picks a weight from one when it is given as a file, so the resource is
     * copied once into the cache and opened from there.
     */
    private fun variable(
        resource: Int,
        name: String,
        weight: Int,
    ): Typeface? =
        try {
            val file = File(context.cacheDir, "fonts/$name.ttf")
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                context.resources.openRawResource(resource).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
            }
            Typeface.Builder(file).setFontVariationSettings("'wght' $weight").build()
        } catch (failure: IOException) {
            null
        }

    private companion object {
        const val DISPLAY_WEIGHT = 700
        const val BODY_WEIGHT = 600
        const val BODY_BOLD_WEIGHT = 800
    }
}

private class DeckPainter(
    private val canvas: Canvas?,
    private val fonts: Fonts,
    private val style: DeckImageStyle,
    private val deck: SharedDeck,
    private val labels: ShareLabels,
    private val heroes: List<Bitmap>,
    private val qr: Bitmap?,
) {
    private var y = 0f

    /** Draws top to bottom and returns the height used. */
    fun paint(): Float {
        canvas?.drawColor(Color.WHITE)
        band()
        y = BAND_HEIGHT + GAP * 1.4f
        title()
        energyChip()
        section(labels.pokemon, deck.pokemon)
        section(labels.trainers, deck.trainers)
        qrPanel()
        footer()
        return y
    }

    private fun band() {
        canvas ?: return
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), BAND_HEIGHT, fill(style.soft))
        canvas.withClip(0f, 0f, WIDTH.toFloat(), BAND_HEIGHT) { fan() }
        wordmark()
    }

    /** The key cards fanned out at the bottom of the band, cropped by its edge like the deck tile in the app. */
    private fun fan() {
        val canvas = canvas ?: return
        if (heroes.isEmpty()) return
        val middle = heroes[0]
        val sides = heroes.drop(1).take(2)
        val sideHeight = SIDE_CARD_HEIGHT
        val centreX = WIDTH / 2f
        val spread = CARD_HEIGHT * CARD_ASPECT * 0.78f
        sides.forEachIndexed { index, bitmap ->
            val direction = if (index == 0) -1 else 1
            card(canvas, bitmap, centreX + direction * spread, BAND_HEIGHT + 50f, sideHeight, direction * TILT)
        }
        card(canvas, middle, centreX, BAND_HEIGHT + 20f, CARD_HEIGHT, 0f)
    }

    private fun card(
        canvas: Canvas,
        bitmap: Bitmap,
        centreX: Float,
        bottom: Float,
        height: Float,
        tilt: Float,
    ) {
        val width = height * CARD_ASPECT
        val rect = RectF(centreX - width / 2, bottom - height, centreX + width / 2, bottom)
        canvas.withRotation(tilt, rect.centerX(), rect.bottom) {
            val clip = Path().apply { addRoundRect(rect, CORNER, CORNER, Path.Direction.CW) }
            withClip(clip) { drawBitmap(bitmap.scale(width.toInt(), height.toInt(), true), rect.left, rect.top, null) }
            drawRoundRect(rect, CORNER, CORNER, stroke(style.strong, OUTLINE))
        }
    }

    /** "PocketDeck" in a pill of the brand's accent, top left of the band. */
    private fun wordmark() {
        val canvas = canvas ?: return
        val paint = text(WORDMARK_SIZE, Color.WHITE, fonts.display)
        val width = paint.measureText(WORDMARK)
        val pill =
            RectF(
                MARGIN,
                MARGIN,
                MARGIN + width + 2 * WORDMARK_PADDING,
                MARGIN + WORDMARK_SIZE + 2 * WORDMARK_PADDING,
            )
        canvas.drawRoundRect(pill, pill.height() / 2, pill.height() / 2, fill(style.accent))
        canvas.drawText(
            WORDMARK,
            pill.left + WORDMARK_PADDING,
            pill.bottom - WORDMARK_PADDING - WORDMARK_SIZE * 0.14f,
            paint,
        )
    }

    private fun title() {
        val paint = text(TITLE_MAX_SIZE, INK, fonts.display)
        val room = WIDTH - 2 * MARGIN
        while (paint.measureText(deck.title) > room && paint.textSize > TITLE_MIN_SIZE) paint.textSize -= 2f
        y += paint.textSize
        canvas?.drawText(deck.title, MARGIN, y, paint)
        y += GAP * 0.7f
    }

    /** The deck's energy in a pill of its tint. */
    private fun energyChip() {
        val paint = text(CHIP_SIZE, INK, fonts.bodyBold)
        val width = paint.measureText(deck.energy) + 2 * CHIP_PADDING + DOT + CHIP_PADDING / 2
        val chip = RectF(MARGIN, y, MARGIN + width, y + CHIP_SIZE + CHIP_PADDING)
        canvas?.let {
            it.drawRoundRect(chip, chip.height() / 2, chip.height() / 2, fill(style.soft))
            it.drawCircle(chip.left + CHIP_PADDING + DOT / 2, chip.centerY(), DOT / 2, fill(style.strong))
            it.drawText(
                deck.energy,
                chip.left + CHIP_PADDING + DOT + CHIP_PADDING / 2,
                chip.bottom - CHIP_PADDING * 0.8f,
                paint,
            )
        }
        y = chip.bottom + GAP * 0.4f
    }

    private fun section(
        name: String,
        lines: List<String>,
    ) {
        if (lines.isEmpty()) return
        y += GAP + HEADING_SIZE
        canvas?.let {
            it.drawRoundRect(RectF(MARGIN, y - HEADING_SIZE, MARGIN + BAR, y), BAR / 2, BAR / 2, fill(style.strong))
            it.drawText(name, MARGIN + BAR + 20f, y - 4f, text(HEADING_SIZE, style.strong, fonts.bodyBold))
        }
        y += GAP / 2
        val paint = text(LINE_SIZE, INK, fonts.body)
        lines.forEach {
            y += LINE_HEIGHT
            canvas?.drawText(it, MARGIN, y, paint)
        }
    }

    /** The import QR on a white panel with an outline in the tint; the QR itself stays pure black on white. */
    private fun qrPanel() {
        y += GAP * 1.4f
        val qr = qr ?: return
        val panel = RectF((WIDTH - PANEL_WIDTH) / 2, y, (WIDTH + PANEL_WIDTH) / 2, y + QR_PANEL + CAPTION_SIZE + GAP)
        canvas?.let {
            it.drawRoundRect(panel, CORNER, CORNER, fill(Color.WHITE))
            it.drawRoundRect(panel, CORNER, CORNER, stroke(style.strong, OUTLINE))
            it.drawBitmap(qr.scale(QR_SIDE, QR_SIDE, false), (WIDTH - QR_SIDE) / 2f, y + (QR_PANEL - QR_SIDE) / 2, null)
            val caption = text(CAPTION_SIZE, MUTED, fonts.bodyBold).apply { textAlign = Paint.Align.CENTER }
            while (caption.measureText(labels.scanHint) > PANEL_WIDTH - 2 * GAP &&
                caption.textSize > CAPTION_MIN
            ) {
                caption.textSize -= 1f
            }
            it.drawText(labels.scanHint, WIDTH / 2f, panel.bottom - GAP * 0.6f, caption)
        }
        y = panel.bottom
    }

    /** A bar in the brand's accent with the wordmark and the unofficial note. */
    private fun footer() {
        y += GAP * 1.4f
        canvas?.let {
            it.drawRect(0f, y, WIDTH.toFloat(), y + FOOTER_HEIGHT, fill(style.accent))
            it.drawText(WORDMARK, MARGIN, y + 70f, text(WORDMARK_SIZE + 6f, Color.WHITE, fonts.display))
            val small = text(FOOTER_SIZE, Color.WHITE, fonts.body)
            it.drawText(labels.footer, MARGIN, y + 112f, small)
            it.drawText(REPOSITORY_LINK, MARGIN, y + 112f + FOOTER_SIZE * 1.3f, small)
        }
        y += FOOTER_HEIGHT
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(
        color: Int,
        width: Float,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun text(
        size: Float,
        color: Int,
        face: Typeface,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = face
    }

    private companion object {
        const val WORDMARK = "PocketDeck"
        const val WORDMARK_PADDING = 22f
        const val TITLE_MIN_SIZE = 44f
        const val CHIP_PADDING = 26f
        const val DOT = 22f
        const val BAR = 10f
        const val FOOTER_SIZE = 28f
    }
}
