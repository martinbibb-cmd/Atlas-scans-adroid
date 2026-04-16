package com.atlasscans.android.ui.screens

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.atlasscans.android.data.models.CapturedPhotoV2
import com.atlasscans.android.data.models.SpatialCoordinate
import com.atlasscans.android.utils.ArSessionManager
import com.atlasscans.android.viewmodel.SessionViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Photo capture screen.
 *
 * Features:
 * - CameraX live preview
 * - High-resolution JPEG capture
 * - Optional "pin drop" via ARCore hit-test: the user long-presses the preview
 *   to place a spatial anchor before capturing
 * - Captured photos shown in a horizontal strip at the bottom
 */
@Composable
fun PhotoCaptureScreen(
    viewModel: SessionViewModel,
    arSession: ArSessionManager?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val session by viewModel.session.collectAsState()

    // CameraX state
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    // Pending pin coordinate (set by long-press before capture)
    var pendingPinCoordinate by remember { mutableStateOf<SpatialCoordinate?>(null) }
    var pendingPinLabel by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Camera Preview ────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx: Context ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    imageCapture = capture
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            // Long-press → attempt ARCore hit-test for pin drop
                            val normX = offset.x / size.width.toFloat()
                            val normY = offset.y / size.height.toFloat()
                            val coord = arSession?.hitTest(normX, normY)
                            if (coord != null) {
                                pendingPinCoordinate = coord
                                showPinDialog = true
                            }
                        },
                    )
                },
        )

        // ── Pin indicator overlay ─────────────────────────────────────────────
        if (pendingPinCoordinate != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            "Pin set",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        IconButton(
                            onClick = { pendingPinCoordinate = null; pendingPinLabel = "" },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear pin",
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }

        // ── Capture controls ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // Thumbnail strip
            if (session.photos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(session.photos, key = { it.id }) { photo ->
                        PhotoThumbnail(photo = photo, onRemove = { viewModel.removePhoto(it) })
                    }
                }
            }

            // Shutter button
            FloatingActionButton(
                onClick = {
                    val capture = imageCapture ?: return@FloatingActionButton
                    isCapturing = true
                    takePhoto(
                        context = context,
                        imageCapture = capture,
                        executor = ContextCompat.getMainExecutor(context),
                        pinCoordinate = pendingPinCoordinate,
                        pinLabel = pendingPinLabel.ifBlank { null },
                        onSuccess = { photo ->
                            viewModel.addPhoto(photo)
                            pendingPinCoordinate = null
                            pendingPinLabel = ""
                            isCapturing = false
                        },
                        onError = { isCapturing = false },
                    )
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Capture photo",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }

    // Pin label dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Name this pin") },
            text = {
                OutlinedTextField(
                    value = pendingPinLabel,
                    onValueChange = { pendingPinLabel = it },
                    label = { Text("Label (optional)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingPinCoordinate = null
                    pendingPinLabel = ""
                    showPinDialog = false
                }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoThumbnail(photo: CapturedPhotoV2, onRemove: (String) -> Unit) {
    Box(modifier = Modifier.size(64.dp)) {
        AsyncImage(
            model = photo.filePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.coordinate != null) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = "Has pin",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
            )
        }
        IconButton(
            onClick = { onRemove(photo.id) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(20.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove photo",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    pinCoordinate: SpatialCoordinate?,
    pinLabel: String?,
    onSuccess: (CapturedPhotoV2) -> Unit,
    onError: (Exception) -> Unit,
) {
    val capturesDir = File(context.getExternalFilesDir(null), "captures").also { it.mkdirs() }
    val photoFile = File(capturesDir, "photo_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri: Uri = outputFileResults.savedUri
                    ?: Uri.fromFile(photoFile)
                onSuccess(
                    CapturedPhotoV2(
                        id = UUID.randomUUID().toString(),
                        filePath = savedUri.toString(),
                        timestamp = System.currentTimeMillis(),
                        coordinate = pinCoordinate,
                        pinLabel = pinLabel,
                    )
                )
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        },
    )
}
