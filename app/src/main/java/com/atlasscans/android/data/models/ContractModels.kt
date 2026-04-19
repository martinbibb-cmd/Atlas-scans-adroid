package com.atlasscans.android.data.models

import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Spatial Alignment – AtlasSpatialModelV1
// ─────────────────────────────────────────────────────────────────────────────

/** Confidence level for a spatial position measurement. */
@Serializable
enum class AtlasPositionConfidence { confirmed, inferred }

/** Source of a spatial position reading. */
@Serializable
enum class AtlasPositionSource { lidar, manual, derived }

/**
 * Absolute world-space position in metres on the local site grid.
 *
 * Right-handed coordinate system matching ARCore:
 * - x = right
 * - y = up (height above the scan origin)
 * - z = towards the camera at session start
 */
@Serializable
data class AtlasWorldPosition(
    val x: Double,
    val y: Double,
    /** Vertical axis – height above the scan origin in metres. */
    val z: Double,
    val confidence: AtlasPositionConfidence,
    val source: AtlasPositionSource,
)

/**
 * A labelled object of interest (boiler, cylinder, consumer unit, etc.)
 * anchored at a known world position.
 */
@Serializable
data class AtlasAnchor(
    /** Unique identifier (UUID v4). */
    val id: String,
    /** Human-readable label, e.g. "boiler", "cylinder", "consumer_unit". */
    val label: String,
    val worldPosition: AtlasWorldPosition,
    /** Optional room this anchor belongs to. */
    val roomId: String? = null,
)

/**
 * Vertical spatial relationship between two anchors.
 *
 * [relation] values: "above" | "below" | "same_level"
 */
@Serializable
data class AtlasVerticalRelation(
    val fromAnchorId: String,
    val toAnchorId: String,
    val verticalDistanceM: Double,
    /** "above", "below", or "same_level" from the perspective of [fromAnchorId]. */
    val relation: String,
)

/**
 * An inferred pipe, cable, or flue route through world space.
 *
 * SAFETY: [confidence] is always "inferred". Never render inferred routes as
 * confirmed measurements. Always expose [reason] in the UI.
 */
@Serializable
data class AtlasInferredRoute(
    /** Unique identifier (UUID v4). */
    val id: String,
    /** "pipe", "cable", or "flue". */
    val type: String,
    /** Ordered world-space waypoints describing the route. */
    val path: List<AtlasWorldPosition>,
    /** Always "inferred" – never promote to confirmed without physical measurement. */
    val confidence: String = "inferred",
    /** Human-readable explanation of why this route was inferred. */
    val reason: String,
)

/** Optional geo-reference for the building scan origin. */
@Serializable
data class AtlasBuildingOrigin(
    val lat: Double? = null,
    val lng: Double? = null,
)

/**
 * Spatial model for the current site – anchors, vertical relationships,
 * and inferred routing paths.
 *
 * This is the ground-truth spatial layer consumed by the SpatialAlignmentEngine.
 */
@Serializable
data class AtlasSpatialModelV1(
    val anchors: List<AtlasAnchor> = emptyList(),
    val verticalRelations: List<AtlasVerticalRelation> = emptyList(),
    val inferredRoutes: List<AtlasInferredRoute> = emptyList(),
    val buildingOrigin: AtlasBuildingOrigin = AtlasBuildingOrigin(),
)

/**
 * 3-D coordinate in metres, right-handed (ARCore native convention).
 * Matches the spatial coordinate format required by AtlasContracts.
 */
@Serializable
data class SpatialCoordinate(
    val x: Float,
    val y: Float,
    val z: Float,
)

/**
 * A single detected plane (floor, ceiling, or wall) captured during the AR scan.
 */
@Serializable
data class DetectedPlane(
    /** Unique identifier for this plane. */
    val id: String,
    /**
     * Plane orientation type:
     * - "HORIZONTAL_UP"   – floor-like surface (normal pointing up)
     * - "HORIZONTAL_DOWN" – ceiling-like surface (normal pointing down)
     * - "VERTICAL"        – wall-like surface
     */
    val type: String,
    /** World-space centre of the plane polygon. */
    val centerPose: SpatialCoordinate,
    /** Half-extent along the plane's local X axis (metres). */
    val extentX: Float,
    /** Half-extent along the plane's local Z axis (metres). */
    val extentZ: Float,
)

/**
 * Full room-scan payload produced by the ARCore Depth API + plane detection.
 * Mirrors Apple's RoomPlan CapturedRoom for AtlasContracts binary compatibility.
 */
@Serializable
data class CapturedRoomScanV2(
    /** Unique identifier (UUID v4). */
    val id: String,
    /** Unix epoch milliseconds at scan completion. */
    val timestamp: Long,
    /**
     * Sampled point cloud in world space (metres).
     * Points are down-sampled to at most MAX_POINT_CLOUD_SIZE entries.
     */
    val pointCloud: List<SpatialCoordinate>,
    /** All planes tracked during the scan. */
    val detectedPlanes: List<DetectedPlane>,
    /** How long the user actively scanned (seconds). */
    val scanDurationSeconds: Float,
)

/**
 * A single high-resolution photo with optional spatial pin metadata.
 */
@Serializable
data class CapturedPhotoV2(
    /** Unique identifier (UUID v4). */
    val id: String,
    /** Absolute path on device storage. Excluded from the export bundle. */
    val filePath: String,
    /** Unix epoch milliseconds when the photo was taken. */
    val timestamp: Long,
    /**
     * ARCore hit-test coordinate where the pin was dropped, or null if
     * the photo was taken without a spatial anchor.
     */
    val coordinate: SpatialCoordinate?,
    /** Optional human-readable label for the pin. */
    val pinLabel: String?,
)

/**
 * A voice note.  The raw audio file is intentionally excluded from the
 * export bundle – only the transcript string is included, matching the iOS contract.
 */
@Serializable
data class VoiceNoteV2(
    /** Unique identifier (UUID v4). */
    val id: String,
    /** Speech-to-text transcript generated by Google SpeechRecognizer. */
    val transcript: String,
    /** Unix epoch milliseconds when recording started. */
    val timestamp: Long,
    /** Duration of the recording in seconds. */
    val durationSeconds: Float,
)

/**
 * Top-level capture session – the root object serialised and exported
 * as the JSON bundle consumed by Atlas Mind.
 */
@Serializable
data class SessionCaptureV2(
    /** Unique identifier (UUID v4). */
    val id: String,
    /** Unix epoch milliseconds when the session was created. */
    val createdAt: Long,
    /** The room scan, or null if scanning was not performed. */
    val roomScan: CapturedRoomScanV2?,
    /** All photos captured during the session. */
    val photos: List<CapturedPhotoV2>,
    /** All voice notes captured during the session. */
    val voiceNotes: List<VoiceNoteV2>,
    /**
     * Spatial model for this site – anchors, vertical relationships, and
     * inferred routing. Null until the engineer begins placing anchors.
     */
    val spatialModel: AtlasSpatialModelV1? = null,
)
