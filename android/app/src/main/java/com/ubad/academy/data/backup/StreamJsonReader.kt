package com.ubad.academy.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.io.IOException
import java.io.Reader

/**
 * Minimal pull-style JSON reader whose distinguishing feature is [readStringChunks]:
 * a string value can be consumed in chunks, so base64 data URLs of any size
 * (PDFs, videos inside a backup) stream straight to disk.
 *
 * Small values are materialized as kotlinx [JsonElement]s via [readElement].
 */
class StreamJsonReader(private val reader: Reader) {

    enum class Kind { BEGIN_OBJECT, END_OBJECT, BEGIN_ARRAY, END_ARRAY, NAME, STRING, NUMBER, TRUE, FALSE, NULL, END_DOCUMENT }

    private val buf = CharArray(BUF_SIZE)
    private var pos = 0
    private var lim = 0

    private enum class Ctx { EMPTY_DOC, NONEMPTY_DOC, EMPTY_ARRAY, NONEMPTY_ARRAY, EMPTY_OBJECT, DANGLING_NAME, NONEMPTY_OBJECT }
    private val stack = ArrayList<Ctx>().apply { add(Ctx.EMPTY_DOC) }
    private var peeked: Kind? = null

    // ── low level ──────────────────────────────────────────────────────

    private fun fill(): Boolean {
        if (pos < lim) return true
        val n = reader.read(buf, 0, buf.size)
        if (n <= 0) return false
        pos = 0; lim = n
        return true
    }

    private fun nextNonWs(): Int {
        while (true) {
            if (!fill()) return -1
            val c = buf[pos]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF') { pos++; continue }
            return c.code
        }
    }

    private fun syntax(msg: String): Nothing = throw IOException("Malformed JSON: $msg")

    private fun valueKind(c: Int): Kind = when (c) {
        '{'.code -> Kind.BEGIN_OBJECT
        '['.code -> Kind.BEGIN_ARRAY
        '"'.code -> Kind.STRING
        't'.code -> Kind.TRUE
        'f'.code -> Kind.FALSE
        'n'.code -> Kind.NULL
        -1 -> syntax("unexpected end")
        else -> Kind.NUMBER
    }

    fun peek(): Kind {
        peeked?.let { return it }
        val top = stack.last()
        val k: Kind = when (top) {
            Ctx.EMPTY_DOC -> { stack[stack.lastIndex] = Ctx.NONEMPTY_DOC; valueKind(nextNonWs()) }
            Ctx.NONEMPTY_DOC -> if (nextNonWs() == -1) Kind.END_DOCUMENT else syntax("trailing data")
            Ctx.EMPTY_ARRAY -> {
                val c = nextNonWs()
                if (c == ']'.code) Kind.END_ARRAY else { stack[stack.lastIndex] = Ctx.NONEMPTY_ARRAY; valueKind(c) }
            }
            Ctx.NONEMPTY_ARRAY -> {
                when (nextNonWs()) {
                    ']'.code -> Kind.END_ARRAY
                    ','.code -> { pos++; valueKind(nextNonWs()) }
                    else -> syntax("expected , or ]")
                }
            }
            Ctx.EMPTY_OBJECT, Ctx.NONEMPTY_OBJECT -> {
                var c = nextNonWs()
                if (c == '}'.code) Kind.END_OBJECT
                else {
                    if (top == Ctx.NONEMPTY_OBJECT) { if (c != ','.code) syntax("expected , or }"); pos++; c = nextNonWs() }
                    if (c != '"'.code) syntax("expected name")
                    Kind.NAME
                }
            }
            Ctx.DANGLING_NAME -> {
                if (nextNonWs() != ':'.code) syntax("expected :")
                pos++
                stack[stack.lastIndex] = Ctx.NONEMPTY_OBJECT
                valueKind(nextNonWs())
            }
        }
        peeked = k
        return k
    }

    private fun expect(k: Kind) { if (peek() != k) syntax("expected $k but was ${peek()}") }

    fun beginObject() { expect(Kind.BEGIN_OBJECT); pos++; peeked = null; stack.add(Ctx.EMPTY_OBJECT) }
    fun endObject() { expect(Kind.END_OBJECT); pos++; peeked = null; stack.removeAt(stack.lastIndex) }
    fun beginArray() { expect(Kind.BEGIN_ARRAY); pos++; peeked = null; stack.add(Ctx.EMPTY_ARRAY) }
    fun endArray() { expect(Kind.END_ARRAY); pos++; peeked = null; stack.removeAt(stack.lastIndex) }

    fun hasNext(): Boolean = peek().let { it != Kind.END_OBJECT && it != Kind.END_ARRAY && it != Kind.END_DOCUMENT }

    fun nextName(): String {
        expect(Kind.NAME)
        val sb = StringBuilder()
        readQuoted(Int.MAX_VALUE) { c, o, l -> sb.appendRange(c, o, o + l) }
        peeked = null
        stack[stack.lastIndex] = Ctx.DANGLING_NAME
        return sb.toString()
    }

    /** Streams the current string value to [sink] in chunks (unescaped). */
    fun readStringChunks(sink: (CharArray, Int, Int) -> Unit) {
        expect(Kind.STRING)
        readQuoted(Int.MAX_VALUE, sink)
        peeked = null
    }

    /** Reads a string value, keeping at most [max] chars (rest is consumed and dropped). */
    fun nextString(max: Int = Int.MAX_VALUE): String {
        expect(Kind.STRING)
        val sb = StringBuilder()
        readQuoted(max) { c, o, l -> sb.appendRange(c, o, o + l) }
        peeked = null
        return sb.toString()
    }

    private val scratch = CharArray(2)

    /** Consumes a quoted string starting at the opening quote. */
    private fun readQuoted(max: Int, sink: (CharArray, Int, Int) -> Unit) {
        pos++ // opening quote
        var kept = 0
        fun emit(c: CharArray, o: Int, l: Int) {
            if (kept >= max) return
            val n = minOf(l, max - kept)
            if (n > 0) { sink(c, o, n); kept += n }
        }
        while (true) {
            if (!fill()) syntax("unterminated string")
            var i = pos
            while (i < lim) {
                val c = buf[i]
                if (c == '"' || c == '\\') break
                i++
            }
            if (i > pos) emit(buf, pos, i - pos)
            pos = i
            if (pos == lim) continue
            val c = buf[pos++]
            if (c == '"') return
            // escape
            if (!fill()) syntax("bad escape")
            when (val e = buf[pos++]) {
                'n' -> { scratch[0] = '\n'; emit(scratch, 0, 1) }
                't' -> { scratch[0] = '\t'; emit(scratch, 0, 1) }
                'r' -> { scratch[0] = '\r'; emit(scratch, 0, 1) }
                'b' -> { scratch[0] = '\b'; emit(scratch, 0, 1) }
                'f' -> { scratch[0] = '\u000C'; emit(scratch, 0, 1) }
                'u' -> {
                    var v = 0
                    repeat(4) {
                        if (!fill()) syntax("bad unicode escape")
                        val d = Character.digit(buf[pos++], 16)
                        if (d < 0) syntax("bad unicode escape")
                        v = v * 16 + d
                    }
                    scratch[0] = v.toChar(); emit(scratch, 0, 1)
                }
                else -> { scratch[0] = e; emit(scratch, 0, 1) } // \" \\ \/
            }
        }
    }

    private fun readLiteral(): String {
        val sb = StringBuilder()
        while (fill()) {
            val c = buf[pos]
            if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ':') break
            sb.append(c); pos++
        }
        peeked = null
        return sb.toString()
    }

    fun skipValue() {
        when (peek()) {
            Kind.BEGIN_OBJECT -> { beginObject(); while (hasNext()) { nextName(); skipValue() }; endObject() }
            Kind.BEGIN_ARRAY -> { beginArray(); while (hasNext()) skipValue(); endArray() }
            Kind.STRING -> { readQuoted(0) { _, _, _ -> }; peeked = null }
            Kind.NAME -> nextName()
            Kind.NUMBER, Kind.TRUE, Kind.FALSE, Kind.NULL -> readLiteral()
            else -> syntax("nothing to skip")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    /** Materializes the next value. Strings longer than [maxString] are truncated. */
    fun readElement(maxString: Int = DEFAULT_MAX_STRING, depth: Int = 0): JsonElement {
        if (depth > MAX_DEPTH) syntax("nesting too deep")
        return when (peek()) {
            Kind.BEGIN_OBJECT -> {
                beginObject()
                val m = LinkedHashMap<String, JsonElement>()
                while (hasNext()) { val n = nextName(); m[n] = readElement(maxString, depth + 1) }
                endObject()
                JsonObject(m)
            }
            Kind.BEGIN_ARRAY -> {
                beginArray()
                val l = ArrayList<JsonElement>()
                while (hasNext()) l += readElement(maxString, depth + 1)
                endArray()
                JsonArray(l)
            }
            Kind.STRING -> JsonPrimitive(nextString(maxString))
            Kind.TRUE -> { readLiteral(); JsonPrimitive(true) }
            Kind.FALSE -> { readLiteral(); JsonPrimitive(false) }
            Kind.NULL -> { readLiteral(); JsonNull }
            Kind.NUMBER -> {
                val s = readLiteral()
                if (s.toDoubleOrNull() == null) syntax("bad number '$s'")
                JsonUnquotedLiteral(s) // keep the literal text, like kotlinx's own parser
            }
            else -> syntax("unexpected ${peek()}")
        }
    }

    companion object {
        private const val BUF_SIZE = 64 * 1024
        /** Longer than any field the normalizer keeps (max 50 000) while bounding memory. */
        const val DEFAULT_MAX_STRING = 256 * 1024
        private const val MAX_DEPTH = 64
    }
}
