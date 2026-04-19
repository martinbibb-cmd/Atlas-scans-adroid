package com.atlasscans.android.features.spatialalignment

import com.atlasscans.android.data.models.AtlasAnchor
import com.atlasscans.android.data.models.AtlasInferredRoute
import com.atlasscans.android.data.models.AtlasPositionConfidence
import com.atlasscans.android.data.models.AtlasSpatialModelV1
import com.atlasscans.android.data.models.AtlasVerticalRelation
import com.atlasscans.android.data.models.AtlasWorldPosition
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// ─────────────────────────────────────────────────────────────────────────────
// Supporting types
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Position of a target anchor relative to the observer's position.
 *
 * - [distanceM] – straight-line distance in metres
 * - [bearingDeg] – horizontal bearing (0° = +Z axis, clockwise) in degrees
 * - [verticalOffsetM] – positive means the target is **above** the observer
 */
data class RelativePosition(
    val distanceM: Double,
    val bearingDeg: Double,
    val verticalOffsetM: Double,
)

/** A 2-D on-screen position in pixels, origin top-left. */
data class ScreenPosition(
    val x: Float,
    val y: Float,
)

/**
 * Derived insight describing the spatial relationship between two anchors.
 * Safe to display in the UI – confidence is propagated from the anchor data.
 *
 * SAFETY: [confidence] must always be surfaced to the user.
 * Never render [AtlasPositionConfidence.inferred] data as confirmed facts.
 */
data class AlignmentInsight(
    val anchorId: String,
    val label: String,
    /** "above", "below", or "same_level" relative to the reference anchor. */
    val relation: String,
    val verticalDistanceM: Double,
    val horizontalOffsetM: Double,
    val confidence: AtlasPositionConfidence,
)

/**
 * Minimal camera pose for projecting world positions to screen coordinates.
 *
 * For a full AR overlay, replace this with ARCore's [com.google.ar.core.Pose]
 * matrices directly. This simplified pose is used for the 2-D structure view.
 *
 * @param position    Observer's world position.
 * @param yawDeg      Rotation around the Y (vertical) axis, degrees.
 * @param screenWidthPx  Viewport width in pixels.
 * @param screenHeightPx Viewport height in pixels.
 * @param hFovDeg     Horizontal field-of-view, degrees (default 60°).
 */
data class CameraPose(
    val position: AtlasWorldPosition,
    val yawDeg: Double,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val hFovDeg: Double = 60.0,
)

// ─────────────────────────────────────────────────────────────────────────────
// Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Spatial Alignment Engine – converts an [AtlasSpatialModelV1] into relative
 * positioning data and visual insights for the Alignment View / Structure View.
 *
 * Coordinate system: right-handed, metres.
 *   x = right, y = up (height), z = into the scene (away from camera origin)
 */
object SpatialAlignmentEngine {

    /**
     * Calculate the position of [target] relative to [userPosition].
     *
     * - [RelativePosition.bearingDeg]: measured clockwise from the +Z axis so
     *   that 0° is directly ahead (standard compass-like convention for a
     *   right-handed XZ horizontal plane).
     */
    fun getRelativePosition(
        userPosition: AtlasWorldPosition,
        target: AtlasAnchor,
    ): RelativePosition {
        val dx = target.worldPosition.x - userPosition.x
        val dy = target.worldPosition.y - userPosition.y   // vertical
        val dz = target.worldPosition.z - userPosition.z

        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        // Bearing: atan2(x, -z) gives angle clockwise from +Z in the XZ plane.
        val bearingRad = atan2(dx, -dz)
        val bearingDeg = Math.toDegrees(bearingRad).let { if (it < 0) it + 360.0 else it }

        return RelativePosition(
            distanceM = distance,
            bearingDeg = bearingDeg,
            verticalOffsetM = dy,
        )
    }

    /**
     * Project [worldPosition] to 2-D screen coordinates using a simplified
     * pinhole-camera model given [cameraPose].
     *
     * Returns `null` when [worldPosition] is behind the camera plane.
     *
     * Note: for full AR rendering use ARCore's Pose matrices directly via
     * [com.google.ar.core.Session] frame rendering. This projection is
     * sufficient for the 2-D Structure View overlay.
     */
    fun projectToViewPlane(
        cameraPose: CameraPose,
        worldPosition: AtlasWorldPosition,
    ): ScreenPosition? {
        val dx = worldPosition.x - cameraPose.position.x
        val dz = worldPosition.z - cameraPose.position.z
        val dy = worldPosition.y - cameraPose.position.y

        // Rotate into camera-local space using yaw.
        val yawRad = Math.toRadians(cameraPose.yawDeg)
        val localX = dx * cos(yawRad) - dz * sin(yawRad)
        val localZ = dx * sin(yawRad) + dz * cos(yawRad)

        // Points behind the camera are not visible.
        if (localZ <= 0.0) return null

        val tanHalfHFov = tan(Math.toRadians(cameraPose.hFovDeg / 2.0))

        // NDC in [-1, 1]; approximate vertical FOV from aspect ratio.
        val aspect = cameraPose.screenWidthPx.toDouble() / cameraPose.screenHeightPx
        val ndcX = (localX / localZ) / tanHalfHFov
        val ndcY = (dy / localZ) / (tanHalfHFov / aspect)

        val screenX = ((ndcX + 1.0) / 2.0 * cameraPose.screenWidthPx).toFloat()
        val screenY = ((1.0 - (ndcY + 1.0) / 2.0) * cameraPose.screenHeightPx).toFloat()

        return ScreenPosition(screenX, screenY)
    }

    /**
     * Build a list of [AlignmentInsight]s from the vertical relations already
     * stored in [model].
     *
     * Each insight relates a target anchor to its reference anchor with
     * human-readable distance and direction data, suitable for both the
     * AR overlay labels and the 2-D Structure View list.
     */
    fun buildAlignmentInsights(model: AtlasSpatialModelV1): List<AlignmentInsight> {
        val anchorById = model.anchors.associateBy { it.id }
        return model.verticalRelations.mapNotNull { rel ->
            val to = anchorById[rel.toAnchorId] ?: return@mapNotNull null
            val from = anchorById[rel.fromAnchorId] ?: return@mapNotNull null

            val dx = to.worldPosition.x - from.worldPosition.x
            val dz = to.worldPosition.z - from.worldPosition.z
            val horizontalOffset = sqrt(dx * dx + dz * dz)

            AlignmentInsight(
                anchorId = to.id,
                label = to.label,
                relation = rel.relation,
                verticalDistanceM = rel.verticalDistanceM,
                horizontalOffsetM = horizontalOffset,
                confidence = to.worldPosition.confidence,
            )
        }
    }

    /**
     * Automatically derive [AtlasVerticalRelation]s by comparing the Y
     * (height) coordinates of every anchor pair in [model].
     *
     * Anchors within [sameLevelToleranceM] metres vertically are treated as
     * "same_level". One relation is emitted per **ordered** pair (A→B).
     *
     * Intended to be called after all anchors have been placed, before the
     * user reviews the Structure View. The result can be stored back into the
     * model to drive [buildAlignmentInsights].
     */
    fun deriveVerticalRelations(
        model: AtlasSpatialModelV1,
        sameLevelToleranceM: Double = 0.15,
    ): List<AtlasVerticalRelation> {
        val anchors = model.anchors
        return buildList {
            for (i in anchors.indices) {
                for (j in anchors.indices) {
                    if (i == j) continue
                    val a = anchors[i]
                    val b = anchors[j]
                    val vertDiff = b.worldPosition.y - a.worldPosition.y
                    val relation = when {
                        abs(vertDiff) <= sameLevelToleranceM -> "same_level"
                        vertDiff > 0 -> "above"
                        else -> "below"
                    }
                    add(
                        AtlasVerticalRelation(
                            fromAnchorId = a.id,
                            toAnchorId = b.id,
                            verticalDistanceM = abs(vertDiff),
                            relation = relation,
                        )
                    )
                }
            }
        }
    }

    /**
     * Estimate the total length of [route] by summing the straight-line
     * distances between consecutive waypoints in metres.
     *
     * Used to feed pipe-length estimates into the hydraulic / heat-loss modules.
     * Returns 0.0 for routes with fewer than 2 waypoints.
     */
    fun estimateRouteLength(route: AtlasInferredRoute): Double {
        if (route.path.size < 2) return 0.0
        return route.path.zipWithNext().sumOf { (a, b) ->
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dz = b.z - a.z
            sqrt(dx * dx + dy * dy + dz * dz)
        }
    }

    /**
     * Estimate total pipe length from all inferred "pipe" routes in [model].
     *
     * Feeds directly into hydraulic pump-head and heat-loss calculations.
     * Returns 0.0 when no pipe routes are present.
     */
    fun totalPipeLength(model: AtlasSpatialModelV1): Double =
        model.inferredRoutes
            .filter { it.type == "pipe" && it.confidence == "inferred" }
            .sumOf { estimateRouteLength(it) }
}
