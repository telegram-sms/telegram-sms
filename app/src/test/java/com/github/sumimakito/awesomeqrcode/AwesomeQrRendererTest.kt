package com.github.sumimakito.awesomeqrcode

import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwesomeQrRendererTest {

    private val renderer = AwesomeQrRenderer()

    @Test
    fun getProtoQrCode_encodesSimpleAscii() {
        val qr = renderer.getProtoQrCode("HELLO", ErrorCorrectionLevel.L)
        assertNotNull(qr.matrix)
        // Version 1 = 21x21 — anything we encode produces at least a v1 module grid.
        assertTrue("matrix should be at least 21x21, was ${qr.matrix.width}", qr.matrix.width >= 21)
        assertEquals(qr.matrix.width, qr.matrix.height)
    }

    @Test
    fun getProtoQrCode_supportsAllEccLevels() {
        for (ecc in arrayOf(
            ErrorCorrectionLevel.L,
            ErrorCorrectionLevel.M,
            ErrorCorrectionLevel.Q,
            ErrorCorrectionLevel.H
        )) {
            val qr = renderer.getProtoQrCode("test", ecc)
            assertEquals("ECC level should round-trip", ecc, qr.ecLevel)
        }
    }

    @Test
    fun getProtoQrCode_higherEccUsesAtLeastAsManyModules() {
        // Stronger error correction can only require equal-or-larger versions for
        // the same payload; it never gets smaller.
        val payload = "https://example.com/some/longer/path?token=ABCDEFG"
        val low = renderer.getProtoQrCode(payload, ErrorCorrectionLevel.L)
        val high = renderer.getProtoQrCode(payload, ErrorCorrectionLevel.H)
        assertTrue(
            "H (${high.matrix.width}) should be >= L (${low.matrix.width})",
            high.matrix.width >= low.matrix.width
        )
    }

    @Test
    fun getProtoQrCode_encodesUtf8Content() {
        // The renderer hard-codes CHARACTER_SET=UTF-8, so non-ASCII content should
        // encode without throwing.
        val qr = renderer.getProtoQrCode("你好世界", ErrorCorrectionLevel.M)
        assertNotNull(qr.matrix)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getProtoQrCode_throwsOnEmptyContent() {
        renderer.getProtoQrCode("", ErrorCorrectionLevel.L)
    }

    @Test(expected = WriterException::class)
    fun getProtoQrCode_throwsWhenPayloadTooLargeForQrCapacity() {
        // QR version 40 with L can hold ~2953 bytes; 8000 random ASCII bytes
        // exceeds any QR capacity at any ECC level.
        val tooLong = "A".repeat(8000)
        renderer.getProtoQrCode(tooLong, ErrorCorrectionLevel.H)
    }

    // ---- convertByteMatrixToBitMatrix ----

    @Test
    fun convertByteMatrixToBitMatrix_preservesDimensions() {
        val byteMatrix = ByteMatrix(5, 7)
        byteMatrix.clear(0)
        val bitMatrix = renderer.convertByteMatrixToBitMatrix(byteMatrix)
        assertEquals(5, bitMatrix.width)
        assertEquals(7, bitMatrix.height)
    }

    @Test
    fun convertByteMatrixToBitMatrix_emptyMatrixIsAllFalse() {
        val byteMatrix = ByteMatrix(4, 4)
        byteMatrix.clear(0)
        val bitMatrix = renderer.convertByteMatrixToBitMatrix(byteMatrix)
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                assertEquals("($x,$y) should be unset", false, bitMatrix[x, y])
            }
        }
    }

    @Test
    fun convertByteMatrixToBitMatrix_setsOnlyOneBitsAndPreservesCoordinates() {
        // Asymmetric pattern guarantees we'd catch any x/y swap.
        // Set (0,0), (3,1), (1,2) to 1; everything else 0.
        val byteMatrix = ByteMatrix(4, 3)
        byteMatrix.clear(0)
        byteMatrix.set(0, 0, 1)
        byteMatrix.set(3, 1, 1)
        byteMatrix.set(1, 2, 1)

        val bitMatrix = renderer.convertByteMatrixToBitMatrix(byteMatrix)
        assertTrue(bitMatrix[0, 0])
        assertTrue(bitMatrix[3, 1])
        assertTrue(bitMatrix[1, 2])

        // Spot-check that nothing else got set.
        assertEquals(false, bitMatrix[1, 0])
        assertEquals(false, bitMatrix[2, 1])
        assertEquals(false, bitMatrix[0, 2])
    }

    @Test
    fun convertByteMatrixToBitMatrix_treatsZeroAsWhite_andNonOneAsAlsoWhite() {
        // Per the comment in the code, "zero is white". The implementation only
        // marks a bit when the byte equals exactly 1 — verify that contract: a 2
        // does NOT get set.
        val byteMatrix = ByteMatrix(2, 2)
        byteMatrix.clear(0)
        byteMatrix.set(0, 0, 1)
        byteMatrix.set(1, 1, 2)

        val bitMatrix = renderer.convertByteMatrixToBitMatrix(byteMatrix)
        assertTrue(bitMatrix[0, 0])
        assertEquals(false, bitMatrix[1, 1])
    }

    @Test
    fun convertByteMatrixToBitMatrix_realQrPayload_roundTripsMatrixSize() {
        // End-to-end: encode something, hand the byte matrix to the converter,
        // and the bit matrix should match the byte matrix dimension-for-dimension.
        val qr = renderer.getProtoQrCode("ROUND-TRIP-CHECK", ErrorCorrectionLevel.M)
        val byteMatrix = qr.matrix
        val bitMatrix = renderer.convertByteMatrixToBitMatrix(byteMatrix)
        assertEquals(byteMatrix.width, bitMatrix.width)
        assertEquals(byteMatrix.height, bitMatrix.height)

        // And the bit set should correspond exactly to byte == 1.
        for (x in 0 until byteMatrix.width) {
            for (y in 0 until byteMatrix.height) {
                val expected = byteMatrix[x, y].toInt() == 1
                assertEquals("($x,$y) mismatch", expected, bitMatrix[x, y])
            }
        }
    }
}
