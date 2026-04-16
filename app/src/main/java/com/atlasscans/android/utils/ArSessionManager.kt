package com.atlasscans.android.utils

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.PointCloud
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.atlasscans.android.data.models.CapturedRoomScanV2
import com.atlasscans.android.data.models.SpatialCoordinate
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Thin wrapper around an ARCore [Session] that handles:
 * - Availability checking at construction time
 * - Depth API + plane detection configuration
 * - Point-cloud accumulation during an active scan
 * - Hit-test raycasting for pin-drop functionality
 * - Lifecycle forwarding (resume/pause/destroy)
 */
class ArSessionManager(context: Context) {

    private val session: Session
    private val pointCloudSnapshots = mutableListOf<PointCloud>()
    private val isScanning = AtomicBoolean(false)

    // Latest frame kept for raycasting (pin drop)
    @Volatile
    private var latestFrame: Frame? = null

    // Viewport dimensions kept for normalised hit-test coordinates
    @Volatile private var viewportWidth: Int = 1
    @Volatile private var viewportHeight: Int = 1

    init {
        // Throws if ARCore is not available; the caller handles the exception.
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
            throw UnsupportedOperationException("ARCore is not supported on this device.")
        }

        session = Session(context).also { s ->
            val config = Config(s).apply {
                depthMode = Config.DepthMode.AUTOMATIC
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
            s.configure(config)
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    fun onResume() = session.resume()
    fun onPause() = session.pause()
    fun onDestroy() = session.close()

    // -------------------------------------------------------------------------
    // Scan control
    // -------------------------------------------------------------------------

    fun startScan() {
        pointCloudSnapshots.clear()
        isScanning.set(true)
    }

    fun stopScan() {
        isScanning.set(false)
    }

    fun buildRoomScan(scanDurationSeconds: Float): CapturedRoomScanV2? {
        val planes = session.getAllTrackables(Plane::class.java)
        return ArCoreConverter.buildRoomScan(
            pointCloudSnapshots = pointCloudSnapshots.toList(),
            trackedPlanes = planes,
            scanDurationSeconds = scanDurationSeconds,
        )
    }

    // -------------------------------------------------------------------------
    // HUD helpers
    // -------------------------------------------------------------------------

    fun trackedPlaneCount(): Int =
        session.getAllTrackables(Plane::class.java)
            .count { it.trackingState == TrackingState.TRACKING }

    fun collectedPointCount(): Int =
        pointCloudSnapshots.sumOf { cloud ->
            cloud.points.capacity() / 4   // 4 floats per point (x,y,z,confidence)
        }

    // -------------------------------------------------------------------------
    // Hit-test / pin-drop
    // -------------------------------------------------------------------------

    /**
     * Perform a hit-test at the given normalised screen coordinates.
     * Returns the world-space hit coordinate (metres) or null if no surface
     * was hit.
     *
     * @param normalizedX X in [0, 1] (left → right).
     * @param normalizedY Y in [0, 1] (top → bottom).
     */
    fun hitTest(normalizedX: Float, normalizedY: Float): SpatialCoordinate? {
        val frame = latestFrame ?: return null
        if (frame.camera.trackingState != TrackingState.TRACKING) return null

        val screenX = normalizedX * viewportWidth
        val screenY = normalizedY * viewportHeight

        val hits: List<HitResult> = frame.hitTest(screenX, screenY)

        val best = hits.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)
        } ?: return null

        val pose = best.hitPose
        return SpatialCoordinate(pose.tx(), pose.ty(), pose.tz())
    }

    // -------------------------------------------------------------------------
    // GL integration
    // -------------------------------------------------------------------------

    /**
     * Create a [GLSurfaceView] wired to this ARCore session.
     * The renderer drives [session.update] each frame and accumulates point-
     * cloud snapshots while [isScanning] is true.
     */
    fun createGlSurfaceView(context: Context): GLSurfaceView {
        val glView = GLSurfaceView(context)
        glView.preserveEGLContextOnPause = true
        glView.setEGLContextClientVersion(2)
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glView.setRenderer(object : GLSurfaceView.Renderer {

            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                session.setCameraTextureName(0)
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                GLES20.glViewport(0, 0, width, height)
                viewportWidth = width
                viewportHeight = height
                // 0 = Surface.ROTATION_0 (portrait)
                session.setDisplayGeometry(0, width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                runCatching {
                    val frame = session.update()
                    latestFrame = frame
                    if (isScanning.get()) {
                        val cloud: PointCloud = frame.acquirePointCloud()
                        pointCloudSnapshots.add(cloud)
                    }
                }
            }
        })
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        return glView
    }
}
