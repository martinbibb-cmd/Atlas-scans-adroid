package com.atlasscans.android.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlasscans.android.R
import com.atlasscans.android.data.models.CapturedPhotoV2
import com.atlasscans.android.data.models.SessionCaptureV2
import com.atlasscans.android.data.models.VoiceNoteV2
import com.atlasscans.android.viewmodel.SessionViewModel

/**
 * Summary screen – lists all captured assets and allows the user to:
 * - Review the room scan, photos, and voice notes
 * - Delete individual items
 * - Export the full [SessionCaptureV2] as a JSON bundle via an Android share sheet
 * - Start a new session
 */
@Composable
fun SummaryScreen(viewModel: SessionViewModel) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    var exportConfirmOpen by remember { mutableStateOf(false) }
    var newSessionConfirmOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Session Summary",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "ID: ${session.id.take(8)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Asset list ────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // Room Scan
            item(key = "scan_section") {
                SectionHeader(
                    icon = { Icon(Icons.Default.ViewInAr, contentDescription = null) },
                    title = "Room Scan",
                )
            }
            if (session.roomScan != null) {
                val scan = session.roomScan!!
                item(key = "scan_item") {
                    SummaryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Planes: ${scan.detectedPlanes.size}  ·  " +
                                        "Points: ${scan.pointCloud.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "Duration: ${"%.1f".format(scan.scanDurationSeconds)} s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "scan_empty") {
                    Text(
                        "No scan captured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // Photos
            item(key = "photo_section") {
                SectionHeader(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    title = "Photos (${session.photos.size})",
                )
            }
            if (session.photos.isEmpty()) {
                item(key = "photo_empty") {
                    Text(
                        "No photos captured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                items(session.photos, key = { "photo_${it.id}" }) { photo ->
                    PhotoSummaryCard(
                        photo = photo,
                        onDelete = { viewModel.removePhoto(photo.id) },
                    )
                }
            }

            // Voice Notes
            item(key = "voice_section") {
                SectionHeader(
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    title = "Voice Notes (${session.voiceNotes.size})",
                )
            }
            if (session.voiceNotes.isEmpty()) {
                item(key = "voice_empty") {
                    Text(
                        "No voice notes captured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                items(session.voiceNotes, key = { "voice_${it.id}" }) { note ->
                    VoiceNoteSummaryCard(
                        note = note,
                        onDelete = { viewModel.removeVoiceNote(note.id) },
                    )
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
        }

        // ── Action bar ────────────────────────────────────────────────────────
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { newSessionConfirmOpen = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.btn_new_session))
                }
                Button(
                    onClick = { exportConfirmOpen = true },
                    modifier = Modifier.weight(1f),
                    enabled = hasCaptures(session),
                ) {
                    Text(stringResource(R.string.btn_export))
                }
            }
        }
    }

    // Export confirmation dialog
    if (exportConfirmOpen) {
        AlertDialog(
            onDismissRequest = { exportConfirmOpen = false },
            title = { Text("Export session") },
            text = { Text("Share the session JSON bundle with Atlas Mind?") },
            confirmButton = {
                TextButton(onClick = {
                    exportConfirmOpen = false
                    shareSession(context, viewModel)
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { exportConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }

    // New session confirmation dialog
    if (newSessionConfirmOpen) {
        AlertDialog(
            onDismissRequest = { newSessionConfirmOpen = false },
            title = { Text("Start new session?") },
            text = { Text("All unsaved captures in the current session will be discarded.") },
            confirmButton = {
                TextButton(onClick = {
                    newSessionConfirmOpen = false
                    viewModel.newSession()
                }) { Text("Discard & New") }
            },
            dismissButton = {
                TextButton(onClick = { newSessionConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun hasCaptures(session: SessionCaptureV2) =
    session.roomScan != null || session.photos.isNotEmpty() || session.voiceNotes.isNotEmpty()

private fun shareSession(context: Context, viewModel: SessionViewModel) {
    val intent = viewModel.buildExportIntent(context)
    context.startActivity(Intent.createChooser(intent, "Export Atlas Session"))
}

// ─────────────────────────────────────────────────────────────────────────────
// UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: @Composable () -> Unit, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SummaryCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun PhotoSummaryCard(photo: CapturedPhotoV2, onDelete: () -> Unit) {
    SummaryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = photo.pinLabel ?: "Photo",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (photo.coordinate != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "(%.2f, %.2f, %.2f)".format(
                                photo.coordinate.x,
                                photo.coordinate.y,
                                photo.coordinate.z,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete photo")
            }
        }
    }
}

@Composable
private fun VoiceNoteSummaryCard(note: VoiceNoteV2, onDelete: () -> Unit) {
    SummaryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.transcript.ifBlank { "(no transcript)" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${"%.1f".format(note.durationSeconds)} s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete voice note")
            }
        }
    }
}
