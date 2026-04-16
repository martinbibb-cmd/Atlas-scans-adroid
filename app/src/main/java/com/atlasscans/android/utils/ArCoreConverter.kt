package com.atlasscans.android.utils

import com.google.ar.core.Plane
import com.google.ar.core.PointCloud
import com.atlasscans.android.data.models.CapturedRoomScanV2
import com.atlasscans.android.data.models.DetectedPlane
import com.atlasscans.android.data.models.SpatialCoordinate
import java.util.UUID

/** Maximum number of depth points retained in the exported point cloud. */
private const val MAX_POINT_CLOUD_SIZE = 2_000

/**
 * Converts ARCore raw data (point clouds + detected planes) into the
 * [CapturedRoomScanV2] format required by the AtlasContracts schema.
 *
 * **Coordinate system note:** ARCore uses a right-handed coordinate system
 * with Y-up (same convention as the AtlasContracts spec).  No axis flipping
 * is required; coordinates are already in metres.
 */
object ArCoreConverter {

    /**
     * Build a [CapturedRoomScanV2] from the cumulative list of point-cloud
     * snapshots and the final set of tracked planes.
     *
     * @param pointCloudSnapshots All [PointCloud] frames captured during the scan.
     *   Each frame's points are in world space (metres).
     * @param trackedPlanes        All [Plane] objects tracked by ARCore at scan end.
     * @param scanDurationSeconds  Duration of the active scan in seconds.
     */
    fun buildRoomScan(
        pointCloudSnapshots: List<PointCloud>,
        trackedPlanes: Collection<Plane>,
        scanDurationSeconds: Float,
    ): CapturedRoomScanV2 {
        val pointCloud = collectPoints(pointCloudSnapshots)
        val planes = trackedPlanes
            .filter { it.trackingState == com.google.ar.core.TrackingState.TRACKING }
            .map { plane -> convertPlane(plane) }

        return CapturedRoomScanV2(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            pointCloud = pointCloud,
            detectedPlanes = planes,
            scanDurationSeconds = scanDurationSeconds,
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Flatten all point-cloud snapshots into a de-duplicated, down-sampled
     * list of [SpatialCoordinate] values.
     *
     * ARCore point cloud buffers contain (X, Y, Z, confidence) floats packed
     * into a single [java.nio.FloatBuffer].  Each point occupies 4 floats.
     */
    private fun collectPoints(snapshots: List<PointCloud>): List<SpatialCoordinate> {
        val seen = LinkedHashSet<Triple<Float, Float, Float>>()

        for (cloud in snapshots) {
            val buf = cloud.points   // FloatBuffer: x, y, z, confidence per point
            buf.rewind()
            while (buf.remaining() >= 4) {
                val x = buf.get()
                val y = buf.get()
                val z = buf.get()
                buf.get()           // skip confidence
                seen.add(Triple(x, y, z))
                if (seen.size >= MAX_POINT_CLOUD_SIZE) break
            }
            if (seen.size >= MAX_POINT_CLOUD_SIZE) break
        }

        return seen.map { (x, y, z) -> SpatialCoordinate(x, y, z) }
    }

    /**
     * Map an ARCore [Plane] to a [DetectedPlane].
     *
     * Plane types:
     * - [Plane.Type.HORIZONTAL_UPWARD_FACING]   → "HORIZONTAL_UP"
     * - [Plane.Type.HORIZONTAL_DOWNWARD_FACING] → "HORIZONTAL_DOWN"
     * - [Plane.Type.VERTICAL]                   → "VERTICAL"
     */
    private fun convertPlane(plane: Plane): DetectedPlane {
        val pose = plane.centerPose
        val center = SpatialCoordinate(
            x = pose.tx(),
            y = pose.ty(),
            z = pose.tz(),
        )
        val type = when (plane.type) {
            Plane.Type.HORIZONTAL_UPWARD_FACING   -> "HORIZONTAL_UP"
            Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "HORIZONTAL_DOWN"
            Plane.Type.VERTICAL                   -> "VERTICAL"
        }
        return DetectedPlane(
            id = UUID.randomUUID().toString(),
            type = type,
            centerPose = center,
            extentX = plane.extentX,
            extentZ = plane.extentZ,
        )
    }
}
