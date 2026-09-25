package com.ubad.academy.data.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer

/**
 * Push-based decoder for a `data:[<mime>][;base64],<payload>` string that arrives
 * in chunks from [StreamJsonReader]. Bytes are written straight to [out].
 *
 * `valid` mirrors the web check `typeof a.data==='string' && a.data.startsWith('data:')`.
 */
class DataUrlSink(private val out: OutputStream) {
    private val header = StringBuilder()
    private var inPayload = false
    private var base64 = false
    var valid = true; private set
    var mime: String = ""; private set
    var bytes: Long = 0; private set

    // base64 state
    private var quad = 0
    private var quadLen = 0
    private var ended = false
    private val outBuf = ByteArray(48 * 1024)
    private var outLen = 0

    // percent-decoding state (non-base64 data URLs; FileReader never produces these but be tolerant)
    private val pct = StringBuilder()

    fun accept(c: CharArray, off: Int, len: Int) {
        if (!valid) return
        var i = off
        val end = off + len
        while (!inPayload && i < end) {
            val ch = c[i++]
            if (ch == ',') { parseHeader(); inPayload = true; if (!valid) return }
            else {
                header.append(ch)
                if (header.length > 5 && !header.startsWith("data:")) { valid = false; return }
                if (header.length > MAX_HEADER) { valid = false; return }
            }
        }
        if (!inPayload || i >= end) return
        if (base64) decodeBase64(c, i, end) else decodePercent(c, i, end)
    }

    private fun parseHeader() {
        val h = header.toString()
        if (!h.startsWith("data:")) { valid = false; return }
        val meta = h.substring(5).split(';')
        mime = meta.firstOrNull()?.trim()?.lowercase().orEmpty().take(120)
        base64 = meta.drop(1).any { it.trim().equals("base64", ignoreCase = true) }
    }

    private fun decodeBase64(c: CharArray, from: Int, to: Int) {
        for (i in from until to) {
            if (ended) return
            val ch = c[i]
            val v = if (ch.code < 128) B64[ch.code] else -1
            if (v >= 0) {
                quad = (quad shl 6) or v
                quadLen++
                if (quadLen == 4) {
                    put((quad shr 16) and 0xFF); put((quad shr 8) and 0xFF); put(quad and 0xFF)
                    quad = 0; quadLen = 0
                }
            } else if (ch == '=') {
                ended = true
            } // whitespace / newlines / garbage ignored like atob-tolerant decoders
        }
    }

    private fun decodePercent(c: CharArray, from: Int, to: Int) {
        for (i in from until to) {
            val ch = c[i]
            if (pct.isNotEmpty() || ch == '%') {
                pct.append(ch)
                if (pct.length == 3) {
                    val b = pct.substring(1).toIntOrNull(16)
                    if (b != null) put(b) else pct.toString().toByteArray().forEach { put(it.toInt()) }
                    pct.setLength(0)
                }
            } else {
                String(charArrayOf(ch)).toByteArray(Charsets.UTF_8).forEach { put(it.toInt()) }
            }
        }
    }

    private fun put(b: Int) {
        outBuf[outLen++] = b.toByte()
        bytes++
        if (outLen == outBuf.size) flushBuf()
    }

    private fun flushBuf() { if (outLen > 0) { out.write(outBuf, 0, outLen); outLen = 0 } }

    /** Call after the JSON string ended. */
    fun finish() {
        if (!valid) return
        if (!inPayload) { if (!header.startsWith("data:")) valid = false; parseHeader(); inPayload = true }
        if (base64) {
            // trailing partial quantum (unpadded input)
            when (quadLen) {
                2 -> put((quad shr 4) and 0xFF)
                3 -> { put((quad shr 10) and 0xFF); put((quad shr 2) and 0xFF) }
            }
        }
        flushBuf()
        out.flush()
    }

    companion object {
        private const val MAX_HEADER = 512
        private val B64 = IntArray(128) { -1 }.also { t ->
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, ch -> t[ch.code] = i }
            t['-'.code] = 62; t['_'.code] = 63 // url-safe variant
        }
    }
}

/** Streams `data:<mime>;base64,<...>` for a file into a JSON string being written (without quotes). */
object DataUrlWriter {
    private val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()

    fun write(w: Writer, mime: String, file: File) = file.inputStream().use { write(w, mime, it) }

    fun write(w: Writer, mime: String, input: InputStream) {
        // FileReader.readAsDataURL uses application/octet-stream for untyped blobs.
        w.write("data:")
        w.write(mime.ifBlank { "application/octet-stream" })
        w.write(";base64,")
        val inBuf = ByteArray(3 * 16 * 1024)
        val outBuf = CharArray(4 * 16 * 1024)
        var have = 0
        while (true) {
            val n = input.read(inBuf, have, inBuf.size - have)
            if (n < 0) break
            have += n
            val whole = have - have % 3
            var o = 0
            var i = 0
            while (i < whole) {
                val v = ((inBuf[i].toInt() and 0xFF) shl 16) or ((inBuf[i + 1].toInt() and 0xFF) shl 8) or (inBuf[i + 2].toInt() and 0xFF)
                outBuf[o++] = ALPHA[(v shr 18) and 63]; outBuf[o++] = ALPHA[(v shr 12) and 63]
                outBuf[o++] = ALPHA[(v shr 6) and 63]; outBuf[o++] = ALPHA[v and 63]
                i += 3
            }
            if (o > 0) w.write(outBuf, 0, o)
            val rest = have - whole // 0..2 bytes carried to the next round
            for (k in 0 until rest) inBuf[k] = inBuf[whole + k]
            have = rest
        }
        encodeTail(w, inBuf, have)
    }

    private fun encodeTail(w: Writer, b: ByteArray, n: Int) {
        when (n) {
            1 -> {
                val v = (b[0].toInt() and 0xFF) shl 16
                w.write(charArrayOf(ALPHA[(v shr 18) and 63], ALPHA[(v shr 12) and 63], '=', '='))
            }
            2 -> {
                val v = ((b[0].toInt() and 0xFF) shl 16) or ((b[1].toInt() and 0xFF) shl 8)
                w.write(charArrayOf(ALPHA[(v shr 18) and 63], ALPHA[(v shr 12) and 63], ALPHA[(v shr 6) and 63], '='))
            }
        }
    }

    /** Test helper. */
    fun encodeToString(mime: String, bytes: ByteArray): String {
        val sw = java.io.StringWriter()
        write(sw, mime, bytes.inputStream())
        return sw.toString()
    }
}

/** Test helper: decodes a full data URL string. */
internal fun decodeDataUrl(s: String): Pair<String, ByteArray>? {
    val bos = ByteArrayOutputStream()
    val sink = DataUrlSink(bos)
    val arr = s.toCharArray()
    sink.accept(arr, 0, arr.size)
    sink.finish()
    return if (sink.valid) sink.mime to bos.toByteArray() else null
}
