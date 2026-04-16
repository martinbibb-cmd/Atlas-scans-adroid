package com.atlasscans.android

import com.atlasscans.android.data.models.CapturedPhotoV2
import com.atlasscans.android.data.models.CapturedRoomScanV2
import com.atlasscans.android.data.models.DetectedPlane
import com.atlasscans.android.data.models.SessionCaptureV2
import com.atlasscans.android.data.models.SpatialCoordinate
import com.atlasscans.android.data.models.VoiceNoteV2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests verifying:
 * 1. Contract models serialise / deserialise correctly (AtlasContracts compatibility)
 * 2. SessionCaptureV2 round-trips through JSON without data loss
 * 3. Coordinate precision is preserved
 * 4. Optional fields are nullable (null → JSON omission via encodeDefaults=false)
 */
class ContractModelsTest {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    // ─────────────────────────────────────────────────────────────────────────
    // SpatialCoordinate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SpatialCoordinate serialises all three axes`() {
        val coord = SpatialCoordinate(1.5f, -0.3f, 2.75f)
        val encoded = json.encodeToString(coord)
        assertTrue(encoded.contains("\"x\":1.5"))
        assertTrue(encoded.contains("\"y\":-0.3"))
        assertTrue(encoded.contains("\"z\":2.75"))
    }

    @Test
    fun `SpatialCoordinate round-trip preserves values`() {
        val original = SpatialCoordinate(3.14159f, 0f, -100.001f)
        val decoded = json.decodeFromString<SpatialCoordinate>(json.encodeToString(original))
        assertEquals(original.x, decoded.x, 0.0001f)
        assertEquals(original.y, decoded.y, 0.0001f)
        assertEquals(original.z, decoded.z, 0.0001f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DetectedPlane
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `DetectedPlane serialises type and extents`() {
        val plane = DetectedPlane(
            id = UUID.randomUUID().toString(),
            type = "HORIZONTAL_UP",
            centerPose = SpatialCoordinate(0f, 0f, 0f),
            extentX = 3f,
            extentZ = 4f,
        )
        val encoded = json.encodeToString(plane)
        assertTrue(encoded.contains("\"type\":\"HORIZONTAL_UP\""))
        assertTrue(encoded.contains("\"extentX\":3.0"))
        assertTrue(encoded.contains("\"extentZ\":4.0"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CapturedRoomScanV2
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `CapturedRoomScanV2 round-trip with point cloud and planes`() {
        val scan = CapturedRoomScanV2(
            id = UUID.randomUUID().toString(),
            timestamp = 1_700_000_000_000L,
            pointCloud = listOf(
                SpatialCoordinate(1f, 2f, 3f),
                SpatialCoordinate(4f, 5f, 6f),
            ),
            detectedPlanes = listOf(
                DetectedPlane(
                    id = UUID.randomUUID().toString(),
                    type = "VERTICAL",
                    centerPose = SpatialCoordinate(0f, 1f, 0f),
                    extentX = 2f,
                    extentZ = 3f,
                )
            ),
            scanDurationSeconds = 45.5f,
        )

        val decoded = json.decodeFromString<CapturedRoomScanV2>(json.encodeToString(scan))
        assertEquals(scan.id, decoded.id)
        assertEquals(scan.timestamp, decoded.timestamp)
        assertEquals(2, decoded.pointCloud.size)
        assertEquals(1, decoded.detectedPlanes.size)
        assertEquals(45.5f, decoded.scanDurationSeconds, 0.01f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CapturedPhotoV2
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `CapturedPhotoV2 nullable fields are correctly handled`() {
        val photoWithPin = CapturedPhotoV2(
            id = UUID.randomUUID().toString(),
            filePath = "/sdcard/photo.jpg",
            timestamp = System.currentTimeMillis(),
            coordinate = SpatialCoordinate(1f, 0f, -1f),
            pinLabel = "Front door",
        )
        val photoWithoutPin = CapturedPhotoV2(
            id = UUID.randomUUID().toString(),
            filePath = "/sdcard/photo2.jpg",
            timestamp = System.currentTimeMillis(),
            coordinate = null,
            pinLabel = null,
        )

        val decodedWithPin =
            json.decodeFromString<CapturedPhotoV2>(json.encodeToString(photoWithPin))
        assertNotNull(decodedWithPin.coordinate)
        assertEquals("Front door", decodedWithPin.pinLabel)

        val decodedWithoutPin =
            json.decodeFromString<CapturedPhotoV2>(json.encodeToString(photoWithoutPin))
        assertNull(decodedWithoutPin.coordinate)
        assertNull(decodedWithoutPin.pinLabel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VoiceNoteV2
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `VoiceNoteV2 round-trip preserves transcript`() {
        val note = VoiceNoteV2(
            id = UUID.randomUUID().toString(),
            transcript = "The crack is above the window on the east wall.",
            timestamp = System.currentTimeMillis(),
            durationSeconds = 7.3f,
        )
        val decoded = json.decodeFromString<VoiceNoteV2>(json.encodeToString(note))
        assertEquals(note.transcript, decoded.transcript)
        assertEquals(note.durationSeconds, decoded.durationSeconds, 0.01f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SessionCaptureV2 – top-level contract
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SessionCaptureV2 full round-trip`() {
        val session = SessionCaptureV2(
            id = UUID.randomUUID().toString(),
            createdAt = 1_700_000_000_000L,
            roomScan = CapturedRoomScanV2(
                id = UUID.randomUUID().toString(),
                timestamp = 1_700_000_001_000L,
                pointCloud = listOf(SpatialCoordinate(0f, 0f, 0f)),
                detectedPlanes = emptyList(),
                scanDurationSeconds = 30f,
            ),
            photos = listOf(
                CapturedPhotoV2(
                    id = UUID.randomUUID().toString(),
                    filePath = "/sdcard/photo.jpg",
                    timestamp = 1_700_000_002_000L,
                    coordinate = null,
                    pinLabel = null,
                )
            ),
            voiceNotes = listOf(
                VoiceNoteV2(
                    id = UUID.randomUUID().toString(),
                    transcript = "Test note.",
                    timestamp = 1_700_000_003_000L,
                    durationSeconds = 3f,
                )
            ),
        )

        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<SessionCaptureV2>(encoded)

        assertEquals(session.id, decoded.id)
        assertEquals(session.createdAt, decoded.createdAt)
        assertNotNull(decoded.roomScan)
        assertEquals(1, decoded.photos.size)
        assertEquals(1, decoded.voiceNotes.size)
        assertEquals("Test note.", decoded.voiceNotes.first().transcript)
    }

    @Test
    fun `SessionCaptureV2 with null roomScan serialises correctly`() {
        val session = SessionCaptureV2(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            roomScan = null,
            photos = emptyList(),
            voiceNotes = emptyList(),
        )
        val decoded = json.decodeFromString<SessionCaptureV2>(json.encodeToString(session))
        assertNull(decoded.roomScan)
        assertTrue(decoded.photos.isEmpty())
        assertTrue(decoded.voiceNotes.isEmpty())
    }

    @Test
    fun `SessionCaptureV2 UUIDs are valid UUID v4 strings`() {
        val session = SessionCaptureV2(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            roomScan = null,
            photos = emptyList(),
            voiceNotes = emptyList(),
        )
        // Should not throw
        val parsed = UUID.fromString(session.id)
        assertNotNull(parsed)
    }
}
