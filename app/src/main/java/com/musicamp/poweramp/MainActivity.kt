package com.musicamp.poweramp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private lateinit var controller: PowerampController
    private var displayManager: DisplayManager? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = syncDisplayRefreshRate()
        override fun onDisplayRemoved(displayId: Int) = syncDisplayRefreshRate()
        override fun onDisplayChanged(displayId: Int) = syncDisplayRefreshRate()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permission just changed: rescan so the library appears without restarting the app
        controller.scanMediaLibrary()
    }

    // RECORD_AUDIO is only used by the visualizer to read this app's own output (nothing is recorded)
    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) controller.refreshVisualizer()
        else controller.showToast("Visualization", "Microphone permission is needed for the live spectrum")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        // The controller lives in the Application so playback survives the Activity being closed
        controller = (application as PowerampApp).controller
        controller.recordPermissionRequester = { recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        enableAdaptiveHighRefreshRate()
        requestAudioPermissionsIfNeeded()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        controller.showEditTagsDialog -> controller.showEditTagsDialog = false
                        controller.showInfoTagsSheet -> controller.showInfoTagsSheet = false
                        controller.showTrackActionDialog -> controller.showTrackActionDialog = false
                        controller.showSleepTimerDialog -> controller.showSleepTimerDialog = false
                        controller.showEqPresetSheet -> controller.showEqPresetSheet = false
                        controller.showReverbPresetSheet -> controller.showReverbPresetSheet = false
                        controller.showAddToPlaylistDialog -> controller.showAddToPlaylistDialog = false
                        controller.showAlbumArtDialog -> controller.showAlbumArtDialog = false
                        controller.showChangelogDialog -> controller.showChangelogDialog = false
                        controller.showHelpDialog -> controller.showHelpDialog = false
                        controller.showSettingsScreen -> controller.showSettingsScreen = false
                        controller.showMainMenuSheet -> controller.showMainMenuSheet = false
                        controller.showLyricsScreen -> controller.showLyricsScreen = false
                        controller.currentMainTab == MainNavTab.LIBRARY && controller.activeSubFilter != null -> {
                            controller.activeSubFilter = null
                        }
                        controller.currentMainTab == MainNavTab.LIBRARY && controller.activeLibraryCategory != null -> {
                            controller.activeLibraryCategory = null
                        }
                        controller.currentMainTab != MainNavTab.PLAYER -> {
                            controller.currentMainTab = MainNavTab.PLAYER
                        }
                        else -> finish()
                    }
                }
            }
        )

        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            SideEffect { AppTheme.systemDark = systemDark }
            val dark = AppTheme.isDark
            val bg = PowerampBg
            // Navigation bar follows the theme; status bar stays transparent over the accent header
            SideEffect {
                window.navigationBarColor = bg.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !dark
            }
            MaterialTheme(
                colorScheme = if (dark) {
                    androidx.compose.material3.darkColorScheme(
                        background = bg, surface = PowerampSurface, primary = ThemeBerryPrimary, onPrimary = PowerampOnAccent,
                        onBackground = PowerampTextPrimary, onSurface = PowerampTextPrimary
                    )
                } else {
                    androidx.compose.material3.lightColorScheme(
                        background = bg, surface = PowerampSurface, primary = ThemeBerryPrimary, onPrimary = PowerampOnAccent,
                        onBackground = PowerampTextPrimary, onSurface = PowerampTextPrimary
                    )
                }
            ) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Box(modifier = Modifier.fillMaxSize()) {
                    PowerampRootApp(controller = controller)
                    MusicAmpSplashOverlay(
                        visible = showSplash,
                        onFinished = { showSplash = false }
                    )
                }
            }
        }
    }

    private fun requestAudioPermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        // Android 13+: needed for the playback notification / lock-screen controls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun enableAdaptiveHighRefreshRate() {
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayManager = dm
            dm?.registerDisplayListener(displayListener, null)

            val currentDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            if (currentDisplay != null) {
                val activeMode = currentDisplay.mode
                val bestMode = currentDisplay.supportedModes
                    .filter {
                        it.physicalWidth == activeMode.physicalWidth &&
                            it.physicalHeight == activeMode.physicalHeight
                    }
                    .maxByOrNull { it.refreshRate }
                if (bestMode != null && bestMode.refreshRate > activeMode.refreshRate + 0.5f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = bestMode.modeId
                    window.attributes = lp
                }
            }
            syncDisplayRefreshRate()
        } catch (_: Exception) {
            controller.displayRefreshRateHz = 60f
        }
    }

    private fun syncDisplayRefreshRate() {
        try {
            val currentDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val hz = currentDisplay?.refreshRate ?: 60f
            controller.displayRefreshRateHz = hz.coerceIn(30f, 240f)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        // Don't keep a reference to a destroyed Activity inside the app-wide controller
        try {
            displayManager?.unregisterDisplayListener(displayListener)
        } catch (_: Exception) {
        }
        controller.recordPermissionRequester = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Direct Volume Control: volume keys drive the app's software volume, not the (pinned) system stream
        if (controller.dvcActive) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    controller.adjustDvcVolume(+1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    controller.adjustDvcVolume(-1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (controller.dvcActive &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        // Do NOT release the player here: music must keep playing in the background
        controller.onAppStopped()
    }
}

@Composable
fun PowerampRootApp(controller: PowerampController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PowerampBg)
    ) {
        // Primary Content Screen with Poweramp's vertical expand/collapse & zoom-fade screen transitions
        AnimatedContent(
            targetState = Pair(controller.showLyricsScreen, controller.currentMainTab),
            transitionSpec = {
                val toPlayer = !targetState.first && targetState.second == MainNavTab.PLAYER
                val fromPlayer = !initialState.first && initialState.second == MainNavTab.PLAYER
                when {
                    toPlayer -> {
                        // Expanding from bottom Mini-player up into Full Player screen
                        (slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 3 } +
                            fadeIn(tween(240)) +
                            scaleIn(initialScale = 0.94f, animationSpec = tween(300)))
                            .togetherWith(
                                fadeOut(tween(200)) +
                                    scaleOut(targetScale = 0.96f, animationSpec = tween(240))
                            )
                    }
                    fromPlayer -> {
                        // Collapsing Full Player screen into Library / Equalizer / Search
                        (fadeIn(tween(240)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(260)))
                            .togetherWith(
                                slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it / 4 } +
                                    fadeOut(tween(200))
                            )
                    }
                    else -> {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(240)))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)))
                    }
                }
            },
            label = "mainScreenTransition"
        ) { (isLyrics, tab) ->
            if (isLyrics) {
                PowerampLyricsScreen(controller = controller)
            } else {
                when (tab) {
                    MainNavTab.PLAYER -> PowerampPlayerScreen(controller = controller)
                    MainNavTab.LIBRARY -> PowerampLibraryScreen(controller = controller)
                    MainNavTab.EQUALIZER -> PowerampEqualizerScreen(controller = controller)
                    MainNavTab.SEARCH -> PowerampSearchScreen(controller = controller)
                }
            }
        }

        // Full-screen Settings Screen Layer
        AnimatedVisibility(
            visible = controller.showSettingsScreen,
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180))
        ) {
            PowerampSettingsOverlay(controller = controller)
        }

        // Animated Overlays, Bottom Sheets & Modals (Drawn on top of screens and settings)
        AnimatedVisibility(
            visible = controller.showMainMenuSheet,
            enter = slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(160))
        ) {
            PowerampMainMenuOverlay(controller = controller)
        }
        AnimatedVisibility(
            visible = controller.showTrackActionDialog,
            enter = scaleIn(initialScale = 0.88f, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = scaleOut(targetScale = 0.88f, animationSpec = tween(180)) + fadeOut(tween(150))
        ) {
            PowerampTrackMenuDialog(controller = controller)
        }
        AnimatedVisibility(
            visible = controller.showInfoTagsSheet,
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160))
        ) {
            PowerampInfoTagsSheet(controller = controller)
        }
        AnimatedVisibility(
            visible = controller.showEditTagsDialog,
            enter = scaleIn(initialScale = 0.90f, animationSpec = tween(220)) + fadeIn(tween(180)),
            exit = scaleOut(targetScale = 0.90f, animationSpec = tween(180)) + fadeOut(tween(150))
        ) {
            PowerampEditTagsDialog(controller = controller)
        }
        AnimatedVisibility(
            visible = controller.showSleepTimerDialog,
            enter = scaleIn(initialScale = 0.88f, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = scaleOut(targetScale = 0.88f, animationSpec = tween(180)) + fadeOut(tween(150))
        ) {
            PowerampSleepTimerDialog(controller = controller)
        }
        AnimatedVisibility(
            visible = controller.showEqPresetSheet,
            enter = scaleIn(initialScale = 0.90f, animationSpec = tween(230, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = scaleOut(targetScale = 0.90f, animationSpec = tween(180)) + fadeOut(tween(150))
        ) {
            PowerampEqPresetBottomSheet(controller = controller)
        }
        AnimatedVisibility(
            visible = controller.showReverbPresetSheet,
            enter = scaleIn(initialScale = 0.90f, animationSpec = tween(220)) + fadeIn(tween(180)),
            exit = scaleOut(targetScale = 0.90f, animationSpec = tween(180)) + fadeOut(tween(150))
        ) {
            PowerampReverbPresetDialog(controller = controller)
        }
        AnimatedVisibility(
            visible = controller.showAddToPlaylistDialog,
            enter = scaleIn(initialScale = 0.90f, animationSpec = tween(220)) + fadeIn(tween(180)),
            exit = scaleOut(targetScale = 0.90f, animationSpec = tween(180)) + fadeOut(tween(150))
        ) {
            PowerampPlaylistDialog(controller = controller)
        }
        if (controller.showAlbumArtDialog) {
            PowerampSimpleInfoDialog(
                title = "Album Art",
                body = "Current track uses MusicAmp's default High-Contrast Vector Mountain artwork.\n\nResolution: 1080×1080 (Hi-Res Vector)\nCache: Verified",
                onDismiss = { controller.showAlbumArtDialog = false }
            )
        }
        if (controller.showChangelogDialog) {
            PowerampSimpleInfoDialog(
                title = "MusicAmp build-1013-uni",
                body = "• 10-Band Graphic & Parametric Equalizer with Preamp\n• Real-time Bass & Treble Tone Knobs\n• Stereo Expand, Balance, Tempo (0.5x - 2.0x) & Mono DSP\n• Full Reverb Engine (Damp, Filter, Fade, Pre-Delay, Pre-Delay Mix, Size, Mix)\n• Dynamic Scrolling Waveseek Bar & Centered-Spectrum / Milkdrop Visualization\n• 19 Library Categories, Bookmarks, Queue & Tag Editor",
                onDismiss = { controller.showChangelogDialog = false }
            )
        }
        if (controller.showHelpDialog) {
            PowerampSimpleInfoDialog(
                title = "MusicAmp Help & Gestures",
                body = "• Swipe left/right on Album Art or Mini-player to change track with slide animation\n• Drag across the Waveseek bars to scrub track position\n• Double-tap any Rotary Knob or EQ Slider to reset to 0.0\n• Long-press any track in Library or Search for the Track Context Menu",
                onDismiss = { controller.showHelpDialog = false }
            )
        }

        // Poweramp Bottom Toast Notification Banner (`14_vis_panel.png`) with slide + fade animation
        AnimatedVisibility(
            visible = controller.toastTitle != null,
            enter = slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        ) {
            PowerampToastOverlay(
                title = controller.toastTitle ?: "",
                subtitle = controller.toastSubtitle
            )
        }
    }
}
