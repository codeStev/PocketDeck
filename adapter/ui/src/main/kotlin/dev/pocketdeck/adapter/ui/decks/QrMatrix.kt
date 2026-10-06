package dev.pocketdeck.adapter.ui.decks

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The dark and light squares of a QR code for a deck code. The game's own codes are version 9 with high error
 * correction, and a code made the same way is accepted by its scanner.
 */
class QrMatrix private constructor(
    private val bits: BitMatrix,
) {
    val size: Int get() = bits.width

    fun isDark(
        x: Int,
        y: Int,
    ): Boolean = bits.get(x, y)

    companion object {
        private const val VERSION = 9
        private const val QUIET_ZONE = 4

        fun of(text: String): QrMatrix {
            val hints =
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                    EncodeHintType.QR_VERSION to VERSION,
                    EncodeHintType.MARGIN to QUIET_ZONE,
                )
            return QrMatrix(QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints))
        }
    }
}
