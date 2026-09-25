package com.ubad.academy.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.random.Random

class DataUrlTest {

    @Test fun `encoder matches java Base64 for all tail lengths`() {
        for (size in listOf(0, 1, 2, 3, 4, 5, 1000, 49_151, 49_152, 49_153, 200_001)) {
            val bytes = Random(size).nextBytes(size)
            val expected = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(bytes)
            assertEquals("size $size", expected, DataUrlWriter.encodeToString("application/pdf", bytes))
        }
    }

    @Test fun `untyped blobs use octet-stream like FileReader`() {
        assertEquals("data:application/octet-stream;base64,AQI=", DataUrlWriter.encodeToString("", byteArrayOf(1, 2)))
    }

    @Test fun `decoder round-trips regardless of chunk boundaries`() {
        val bytes = Random(7).nextBytes(10_007)
        val url = DataUrlWriter.encodeToString("image/png", bytes).toCharArray()
        for (chunk in listOf(1, 3, 7, 64, 4096, url.size)) {
            val out = ByteArrayOutputStream()
            val sink = DataUrlSink(out)
            var i = 0
            while (i < url.size) { val n = minOf(chunk, url.size - i); sink.accept(url, i, n); i += n }
            sink.finish()
            assertEquals("image/png", sink.mime)
            assertArrayEquals("chunk $chunk", bytes, out.toByteArray())
        }
    }

    @Test fun `unpadded base64 and percent-encoded payloads decode`() {
        assertArrayEquals(byteArrayOf(1, 2), decodeDataUrl("data:x/y;base64,AQI")!!.second)
        assertEquals("hi there", String(decodeDataUrl("data:text/plain,hi%20there")!!.second))
    }

    @Test fun `non data urls are rejected like the web check`() {
        assertNull(decodeDataUrl("https://example.com/a.png"))
        assertNull(decodeDataUrl("blob:abc"))
        val sink = DataUrlSink(ByteArrayOutputStream())
        val s = "nope".toCharArray(); sink.accept(s, 0, s.size); sink.finish()
        assertFalse(sink.valid)
    }
}
