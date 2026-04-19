package com.atlasscans.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlasscans.android.R
import com.atlasscans.android.data.models.AtlasAnchor
import com.atlasscans.android.data.models.AtlasBuildingOrigin
import com.atlasscans.android.data.models.AtlasPositionConfidence
import com.atlasscans.android.data.models.AtlasPositionSource
import com.atlasscans.android.data.models.AtlasSpatialModelV1
import com.atlasscans.android.data.models.AtlasWorldPosition
import com.atlasscans.android.features.spatialalignment.AlignmentInsight
import com.atlasscans.android.features.spatialalignment.SpatialAlignmentEngine
import com.atlasscans.android.viewmodel.SessionViewModel
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val CONFIRMED_COLOR = Color(0xFF4CAF50)      // green – confirmed data
private val INFERRED_COLOR = Color(0xFFFF9800)        // amber – inferred data
private val LOW_CONFIDENCE_COLOR = Color(0xFF9E9E9E)  // grey – faded / low confidence

private val DASH_PATH_EFFECT = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

// ─────────────────────────────────────────────────────────────────────────────
// Screen entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Spatial Alignment View – "Structure View" (2-D fallback for any device).
 *
 * Provides two sub-modes:
 * 1. **Side View** – vertical cross-section showing anchor heights.
 * 2. **Top View** – overhead plan showing anchor XZ positions.
 *
 * A floating action button opens a dialog to add new anchors manually.
 * Visual rules:
 * - Solid lines = confirmed data
 * - Dashed lines = inferred data
 * - Grey / faded = low confidence
 */
@Composable
fun AlignmentViewPanel(viewModel: SessionViewModel) {
    val session by viewModel.session.collectAsState()
    val model = session.spatialModel ?: AtlasSpatialModelV1()

    var selectedTab by remember { mutableStateOf(0) }
    var addAnchorOpen by remember { mutableStateOf(false) }

    val insights = remember(model) {
        SpatialAlignmentEngine.buildAlignmentInsights(model)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.title_structure_view),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.subtitle_structure_view, model.anchors.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Sub-tabs: Side View / Top View / Insights ─────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_side_view)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_top_view)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.tab_insights)) },
                )
            }

            when (selectedTab) {
                0 -> SideViewCanvas(model = model, modifier = Modifier.weight(1f))
                1 -> TopViewCanvas(model = model, modifier = Modifier.weight(1f))
                2 -> InsightsList(
                    insights = insights,
                    model = model,
                    onRemoveAnchor = { viewModel.removeAnchor(it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── FAB – add anchor ──────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { addAnchorOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.btn_add_anchor))
        }
    }

    if (addAnchorOpen) {
        AddAnchorDialog(
            onDismiss = { addAnchorOpen = false },
            onConfirm = { anchor ->
                viewModel.upsertAnchor(anchor)
                addAnchorOpen = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Side View – vertical cross-section
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws a simplified side-elevation view of the site.
 *
 * The Y axis (height) is mapped to the vertical canvas dimension.
 * The X axis (horizontal distance) is mapped to the horizontal dimension.
 * Vertical relationship lines are drawn between anchors.
 *
 * Visual rules:
 * - Confirmed anchors → filled circle + solid label
 * - Inferred anchors  → hollow circle + dashed label
 */
@Composable
private fun SideViewCanvas(model: AtlasSpatialModelV1, modifier: Modifier = Modifier) {
    if (model.anchors.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.empty_no_anchors),
            modifier = modifier,
        )
        return
    }

    val anchors = model.anchors
    val minX = anchors.minOf { it.worldPosition.x }.toFloat()
    val maxX = anchors.maxOf { it.worldPosition.x }.toFloat()
    val minY = anchors.minOf { it.worldPosition.y }.toFloat()
    val maxY = anchors.maxOf { it.worldPosition.y }.toFloat()
    val rangeX = (maxX - minX).coerceAtLeast(1f)
    val rangeY = (maxY - minY).coerceAtLeast(1f)

    val anchorById = remember(model) { model.anchors.associateBy { it.id } }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val padPx = 40f
        val drawW = size.width - padPx * 2
        val drawH = size.height - padPx * 2

        // Draw vertical relationship lines first (behind anchors)
        model.verticalRelations.forEach { rel ->
            val from = anchorById[rel.fromAnchorId] ?: return@forEach
            val to = anchorById[rel.toAnchorId] ?: return@forEach
            val fromPx = anchorToSideOffset(from, padPx, drawW, drawH, minX, rangeX, minY, rangeY)
            val toPx = anchorToSideOffset(to, padPx, drawW, drawH, minX, rangeX, minY, rangeY)
            val lineColor = colorForConfidence(to.worldPosition.confidence)
            val pathEffect = if (to.worldPosition.confidence == AtlasPositionConfidence.confirmed)
                null else DASH_PATH_EFFECT
            drawLine(
                color = lineColor.copy(alpha = 0.6f),
                start = fromPx,
                end = toPx,
                strokeWidth = 2f,
                pathEffect = pathEffect,
            )
        }

        // Draw inferred route paths
        model.inferredRoutes.filter { it.type == "pipe" }.forEach { route ->
            route.path.zipWithNext().forEach { (a, b) ->
                val aX = padPx + ((a.x.toFloat() - minX) / rangeX) * drawW
                val aY = padPx + drawH - ((a.y.toFloat() - minY) / rangeY) * drawH
                val bX = padPx + ((b.x.toFloat() - minX) / rangeX) * drawW
                val bY = padPx + drawH - ((b.y.toFloat() - minY) / rangeY) * drawH
                drawLine(
                    color = INFERRED_COLOR.copy(alpha = 0.5f),
                    start = Offset(aX, aY),
                    end = Offset(bX, bY),
                    strokeWidth = 2f,
                    pathEffect = DASH_PATH_EFFECT,
                )
            }
        }

        // Draw anchors
        anchors.forEach { anchor ->
            val offset = anchorToSideOffset(
                anchor, padPx, drawW, drawH, minX, rangeX, minY, rangeY
            )
            drawAnchorDot(anchor, offset)
        }
    }
}

private fun anchorToSideOffset(
    anchor: AtlasAnchor,
    padPx: Float,
    drawW: Float,
    drawH: Float,
    minX: Float,
    rangeX: Float,
    minY: Float,
    rangeY: Float,
): Offset {
    val cx = padPx + ((anchor.worldPosition.x.toFloat() - minX) / rangeX) * drawW
    val cy = padPx + drawH - ((anchor.worldPosition.y.toFloat() - minY) / rangeY) * drawH
    return Offset(cx, cy)
}

// ─────────────────────────────────────────────────────────────────────────────
// Top View – overhead plan
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws a top-down plan view of the site.
 *
 * The X axis maps to the canvas horizontal dimension.
 * The Z axis (depth) maps to the canvas vertical dimension.
 */
@Composable
private fun TopViewCanvas(model: AtlasSpatialModelV1, modifier: Modifier = Modifier) {
    if (model.anchors.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.empty_no_anchors),
            modifier = modifier,
        )
        return
    }

    val anchors = model.anchors
    val minX = anchors.minOf { it.worldPosition.x }.toFloat()
    val maxX = anchors.maxOf { it.worldPosition.x }.toFloat()
    val minZ = anchors.minOf { it.worldPosition.z }.toFloat()
    val maxZ = anchors.maxOf { it.worldPosition.z }.toFloat()
    val rangeX = (maxX - minX).coerceAtLeast(1f)
    val rangeZ = (maxZ - minZ).coerceAtLeast(1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val padPx = 40f
        val drawW = size.width - padPx * 2
        val drawH = size.height - padPx * 2

        // Inferred pipe routes
        model.inferredRoutes.filter { it.type == "pipe" }.forEach { route ->
            route.path.zipWithNext().forEach { (a, b) ->
                val aX = padPx + ((a.x.toFloat() - minX) / rangeX) * drawW
                val aY = padPx + ((a.z.toFloat() - minZ) / rangeZ) * drawH
                val bX = padPx + ((b.x.toFloat() - minX) / rangeX) * drawW
                val bY = padPx + ((b.z.toFloat() - minZ) / rangeZ) * drawH
                drawLine(
                    color = INFERRED_COLOR.copy(alpha = 0.5f),
                    start = Offset(aX, aY),
                    end = Offset(bX, bY),
                    strokeWidth = 2f,
                    pathEffect = DASH_PATH_EFFECT,
                )
            }
        }

        // Anchors
        anchors.forEach { anchor ->
            val cx = padPx + ((anchor.worldPosition.x.toFloat() - minX) / rangeX) * drawW
            val cy = padPx + ((anchor.worldPosition.z.toFloat() - minZ) / rangeZ) * drawH
            drawAnchorDot(anchor, Offset(cx, cy))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Insights list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InsightsList(
    insights: List<AlignmentInsight>,
    model: AtlasSpatialModelV1,
    onRemoveAnchor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (model.anchors.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.empty_no_anchors),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } else {
            item {
                Text(
                    stringResource(R.string.section_anchors),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(model.anchors, key = { "anchor_${it.id}" }) { anchor ->
                AnchorCard(anchor = anchor, onRemove = { onRemoveAnchor(anchor.id) })
            }
        }

        if (insights.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(
                    stringResource(R.string.section_relationships),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(insights, key = { "insight_${it.anchorId}_${it.relation}" }) { insight ->
                InsightCard(insight = insight)
            }
        }

        // Pipe length summary
        val totalPipe = SpatialAlignmentEngine.totalPipeLength(model)
        if (totalPipe > 0.0) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(
                    stringResource(R.string.section_routing),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.label_est_pipe_length, "%.1f".format(totalPipe)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.label_inferred_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun AnchorCard(anchor: AtlasAnchor, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConfidenceDot(confidence = anchor.worldPosition.confidence)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = anchor.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "(%.2f, %.2f, %.2f) m · %s".format(
                        anchor.worldPosition.x,
                        anchor.worldPosition.y,
                        anchor.worldPosition.z,
                        anchor.worldPosition.confidence.name,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_remove_anchor))
            }
        }
    }
}

@Composable
private fun InsightCard(insight: AlignmentInsight) {
    val borderColor = colorForConfidence(insight.confidence)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = borderColor.copy(alpha = 0.08f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConfidenceDot(confidence = insight.confidence)
                Text(
                    text = insight.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "%.2f m %s · %.2f m horizontal".format(
                    insight.verticalDistanceM,
                    insight.relation.replace("_", " "),
                    insight.horizontalOffsetM,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (insight.confidence == AtlasPositionConfidence.inferred) {
                Text(
                    text = stringResource(R.string.label_inferred_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = INFERRED_COLOR,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Anchor dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddAnchorDialog(
    onDismiss: () -> Unit,
    onConfirm: (AtlasAnchor) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var xText by remember { mutableStateOf("0.0") }
    var yText by remember { mutableStateOf("0.0") }
    var zText by remember { mutableStateOf("0.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_anchor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.field_anchor_label)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = xText,
                        onValueChange = { xText = it },
                        label = { Text("X (m)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = yText,
                        onValueChange = { yText = it },
                        label = { Text("Y (m)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = zText,
                        onValueChange = { zText = it },
                        label = { Text("Z (m)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Text(
                    stringResource(R.string.hint_manual_anchor),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val x = xText.toDoubleOrNull() ?: 0.0
                    val y = yText.toDoubleOrNull() ?: 0.0
                    val z = zText.toDoubleOrNull() ?: 0.0
                    onConfirm(
                        AtlasAnchor(
                            id = UUID.randomUUID().toString(),
                            label = label.ifBlank { "Anchor" },
                            worldPosition = AtlasWorldPosition(
                                x = x,
                                y = y,
                                z = z,
                                confidence = AtlasPositionConfidence.inferred,
                                source = AtlasPositionSource.manual,
                            ),
                        )
                    )
                },
                enabled = label.isNotBlank(),
            ) {
                Text(stringResource(R.string.btn_add_anchor))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared drawing helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawAnchorDot(anchor: AtlasAnchor, offset: Offset) {
    val color = colorForConfidence(anchor.worldPosition.confidence)
    val radius = 10f
    if (anchor.worldPosition.confidence == AtlasPositionConfidence.confirmed) {
        drawCircle(color = color, radius = radius, center = offset)
    } else {
        // Hollow circle for inferred
        drawCircle(color = color.copy(alpha = 0.25f), radius = radius, center = offset)
        drawCircle(color = color, radius = radius, center = offset, style =
            androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

private fun colorForConfidence(confidence: AtlasPositionConfidence): Color =
    when (confidence) {
        AtlasPositionConfidence.confirmed -> CONFIRMED_COLOR
        AtlasPositionConfidence.inferred -> INFERRED_COLOR
    }

// ─────────────────────────────────────────────────────────────────────────────
// Small re-usable composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfidenceDot(confidence: AtlasPositionConfidence) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color = colorForConfidence(confidence), shape = CircleShape),
    )
}

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}
