package se.lyzio.anatole.store

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Minimal PNG writer for 24-bit RGB (no alpha), which Google Play asks for screenshots and
 * the feature graphic. Android's Bitmap.compress always writes an alpha channel.
 */
object Png {
    fun rgb(width: Int, height: Int, argb: IntArray): ByteArray {
        require(argb.size == width * height)
        val raw = ByteArrayOutputStream(height * (width * 3 + 1))
        for (y in 0 until height) {
            raw.write(0) // filter: none
            for (x in 0 until width) {
                val p = argb[y * width + x]
                raw.write(p shr 16 and 0xFF)
                raw.write(p shr 8 and 0xFF)
                raw.write(p and 0xFF)
            }
        }
        val idat = ByteArrayOutputStream()
        DeflaterOutputStream(idat, Deflater(9)).use { it.write(raw.toByteArray()) }

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val ihdr = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(width)
                writeInt(height)
                writeByte(8) // bit depth
                writeByte(2) // color type: RGB
                writeByte(0) // compression
                writeByte(0) // filter
                writeByte(0) // interlace
            }
        }.toByteArray()
        chunk(out, "IHDR", ihdr)
        chunk(out, "IDAT", idat.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val d = DataOutputStream(out)
        d.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        d.write(typeBytes)
        d.write(data)
        val crc = CRC32().apply { update(typeBytes); update(data) }
        d.writeInt(crc.value.toInt())
    }
}
