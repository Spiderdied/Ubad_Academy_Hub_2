package com.ubad.academy.data.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip guarantees for the JSON ⇄ Firestore value bridge.
 *
 * The web writes `cloudState.appData` as a real nested Firestore map, so Android
 * must write the same structure. A value that changes shape on the way through —
 * an integer becoming a float, a nested map collapsing to a string — would still
 * "work" in isolation and then break the other client, so the important cases are
 * pinned here rather than trusted.
 */
class FirestoreValuesTest {

    private val json = Json { explicitNulls = true }

    private fun roundTrip(obj: JsonObject): JsonObject =
        FirestoreValues.fromFirestoreMap(FirestoreValues.toFirestoreMap(obj))

    @Test
    fun `nested payload survives a round trip unchanged`() {
        val payload = buildJsonObject {
            put("courses", JsonArray(emptyList()))
            put("settings", buildJsonObject { put("lang", "ar"); put("sound", true) })
            put("notes", JsonArray(listOf(
                buildJsonObject {
                    put("id", "n1"); put("title", "ملخص المحاضرة"); put("body", "النص العربي")
                    put("tags", JsonArray(listOf(JsonPrimitive("مهم"))))
                    put("pin", false); put("createdAt", 1_700_000_000_000L)
                    put("hasImages", true); put("hasAudio", false)
                },
            )))
        }
        assertEquals(payload, roundTrip(payload))
    }

    @Test
    fun `integers stay integers`() {
        // Last-write-wins compares clientUpdatedAt values. If a Long came back as
        // a Double, two clients could disagree about which write is newer.
        val payload = buildJsonObject {
            put("clientUpdatedAt", 1_723_456_789_012L)
            put("syncVersion", 1)
        }
        val out = roundTrip(payload)
        assertEquals("1723456789012", out["clientUpdatedAt"]?.let { it as JsonPrimitive }?.content)
        assertTrue((out["clientUpdatedAt"] as JsonPrimitive).longOrNull == 1_723_456_789_012L)
        assertEquals(1, (out["syncVersion"] as JsonPrimitive).longOrNull)
    }

    @Test
    fun `non latin text is preserved`() {
        val payload = buildJsonObject { put("name", "أكاديمية عُبَدْ — الصفحة الرئيسية 📚") }
        assertEquals(payload, roundTrip(payload))
    }

    @Test
    fun `nulls round trip to null`() {
        val payload = buildJsonObject {
            put("maybe", JsonNull)
            put("list", JsonArray(listOf(JsonNull, JsonPrimitive("x"))))
        }
        val out = roundTrip(payload)
        assertEquals(JsonNull, out["maybe"])
        assertEquals(JsonArray(listOf(JsonNull, JsonPrimitive("x"))), out["list"])
    }

    @Test
    fun `empty and missing sections do not invent data`() {
        val out = roundTrip(buildJsonObject { })
        assertEquals(0, out.size)
        val partial = roundTrip(buildJsonObject { put("events", JsonArray(emptyList())) })
        assertEquals(setOf("events"), partial.keys)
    }

    @Test
    fun `a large realistic payload round trips inside the firestore budget`() {
        // 900 KB is the guard both clients enforce before writing; Firestore's own
        // ceiling is 1 MiB. This checks a big-but-legal payload survives intact.
        val notes = JsonArray((0 until 300).map { i ->
            buildJsonObject {
                put("id", "note-$i")
                put("title", "محاضرة $i")
                put("body", "أ".repeat(400))
                put("createdAt", 1_700_000_000_000L + i)
                put("updatedAt", 1_700_000_000_000L + i)
                put("pin", i % 3 == 0)
            }
        })
        val payload = buildJsonObject { put("notes", notes) }
        val bytes = json.encodeToString(JsonObject.serializer(), payload).toByteArray(Charsets.UTF_8).size
        assertTrue("fixture should be substantial, was $bytes B", bytes > 100_000)
        assertTrue("fixture must stay under the 900 KB guard, was $bytes B", bytes < 900_000)

        val restored = roundTrip(payload)
        assertEquals(notes, restored["notes"])
    }
}
