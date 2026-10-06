package dev.pocketdeck.adapter.ui.decks

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class QrMatrixTest {
    private val code = "C5iWqJiWqJicApiaXpiXwJiaaJiahpiXKpiWnpiWnpiXogkAM+oAM+oAA9QAA9QAM/4AR6QAR6QAHPIAHPIBBA=="

    @Test
    fun `is a version 9 code like the ones the game shows`() {
        // 17 + 4 * 9 modules per side plus a quiet zone of 4 on every side.
        QrMatrix.of(code).size shouldBe 53 + 2 * 4
    }

    @Test
    fun `can be read back`() {
        val matrix = QrMatrix.of(code)
        val scale = 4
        val side = matrix.size * scale
        val pixels =
            IntArray(side * side) { index ->
                val dark = matrix.isDark(index % side / scale, index / side / scale)
                if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))

        QRCodeReader().decode(image).text shouldBe code
    }
}
