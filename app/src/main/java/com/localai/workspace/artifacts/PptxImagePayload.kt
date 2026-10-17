package com.localai.workspace.artifacts

import android.graphics.BitmapFactory
import java.util.Base64
import java.util.zip.CRC32

internal data class PptxImagePayload(val bytes: ByteArray, val width: Int, val height: Int)

/** Validates one bounded, self-contained PNG supplied in a PPTX descriptor. */
internal object PptxImagePayloadValidator {
    const val MAX_IMAGE_BYTES = 128 * 1024
    const val MAX_TOTAL_IMAGE_BYTES = 128 * 1024
    const val MAX_DIMENSION = 4096
    const val MAX_PIXELS = 16_000_000L
    private const val MAX_DECODE_PIXELS = 1_000_000L
    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    private val legalDepths = mapOf(
        0 to setOf(1, 2, 4, 8, 16),
        2 to setOf(8, 16),
        3 to setOf(1, 2, 4, 8),
        4 to setOf(8, 16),
        6 to setOf(8, 16)
    )

    fun decodeAndValidate(encoded: String): PptxImagePayload {
        require(encoded.length <= ((MAX_IMAGE_BYTES + 2) / 3) * 4) { "PPTX_IMAGE_BYTES_LIMIT" }
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("PPTX_IMAGE_BASE64_INVALID")
        }
        require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) { "PPTX_IMAGE_BYTES_LIMIT" }
        require(Base64.getEncoder().encodeToString(bytes) == encoded) { "PPTX_IMAGE_BASE64_NON_CANONICAL" }
        val (width, height) = validateChunks(bytes)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth == width && bounds.outHeight == height) { "PPTX_IMAGE_DECODE_FAILED" }
        var sample = 1
        while ((width.toLong() / sample) * (height.toLong() / sample) > MAX_DECODE_PIXELS) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            inScaled = false
        })
        requireNotNull(bitmap) { "PPTX_IMAGE_DECODE_FAILED" }.recycle()
        return PptxImagePayload(bytes, width, height)
    }

    private fun validateChunks(bytes: ByteArray): Pair<Int, Int> {
        require(bytes.size >= signature.size + 25 && bytes.copyOfRange(0, signature.size).contentEquals(signature)) {
            "PPTX_IMAGE_PNG_REQUIRED"
        }
        var offset = signature.size
        var index = 0
        var width = 0
        var height = 0
        var colorType = -1
        var sawPalette = false
        var sawIdat = false
        var idatClosed = false
        var sawIend = false
        while (offset < bytes.size) {
            require(offset <= bytes.size - 12) { "PPTX_IMAGE_PNG_TRUNCATED" }
            val lengthLong = uint32(bytes, offset)
            require(lengthLong <= MAX_IMAGE_BYTES && lengthLong <= bytes.size - offset - 12L) { "PPTX_IMAGE_PNG_TRUNCATED" }
            val length = lengthLong.toInt()
            val typeStart = offset + 4
            val type = String(bytes, typeStart, 4, Charsets.US_ASCII)
            require(type.all { it in 'A'..'Z' || it in 'a'..'z' }) { "PPTX_IMAGE_PNG_CHUNK_INVALID" }
            val dataStart = typeStart + 4
            val crcOffset = dataStart + length
            val crc = CRC32().apply { update(bytes, typeStart, length + 4) }.value
            require(crc == uint32(bytes, crcOffset)) { "PPTX_IMAGE_PNG_CRC_INVALID" }

            if (index == 0) {
                require(type == "IHDR" && length == 13) { "PPTX_IMAGE_PNG_HEADER_INVALID" }
                width = uint32(bytes, dataStart).toInt()
                height = uint32(bytes, dataStart + 4).toInt()
                val depth = bytes[dataStart + 8].toInt() and 0xff
                colorType = bytes[dataStart + 9].toInt() and 0xff
                require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION && width.toLong() * height <= MAX_PIXELS) {
                    "PPTX_IMAGE_DIMENSIONS_LIMIT"
                }
                require(depth in legalDepths[colorType].orEmpty() &&
                    bytes[dataStart + 10].toInt() == 0 && bytes[dataStart + 11].toInt() == 0 &&
                    bytes[dataStart + 12].toInt() in 0..1) { "PPTX_IMAGE_PNG_HEADER_INVALID" }
            }
            when (type) {
                "IHDR" -> require(index == 0) { "PPTX_IMAGE_PNG_HEADER_INVALID" }
                "PLTE" -> {
                    require(!sawIdat && !sawPalette && colorType in setOf(2, 3, 6) && length in 3..768 && length % 3 == 0) {
                        "PPTX_IMAGE_PNG_PALETTE_INVALID"
                    }
                    sawPalette = true
                }
                "IDAT" -> {
                    require(!idatClosed) { "PPTX_IMAGE_PNG_CHUNK_ORDER_INVALID" }
                    sawIdat = true
                }
                "IEND" -> {
                    require(sawIdat && length == 0 && crcOffset + 4 == bytes.size) { "PPTX_IMAGE_PNG_END_INVALID" }
                    sawIend = true
                }
                else -> {
                    if (sawIdat) idatClosed = true
                    require(type[0] in 'a'..'z') { "PPTX_IMAGE_PNG_CRITICAL_CHUNK_UNSUPPORTED" }
                }
            }
            if (type != "IDAT" && sawIdat && type != "IEND") idatClosed = true
            offset = crcOffset + 4
            index++
            if (sawIend) break
        }
        require(sawIend && offset == bytes.size) { "PPTX_IMAGE_PNG_END_INVALID" }
        return width to height
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
}
