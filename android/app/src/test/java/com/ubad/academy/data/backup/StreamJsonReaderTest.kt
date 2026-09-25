package com.ubad.academy.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class StreamJsonReaderTest {
    private fun parse(s: String): JsonElement = StreamJsonReader(StringReader(s)).readElement()

    @Test fun `parses the same tree as kotlinx`() {
        val src = """ {"a":[1,2.5,-3e2,true,false,null,"x\"y\\z\u0627\n"],"b":{"c":{},"d":[]},"عربي":"نص"} """
        assertEquals(Json.parseToJsonElement(src), parse(src))
    }

    @Test fun `streams long strings in chunks`() {
        val long = "A".repeat(300_000)
        val r = StreamJsonReader(StringReader("""{"data":"$long","after":1}"""))
        r.beginObject()
        assertEquals("data", r.nextName())
        var total = 0
        r.readStringChunks { _, _, n -> total += n }
        assertEquals(300_000, total)
        assertEquals("after", r.nextName())
        assertEquals(Json.parseToJsonElement("1"), r.readElement())
        r.endObject()
    }

    @Test fun `skipValue skips nested structures`() {
        val r = StreamJsonReader(StringReader("""{"x":{"y":[1,{"z":"]}"}]},"k":"v"}"""))
        r.beginObject(); r.nextName(); r.skipValue()
        assertEquals("k", r.nextName())
        assertEquals("v", r.nextString())
    }

    @Test(expected = java.io.IOException::class)
    fun `malformed json throws`() { parse("""{"a":1,,}""") }
}
