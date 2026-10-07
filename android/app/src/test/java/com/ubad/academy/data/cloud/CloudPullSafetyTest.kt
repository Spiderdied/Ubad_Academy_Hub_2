package com.ubad.academy.data.cloud

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ubad.academy.data.local.db.CourseAssetEntity
import com.ubad.academy.data.local.db.NoteAttachmentEntity
import com.ubad.academy.data.local.db.NoteEntity
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.data.repository.IslamRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves the two data-safety properties a cloud pull depends on, instead of
 * relying on code inspection:
 *
 *  1. Applying a cloud payload **keeps note attachment rows**. The payload carries
 *     note metadata only — the binaries live in Backblaze B2 and are fetched
 *     separately — so a pull that dropped attachment rows would make the user's
 *     images and audio disappear from the note until (or unless) the file
 *     download succeeded. Attachment rows are captured before `clearNotes()`
 *     precisely because `note_attachments` cascades on that delete.
 *
 *  2. Applying a cloud payload **keeps course asset rows**. This is the
 *     deliberate difference from a backup restore: `BackupCodec.restoreCourses`
 *     clears the asset table and directory because a backup carries its own
 *     copies, whereas a cloud payload does not.
 *
 * Both were previously "verified by reading". The payloads below deliberately omit
 * `user` / `settings` / `focus` so the test exercises only the two paths in
 * question and never touches DataStore.
 */
@RunWith(RobolectricTestRunner::class)
class CloudPullSafetyTest {

    private lateinit var db: UbadDatabase
    private lateinit var codec: CloudPayloadCodec

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UbadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        codec = CloudPayloadCodec(db, SettingsStore(context), IslamRepository(db))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `cloud pull keeps note attachments while applying note metadata`() = runTest {
        db.notes().replaceNote(
            NoteEntity(
                id = "n1", title = "Local title", body = "local body", tagsJson = "[]",
                pin = false, createdAt = 1_000L, updatedAt = 1_000L,
            ),
            listOf(
                NoteAttachmentEntity("f-img", "n1", "image", "photo.png", "image/png", 0),
                NoteAttachmentEntity("f-aud", "n1", "audio", "voice.m4a", "audio/mp4", 1000),
            ),
        )

        // Exactly what the cloud payload contains for a note: metadata + flags.
        codec.apply(
            buildJsonObject {
                put(
                    "notes",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", "n1")
                                put("title", "Cloud title")
                                put("body", "cloud body")
                                put("tags", buildJsonArray { add(JsonPrimitive("cloud")) })
                                put("pin", true)
                                put("createdAt", 1_000L)
                                put("updatedAt", 2_000L)
                                put("hasImages", true)
                                put("hasAudio", true)
                            },
                        )
                    },
                )
            },
        )

        val note = db.notes().note("n1")
        assertNotNull("note must still exist after the pull", note)
        assertEquals("cloud title/body must be applied", "Cloud title", note!!.title)
        assertEquals("cloud body", note.body)
        assertEquals(2_000L, note.updatedAt)
        assertTrue("pin flag must be applied", note.pin)

        val attachments = db.notes().attachmentsOf("n1")
        assertEquals(
            "both attachment rows must survive the pull",
            listOf("f-aud", "f-img"),
            attachments.map { it.fileId }.sorted(),
        )
        assertEquals(
            "image name must be preserved",
            "photo.png",
            attachments.first { it.kind == "image" }.name,
        )
        assertEquals(
            "audio position (1000 + index) must be preserved",
            1000,
            attachments.first { it.kind == "audio" }.position,
        )
    }

    @Test
    fun `cloud pull keeps course assets while replacing course structure`() = runTest {
        db.courses().upsertAsset(CourseAssetEntity("a1", "application/pdf", 4_096L))
        assertEquals(1, db.courses().assets().size)

        codec.apply(
            buildJsonObject {
                put(
                    "courses",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", "c1")
                                put("name", "Cloud Course")
                                put("code", "CLD101")
                                put("instructor", "Dr. Cloud")
                                put("credits", 3)
                                put("semester", "Fall")
                                put("createdAt", 1_000L)
                                put(
                                    "units",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", "u1")
                                                put("title", "Cloud Unit")
                                                put(
                                                    "contents",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("id", "x1")
                                                                put("type", "pdf")
                                                                put("title", "Cloud PDF")
                                                                put("assetId", "a1")
                                                                put("name", "cloud.pdf")
                                                                put("mime", "application/pdf")
                                                                put("done", false)
                                                                put("createdAt", 1_000L)
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        // Structure from the cloud is applied...
        assertNotNull("course from the payload must exist", db.courses().course("c1"))
        assertEquals(1, db.courses().unitsOf("c1").size)
        assertEquals("x1", db.courses().contentsOf("u1").single().id)

        // ...but the local asset row (whose bytes live in filesDir/B2) is NOT cleared.
        assertEquals(
            "course assets must survive a cloud pull (no clearAssets on this path)",
            1,
            db.courses().assets().size,
        )
        val asset = db.courses().asset("a1")
        assertNotNull("the asset row itself must still be readable", asset)
        assertEquals("application/pdf", asset!!.mime)
        assertEquals(4_096L, asset.size)
    }
}
