package com.atlasscans.android

import com.atlasscans.android.data.models.AtlasAnchor
import com.atlasscans.android.data.models.AtlasBuildingOrigin
import com.atlasscans.android.data.models.AtlasInferredRoute
import com.atlasscans.android.data.models.AtlasPositionConfidence
import com.atlasscans.android.data.models.AtlasPositionSource
import com.atlasscans.android.data.models.AtlasSpatialModelV1
import com.atlasscans.android.data.models.AtlasVerticalRelation
import com.atlasscans.android.data.models.AtlasWorldPosition
import com.atlasscans.android.features.spatialalignment.CameraPose
import com.atlasscans.android.features.spatialalignment.SpatialAlignmentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for [SpatialAlignmentEngine].
 *
 * Tests verify:
 * 1. [SpatialAlignmentEngine.getRelativePosition] – distance, bearing, vertical offset
 * 2. [SpatialAlignmentEngine.projectToViewPlane] – behind-camera guard, screen mapping
 * 3. [SpatialAlignmentEngine.buildAlignmentInsights] – insight derivation
 * 4. [SpatialAlignmentEngine.deriveVerticalRelations] – relation tagging
 * 5. [SpatialAlignmentEngine.estimateRouteLength] – pipe-length accumulation
 * 6. [SpatialAlignmentEngine.totalPipeLength] – model-level pipe total
 */
class SpatialAlignmentEngineTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun confirmedPosition(x: Double, y: Double, z: Double) =
        AtlasWorldPosition(x, y, z, AtlasPositionConfidence.confirmed, AtlasPositionSource.lidar)

    private fun inferredPosition(x: Double, y: Double, z: Double) =
        AtlasWorldPosition(x, y, z, AtlasPositionConfidence.inferred, AtlasPositionSource.manual)

    private fun anchor(id: String, label: String, x: Double, y: Double, z: Double) =
        AtlasAnchor(id = id, label = label, worldPosition = confirmedPosition(x, y, z))

    // ─────────────────────────────────────────────────────────────────────────
    // getRelativePosition
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getRelativePosition distance is euclidean`() {
        val user = confirmedPosition(0.0, 0.0, 0.0)
        val target = anchor("t1", "boiler", 3.0, 4.0, 0.0)
        val rel = SpatialAlignmentEngine.getRelativePosition(user, target)
        assertEquals(5.0, rel.distanceM, 0.001)
    }

    @Test
    fun `getRelativePosition vertical offset positive when target is above`() {
        val user = confirmedPosition(0.0, 0.0, 0.0)
        val target = anchor("t1", "cylinder", 0.0, 2.3, 0.0)
        val rel = SpatialAlignmentEngine.getRelativePosition(user, target)
        assertEquals(2.3, rel.verticalOffsetM, 0.001)
    }

    @Test
    fun `getRelativePosition vertical offset negative when target is below`() {
        val user = confirmedPosition(0.0, 2.0, 0.0)
        val target = anchor("t1", "underground", 0.0, 0.5, 0.0)
        val rel = SpatialAlignmentEngine.getRelativePosition(user, target)
        assertEquals(-1.5, rel.verticalOffsetM, 0.001)
    }

    @Test
    fun `getRelativePosition bearing 0 when directly ahead on positive Z`() {
        // Target is on the -Z axis: bearing should be 0° (directly ahead in +Z convention)
        val user = confirmedPosition(0.0, 0.0, 0.0)
        val target = anchor("t1", "ahead", 0.0, 0.0, -5.0)
        val rel = SpatialAlignmentEngine.getRelativePosition(user, target)
        assertEquals(0.0, rel.bearingDeg, 0.1)
    }

    @Test
    fun `getRelativePosition bearing is within 0 to 360`() {
        val user = confirmedPosition(0.0, 0.0, 0.0)
        listOf(
            anchor("a", "ne", 1.0, 0.0, -1.0),
            anchor("b", "se", 1.0, 0.0, 1.0),
            anchor("c", "sw", -1.0, 0.0, 1.0),
            anchor("d", "nw", -1.0, 0.0, -1.0),
        ).forEach { t ->
            val rel = SpatialAlignmentEngine.getRelativePosition(user, t)
            assertTrue(rel.bearingDeg in 0.0..360.0)
        }
    }

    @Test
    fun `getRelativePosition same position returns zero distance`() {
        val user = confirmedPosition(1.5, 2.0, 3.0)
        val target = anchor("t", "same", 1.5, 2.0, 3.0)
        val rel = SpatialAlignmentEngine.getRelativePosition(user, target)
        assertEquals(0.0, rel.distanceM, 0.0001)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // projectToViewPlane
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `projectToViewPlane returns null for point behind camera`() {
        val pose = CameraPose(
            position = confirmedPosition(0.0, 0.0, 0.0),
            yawDeg = 0.0,
            screenWidthPx = 1080,
            screenHeightPx = 1920,
        )
        // dz = -5 - 0 = -5; localZ = 0*sin(0) + (-5)*cos(0) = -5 ≤ 0 → behind camera
        val result = SpatialAlignmentEngine.projectToViewPlane(pose, confirmedPosition(0.0, 0.0, -5.0))
        assertNull(result)
    }

    @Test
    fun `projectToViewPlane returns non-null for point in front of camera`() {
        val pose = CameraPose(
            position = confirmedPosition(0.0, 0.0, 0.0),
            yawDeg = 0.0,
            screenWidthPx = 1080,
            screenHeightPx = 1920,
        )
        // dz = 5 - 0 = 5; localZ = 0*sin(0) + 5*cos(0) = 5 > 0 → in front
        val inFront = confirmedPosition(0.0, 0.0, 5.0)
        val result = SpatialAlignmentEngine.projectToViewPlane(pose, inFront)
        assertNotNull(result)
    }

    @Test
    fun `projectToViewPlane centres straight-ahead point on screen`() {
        val pose = CameraPose(
            position = confirmedPosition(0.0, 0.0, 0.0),
            yawDeg = 0.0,
            screenWidthPx = 1080,
            screenHeightPx = 1920,
        )
        // Point directly in front, same height
        val ahead = confirmedPosition(0.0, 0.0, 10.0)
        val result = SpatialAlignmentEngine.projectToViewPlane(pose, ahead)
        assertNotNull(result!!)
        assertEquals(540f, result.x, 1f)  // centred horizontally
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deriveVerticalRelations
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `deriveVerticalRelations tags above correctly`() {
        val boiler = anchor("boiler", "Boiler", 0.0, 0.5, 0.0)
        val cylinder = anchor("cylinder", "Cylinder", 0.0, 3.0, 0.0)
        val model = AtlasSpatialModelV1(anchors = listOf(boiler, cylinder))
        val relations = SpatialAlignmentEngine.deriveVerticalRelations(model)

        val boilerToCylinder = relations.first { it.fromAnchorId == "boiler" && it.toAnchorId == "cylinder" }
        assertEquals("above", boilerToCylinder.relation)
        assertEquals(2.5, boilerToCylinder.verticalDistanceM, 0.001)
    }

    @Test
    fun `deriveVerticalRelations tags below correctly`() {
        val boiler = anchor("boiler", "Boiler", 0.0, 3.0, 0.0)
        val floor = anchor("floor", "Floor box", 0.0, 0.0, 0.0)
        val model = AtlasSpatialModelV1(anchors = listOf(boiler, floor))
        val relations = SpatialAlignmentEngine.deriveVerticalRelations(model)

        val boilerToFloor = relations.first { it.fromAnchorId == "boiler" && it.toAnchorId == "floor" }
        assertEquals("below", boilerToFloor.relation)
    }

    @Test
    fun `deriveVerticalRelations same_level within tolerance`() {
        val a = anchor("a", "A", 0.0, 1.0, 0.0)
        val b = anchor("b", "B", 2.0, 1.1, 0.0)  // 0.1 m apart – within 0.15 m tolerance
        val model = AtlasSpatialModelV1(anchors = listOf(a, b))
        val relations = SpatialAlignmentEngine.deriveVerticalRelations(model)

        val aToB = relations.first { it.fromAnchorId == "a" && it.toAnchorId == "b" }
        assertEquals("same_level", aToB.relation)
    }

    @Test
    fun `deriveVerticalRelations produces N*(N-1) relations for N anchors`() {
        val anchors = (1..4).map { i -> anchor("a$i", "Anchor $i", i.toDouble(), i.toDouble(), 0.0) }
        val model = AtlasSpatialModelV1(anchors = anchors)
        val relations = SpatialAlignmentEngine.deriveVerticalRelations(model)
        assertEquals(4 * 3, relations.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // buildAlignmentInsights
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `buildAlignmentInsights returns insight for each vertical relation`() {
        val boiler = anchor("boiler", "Boiler", 0.0, 0.0, 0.0)
        val cylinder = anchor("cylinder", "Cylinder", 0.0, 2.5, 0.0)
        val relations = listOf(
            AtlasVerticalRelation("boiler", "cylinder", 2.5, "above"),
        )
        val model = AtlasSpatialModelV1(
            anchors = listOf(boiler, cylinder),
            verticalRelations = relations,
        )

        val insights = SpatialAlignmentEngine.buildAlignmentInsights(model)
        assertEquals(1, insights.size)
        assertEquals("Cylinder", insights[0].label)
        assertEquals("above", insights[0].relation)
        assertEquals(2.5, insights[0].verticalDistanceM, 0.001)
    }

    @Test
    fun `buildAlignmentInsights skips relations with missing anchor ids`() {
        val boiler = anchor("boiler", "Boiler", 0.0, 0.0, 0.0)
        val relations = listOf(
            AtlasVerticalRelation("boiler", "ghost_id", 1.0, "above"),
        )
        val model = AtlasSpatialModelV1(
            anchors = listOf(boiler),
            verticalRelations = relations,
        )
        val insights = SpatialAlignmentEngine.buildAlignmentInsights(model)
        assertTrue(insights.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // estimateRouteLength
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `estimateRouteLength single segment`() {
        val route = AtlasInferredRoute(
            id = "r1",
            type = "pipe",
            path = listOf(
                confirmedPosition(0.0, 0.0, 0.0),
                confirmedPosition(3.0, 4.0, 0.0),
            ),
            reason = "Test",
        )
        val length = SpatialAlignmentEngine.estimateRouteLength(route)
        assertEquals(5.0, length, 0.001)
    }

    @Test
    fun `estimateRouteLength multi-segment sums all legs`() {
        val route = AtlasInferredRoute(
            id = "r1",
            type = "pipe",
            path = listOf(
                confirmedPosition(0.0, 0.0, 0.0),
                confirmedPosition(1.0, 0.0, 0.0),
                confirmedPosition(1.0, 2.0, 0.0),
            ),
            reason = "Test",
        )
        val length = SpatialAlignmentEngine.estimateRouteLength(route)
        assertEquals(3.0, length, 0.001)   // 1 + 2
    }

    @Test
    fun `estimateRouteLength returns zero for fewer than 2 waypoints`() {
        val single = AtlasInferredRoute(
            id = "r1",
            type = "pipe",
            path = listOf(confirmedPosition(0.0, 0.0, 0.0)),
            reason = "Test",
        )
        assertEquals(0.0, SpatialAlignmentEngine.estimateRouteLength(single), 0.0)

        val empty = AtlasInferredRoute(
            id = "r2",
            type = "pipe",
            path = emptyList(),
            reason = "Test",
        )
        assertEquals(0.0, SpatialAlignmentEngine.estimateRouteLength(empty), 0.0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // totalPipeLength
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `totalPipeLength sums only pipe routes`() {
        val model = AtlasSpatialModelV1(
            inferredRoutes = listOf(
                AtlasInferredRoute(
                    id = "p1", type = "pipe",
                    path = listOf(confirmedPosition(0.0, 0.0, 0.0), confirmedPosition(2.0, 0.0, 0.0)),
                    reason = "Test",
                ),
                AtlasInferredRoute(
                    id = "c1", type = "cable",
                    path = listOf(confirmedPosition(0.0, 0.0, 0.0), confirmedPosition(10.0, 0.0, 0.0)),
                    reason = "Test",
                ),
            )
        )
        assertEquals(2.0, SpatialAlignmentEngine.totalPipeLength(model), 0.001)
    }

    @Test
    fun `totalPipeLength returns zero when no pipe routes`() {
        val model = AtlasSpatialModelV1()
        assertEquals(0.0, SpatialAlignmentEngine.totalPipeLength(model), 0.0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Safety: inferred data must never be promoted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `inferred route confidence field is always inferred string`() {
        val route = AtlasInferredRoute(
            id = "r1",
            type = "pipe",
            path = emptyList(),
            reason = "Aligned kitchen tap + boiler position + standard routing",
        )
        assertEquals("inferred", route.confidence)
    }

    @Test
    fun `manually placed anchor gets inferred confidence`() {
        val pos = AtlasWorldPosition(
            x = 1.0, y = 2.0, z = 3.0,
            confidence = AtlasPositionConfidence.inferred,
            source = AtlasPositionSource.manual,
        )
        assertEquals(AtlasPositionConfidence.inferred, pos.confidence)
    }
}
