@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package pixl.rec.ui.settings

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pixl.rec.core.game.GameDetector
import pixl.rec.core.model.QuickPreset
import pixl.rec.core.model.RecorderState
import pixl.rec.core.storage.ConfigPreferences
import pixl.rec.ui.components.SlidingPillSelector
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.settings.components.GamingOptimizationDialog
import pixl.rec.ui.settings.sections.AudioSettingsSection
import pixl.rec.ui.settings.sections.CaptureSettingsSection
import pixl.rec.ui.settings.sections.GeneralSettingsSection
import pixl.rec.ui.settings.sections.OutputSettingsSection
import pixl.rec.ui.settings.sections.StreamSettingsSection
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary

/**
 * Master Settings Screen for REC.
 *
 * Implements Point 9 OBS-Inspired Information Architecture:
 * - GENERAL: Application behavior, overlay & gestures, notifications, backup & restore, live streaming master switch
 * - CAPTURE: Input capture resolution, orientation, and FPS (pure capture pacing)
 * - AUDIO: Audio routing, mic/internal gains, and real-time stereo VU monitoring
 * - OUTPUT: Local recording codec/bitrate/storage + Live broadcast encoding + Instant Replay buffer
 * - STREAM: Broadcast destination identity, endpoints, keystore credentials, and multi-stream staging
 */
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    onNavigateToHudStudio: (pixl.rec.core.storage.StudioMode) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val recorderState by viewModel.recorderState.collectAsState()
    val isRecordingActive = recorderState is RecorderState.Recording || recorderState is RecorderState.Paused

    val isStreamingEnabled by viewModel.isLiveStreamingEnabled.collectAsState()
    var selectedSubTab by remember { mutableStateOf(SettingsTab.GENERAL) }

    val availableTabs = remember(isStreamingEnabled) {
        if (isStreamingEnabled) {
            SettingsTab.entries
        } else {
            listOf(SettingsTab.GENERAL, SettingsTab.CAPTURE, SettingsTab.AUDIO, SettingsTab.OUTPUT)
        }
    }

    LaunchedEffect(isStreamingEnabled) {
        if (!isStreamingEnabled && selectedSubTab == SettingsTab.STREAM) {
            selectedSubTab = SettingsTab.GENERAL
        }
    }

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val cardShineProgress = remember { Animatable(0f) }
    val pillTraceProgress = remember { Animatable(0f) }
    var showGamingOptDialog by remember { mutableStateOf(false) }
    var awaitingUsagePermission by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (awaitingUsagePermission && GameDetector.hasUsageAccessPermission(context)) {
                    awaitingUsagePermission = false
                    viewModel.toggleSmartGameOptimization(true)
                    Toast.makeText(context, "🎮 Smart Game Optimization Enabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showGamingOptDialog) {
        GamingOptimizationDialog(
            onDismiss = { doNotAskAgain ->
                if (doNotAskAgain) {
                    ConfigPreferences.setGamingPresetPromptDismissed(context, true)
                }
                showGamingOptDialog = false
                viewModel.applyQuickPreset(QuickPreset.GAMING)
            },
            onConfigure = { doNotAskAgain ->
                if (doNotAskAgain) {
                    ConfigPreferences.setGamingPresetPromptDismissed(context, true)
                }
                showGamingOptDialog = false
                viewModel.applyQuickPreset(QuickPreset.GAMING)
                selectedSubTab = SettingsTab.GENERAL
                coroutineScope.launch {
                    delay(200)
                    if (scrollState.maxValue == 0) {
                        delay(150)
                    }
                    scrollState.animateScrollTo(scrollState.maxValue, tween(500))
                    try {
                        bringIntoViewRequester.bringIntoView()
                    } catch (_: Exception) {}

                    cardShineProgress.snapTo(0f)
                    pillTraceProgress.snapTo(0f)

                    // Phase 1: Slanted red beam passes across the card (left to right)
                    val shineJob = launch {
                        cardShineProgress.animateTo(1f, tween(550))
                    }

                    // Phase 2: As red shine reaches the toggle pill, ignite border trace
                    delay(300)
                    pillTraceProgress.animateTo(1.3f, tween(650))

                    shineJob.join()
                    cardShineProgress.snapTo(0f)
                    pillTraceProgress.snapTo(0f)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Settings Header
        Text(
            text = "CONFIG // SETTINGS",
            color = TextPrimary,
            fontSize = 24.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "HARDWARE ENCODER & ENGINE PROFILES",
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = BitcountPropSingle
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Sub-Navigation Tabs: [ GENERAL | CAPTURE | AUDIO | OUTPUT | STREAM ]
        SlidingPillSelector(
            items = availableTabs,
            selectedItem = selectedSubTab,
            onItemSelected = { selectedSubTab = it },
            itemLabel = { it.title },
            height = 42.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Sub-Tab Content
        when (selectedSubTab) {
            SettingsTab.GENERAL -> GeneralSettingsSection(
                uiState = uiState,
                viewModel = viewModel,
                isStreamingEnabled = isStreamingEnabled,
                isRecordingActive = isRecordingActive,
                smartGameOptRequester = bringIntoViewRequester,
                cardShineProgress = cardShineProgress,
                pillTraceProgress = pillTraceProgress,
                onRequestUsagePermission = {
                    awaitingUsagePermission = true
                    GameDetector.openUsageAccessSettings(context)
                    Toast.makeText(
                        context,
                        "Grant Usage Access to enable Game Auto-Detection",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onNavigateToHudStudio = { onNavigateToHudStudio(pixl.rec.core.storage.StudioMode.RECORD) },
                onNavigateToStream = { selectedSubTab = SettingsTab.STREAM }
            )
            SettingsTab.CAPTURE -> CaptureSettingsSection(
                uiState = uiState,
                isRecordingActive = isRecordingActive,
                viewModel = viewModel,
                onSelectGamingPreset = { showGamingOptDialog = true }
            )
            SettingsTab.AUDIO -> AudioSettingsSection(
                uiState = uiState,
                isRecordingActive = isRecordingActive,
                recorderState = recorderState,
                viewModel = viewModel
            )
            SettingsTab.OUTPUT -> OutputSettingsSection(
                uiState = uiState,
                isRecordingActive = isRecordingActive,
                isStreamingEnabled = isStreamingEnabled,
                viewModel = viewModel,
                onNavigateToGeneral = { selectedSubTab = SettingsTab.GENERAL }
            )
            SettingsTab.STREAM -> StreamSettingsSection(
                viewModel = viewModel,
                isRecordingActive = isRecordingActive,
                onNavigateToHudStudio = { onNavigateToHudStudio(pixl.rec.core.storage.StudioMode.STREAM) }
            )
        }

        Spacer(modifier = Modifier.height(116.dp))
    }
}
