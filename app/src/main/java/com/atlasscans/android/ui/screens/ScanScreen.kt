package com.atlasscans.android.ui.screens

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.atlasscans.android.R
import com.atlasscans.android.data.models.CapturedRoomScanV2
import com.atlasscans.android.utils.ArCoreConverter
import com.atlasscans.android.utils.ArSessionManager
import com.atlasscans.android.viewmodel.SessionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Room-scan screen.
 *
 * Embeds a [GLSurfaceView] that hosts an ARCore session with:
 * - Depth API for point-cloud collection
 * - Plane detection for floor / wall identification
 *
 * The user taps "Start Scan", moves the device around the room, then taps
 * "Stop Scan".  The accumulated point cloud and planes are converted via
 * [ArCoreConverter] and handed to [SessionViewModel.setRoomScan].
 */
@Composable
fun ScanScreen(viewModel: SessionViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // UI state
    var isScanning by remember { mutableStateOf(false) }
    var scanComplete by remember { mutableStateOf(false) }
    var planeCount by remember { mutableIntStateOf(0) }
    var pointCount by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    val session: ArSessionManager? = remember(context) {
        runCatching { ArSessionManager(context) }.getOrNull()
    }
    // Derive AR availability from the session (null means unsupported or error)
    val arUnavailable = session == null

    // Elapsed-time ticker while scanning
    LaunchedEffect(isScanning) {
        if (isScanning) {
            val startMs = System.currentTimeMillis()
            while (isActive && isScanning) {
                elapsedSeconds = (System.currentTimeMillis() - startMs) / 1_000f
                planeCount = session?.trackedPlaneCount() ?: 0
                pointCount = session?.collectedPointCount() ?: 0
                delay(500)
            }
        }
    }

    // Lifecycle integration – pause/resume ARCore with the Activity
    DisposableEffect(session, lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { session?.onResume() }
            override fun onPause(owner: LifecycleOwner) { session?.onPause() }
            override fun onDestroy(owner: LifecycleOwner) { session?.onDestroy() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (arUnavailable) {
            // Graceful fallback when device does not support ARCore
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.arcore_not_supported),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            // AR camera preview embedded as an AndroidView
            AndroidView(
                factory = { ctx: Context ->
                    session?.createGlSurfaceView(ctx) ?: GLSurfaceView(ctx)
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Overlay HUD
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isScanning) {
                    ScanStatsCard(
                        elapsedSeconds = elapsedSeconds,
                        planeCount = planeCount,
                        pointCount = pointCount,
                    )
                }

                if (scanComplete) {
                    Text(
                        text = stringResource(R.string.scan_complete),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = {
                        if (!isScanning) {
                            session?.startScan()
                            isScanning = true
                            scanComplete = false
                        } else {
                            isScanning = false
                            session?.stopScan()
                            val roomScan: CapturedRoomScanV2? = session?.buildRoomScan(elapsedSeconds)
                            roomScan?.let { viewModel.setRoomScan(it) }
                            scanComplete = roomScan != null
                        }
                    },
                    colors = if (isScanning)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else
                        ButtonDefaults.buttonColors(),
                ) {
                    Text(
                        if (isScanning) stringResource(R.string.btn_stop_scan)
                        else stringResource(R.string.btn_start_scan)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanStatsCard(
    elapsedSeconds: Float,
    planeCount: Int,
    pointCount: Int,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "%.1f s".format(elapsedSeconds),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.planes_detected, planeCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.points_collected, pointCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
