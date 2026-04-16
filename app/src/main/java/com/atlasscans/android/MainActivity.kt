package com.atlasscans.android

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.atlasscans.android.data.database.AppDatabase
import com.atlasscans.android.data.repository.SessionRepository
import com.atlasscans.android.ui.screens.PhotoCaptureScreen
import com.atlasscans.android.ui.screens.ScanScreen
import com.atlasscans.android.ui.screens.SummaryScreen
import com.atlasscans.android.ui.screens.VoiceNoteScreen
import com.atlasscans.android.ui.theme.AtlasScansTheme
import com.atlasscans.android.utils.ArSessionManager
import com.atlasscans.android.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

/**
 * Single-activity host for the Atlas Scans Android app.
 *
 * Uses a [HorizontalPager] with four pages:
 * 1. Room Scan (ARCore depth + plane detection)
 * 2. Photo Capture (CameraX + optional ARCore pin drop)
 * 3. Voice Note (MediaRecorder + SpeechRecognizer)
 * 4. Summary & Export
 *
 * The [SessionViewModel] is scoped to this Activity so session state survives
 * configuration changes and page swipes.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        val repo = SessionRepository(db.captureSessionDao())
        SessionViewModel.Factory(repo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            AtlasScansTheme {
                AtlasScansApp(viewModel = viewModel)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App root
// ─────────────────────────────────────────────────────────────────────────────

private val REQUIRED_PERMISSIONS = listOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private data class TabItem(
    val label: Int,
    val icon: ImageVector,
)

private val TABS = listOf(
    TabItem(R.string.tab_scan, Icons.Default.ViewInAr),
    TabItem(R.string.tab_photo, Icons.Default.CameraAlt),
    TabItem(R.string.tab_voice, Icons.Default.Mic),
    TabItem(R.string.tab_summary, Icons.Default.List),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun AtlasScansApp(viewModel: SessionViewModel) {
    val permissionsState = rememberMultiplePermissionsState(REQUIRED_PERMISSIONS)
    val pagerState: PagerState = rememberPagerState { TABS.size }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    // Shared ARCore session – created once permissions are granted.
    // Each individual screen that needs AR still does its own availability
    // check (ArSessionManager throws if the device is unsupported).
    val arSession: ArSessionManager? = remember(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            runCatching { ArSessionManager(context) }.getOrNull()
        } else {
            null
        }
    }

    DisposableEffect(arSession) {
        onDispose { arSession?.onDestroy() }
    }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Permission banner
            AnimatedVisibility(
                visible = !permissionsState.allPermissionsGranted,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = stringResource(R.string.permission_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                beyondBoundsPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> ScanScreen(viewModel = viewModel, arSession = arSession)
                    1 -> PhotoCaptureScreen(viewModel = viewModel, arSession = arSession)
                    2 -> VoiceNoteScreen(viewModel = viewModel)
                    3 -> SummaryScreen(viewModel = viewModel)
                }
            }
        }
    }
}
