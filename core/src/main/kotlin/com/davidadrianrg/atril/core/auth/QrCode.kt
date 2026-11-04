package com.davidadrianrg.atril.core.auth

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Square QR module matrix (true = dark), quiet zone excluded; UI draws its own margin. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]
}

object QrCode {
    fun encode(text: String): QrMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val bits = BooleanArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) bits[y * m.width + x] = m[x, y]
        return QrMatrix(m.width, bits)
    }
}
