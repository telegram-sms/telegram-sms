package com.github.sumimakito.awesomeqr

import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import java.util.*

class AwesomeQrRenderer {
    /**
     * @param contents             Contents to encode.
     * @param errorCorrectionLevel ErrorCorrectionLevel
     * @return QR code object.
     * @throws WriterException Refer to the messages below.
     */
    @Throws(WriterException::class)
    fun getProtoQrCode(contents: String, errorCorrectionLevel: ErrorCorrectionLevel): QRCode {
        if (contents.isEmpty()) {
            throw IllegalArgumentException("Found empty content.")
        }
        val hintMap = Hashtable<EncodeHintType, Any>()
        hintMap[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hintMap[EncodeHintType.ERROR_CORRECTION] = errorCorrectionLevel
        return Encoder.encode(contents, errorCorrectionLevel, hintMap)
    }

    /**
     * Convert the ByteMatrix to BitMatrix.
     *
     * @param matrix The input matrix.
     * @return The output matrix.
     */
    fun convertByteMatrixToBitMatrix(matrix: ByteMatrix): BitMatrix {
        val matrixWidgth = matrix.width
        val matrixHeight = matrix.height
        val output = BitMatrix(matrixWidgth, matrixHeight)
        output.clear()
        for (i in 0 until matrixWidgth) {
            for (j in 0 until matrixHeight) {
                // Zero is white in the bytematrix
                if (matrix[i, j].toInt() == 1) {
                    output[i] = j
                }
            }
        }
        return output
    }
}