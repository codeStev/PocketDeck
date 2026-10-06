package dev.pocketdeck.adapter.ui.decks

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import java.io.IOException

private const val PIXELS_PER_MODULE = 10
private const val DARK = 0xFF000000.toInt()
private const val LIGHT = 0xFFFFFFFF.toInt()
private const val ALBUM = "PocketDeck"

/** Draws the QR code as a picture the game's "Scan Saved Image" can read. */
internal fun QrMatrix.toBitmap(): Bitmap {
    val side = size * PIXELS_PER_MODULE
    val bitmap = createBitmap(side, side)
    for (y in 0 until side) {
        for (x in 0 until side) {
            bitmap[x, y] = if (isDark(x / PIXELS_PER_MODULE, y / PIXELS_PER_MODULE)) DARK else LIGHT
        }
    }
    return bitmap
}

/** Puts the picture into the gallery (needs no permission for the app's own new pictures). True if it worked. */
internal fun Bitmap.saveToGallery(
    context: Context,
    name: String,
): Boolean {
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) } == true
    } catch (failure: IOException) {
        resolver.delete(uri, null, null)
        false
    }
}

private const val PNG_QUALITY = 100
