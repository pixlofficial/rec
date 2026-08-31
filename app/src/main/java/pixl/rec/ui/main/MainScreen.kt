package pixl.rec.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pixl.rec.core.model.RecorderState
import pixl.rec.ui.dashboard.DashboardScreen
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.more.MoreScreen
import pixl.rec.ui.navigation.BottomNavBar
import pixl.rec.ui.navigation.NavigationTab
import pixl.rec.ui.settings.SettingsScreen
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.vault.VaultScreen
import pixl.rec.ui.vault.VaultViewModel

@Composable
fun MainScreen(
    dashboardViewModel: DashboardViewModel,
    vaultViewModel: VaultViewModel = viewModel(),
    initialTab: NavigationTab = NavigationTab.DASHBOARD,
    onRequestRecordPermission: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(initialTab) }
    val isRecording by dashboardViewModel.isRecordingActive.collectAsState()

    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
    ) {
        // Fullscreen edge-to-edge content layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    NavigationTab.DASHBOARD -> DashboardScreen(
                        viewModel = dashboardViewModel,
                        onRequestRecordPermission = onRequestRecordPermission,
                        onNavigateToSettings = { currentTab = NavigationTab.SETTINGS }
                    )
                    NavigationTab.VAULT -> VaultScreen(
                        vaultViewModel = vaultViewModel,
                        onRequestRecord = onRequestRecordPermission
                    )
                    NavigationTab.SETTINGS -> SettingsScreen(
                        viewModel = dashboardViewModel
                    )
                    NavigationTab.MORE -> MoreScreen(
                        viewModel = dashboardViewModel
                    )
                }
            }
        }

        // Floating Bottom Navigation Bar overlay
        BottomNavBar(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            isRecording = isRecording,
            onRecordAction = {
                if (isRecording) {
                    dashboardViewModel.stopRecording()
                } else {
                    onRequestRecordPermission()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
