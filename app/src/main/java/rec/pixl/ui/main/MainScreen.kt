package rec.pixl.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import rec.pixl.core.model.RecorderState
import rec.pixl.ui.dashboard.DashboardScreen
import rec.pixl.ui.dashboard.DashboardViewModel
import rec.pixl.ui.more.MoreScreen
import rec.pixl.ui.navigation.BottomNavBar
import rec.pixl.ui.navigation.NavigationTab
import rec.pixl.ui.settings.SettingsScreen
import rec.pixl.ui.theme.ObsidianCanvas
import rec.pixl.ui.vault.VaultScreen
import rec.pixl.ui.vault.VaultViewModel

@Composable
fun MainScreen(
    dashboardViewModel: DashboardViewModel,
    vaultViewModel: VaultViewModel = viewModel(),
    onRequestRecordPermission: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(NavigationTab.DASHBOARD) }
    val recorderState by dashboardViewModel.recorderState.collectAsState()

    Scaffold(
        containerColor = ObsidianCanvas,
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                recorderState = recorderState,
                onRecordAction = {
                    val isRecording = recorderState is RecorderState.Recording || recorderState is RecorderState.Paused
                    if (isRecording) {
                        dashboardViewModel.stopRecording()
                    } else {
                        onRequestRecordPermission()
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianCanvas)
                .padding(bottom = padding.calculateBottomPadding())
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
    }
}
