package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.GameBoardScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LevelSelectScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ChromaFlowTheme
import com.example.ui.theme.StudioThemes
import com.example.ui.viewmodel.GameBoardViewModel
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object GameRoute

@Serializable
object LevelsRoute

@Serializable
object SettingsRoute

class MainActivity : ComponentActivity() {

    private val viewModel: GameBoardViewModel by viewModels {
        val app = application as ChromaFlowApplication
        GameBoardViewModel.Factory(
            repository = app.progressRepository,
            soundEngine = app.soundManager,
            peerTransferEngine = app.peerTransferEngine
        )
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val completedProgress by viewModel.completedProgress.collectAsStateWithLifecycle()
            val tierBestTimes by viewModel.tierBestTimes.collectAsStateWithLifecycle()
            val transferState by viewModel.transferUiState.collectAsStateWithLifecycle()
            val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
            val fileBackupFeedback by viewModel.fileBackupFeedback.collectAsStateWithLifecycle()
            val pendingBackupPreview by viewModel.pendingBackupPreview.collectAsStateWithLifecycle()

            val activeThemeSpec = StudioThemes.get(uiState.activeTheme)

            ChromaFlowTheme(darkTheme = activeThemeSpec.isDark) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            viewModel = viewModel,
                            widthSizeClass = windowSizeClass.widthSizeClass,
                            onPlayClick = {
                                viewModel.resumeStandardMode()
                                navController.navigate(GameRoute) { launchSingleTop = true }
                            },
                            onPlayDailyChallenge = {
                                viewModel.startDailyChallenge()
                                navController.navigate(GameRoute) { launchSingleTop = true }
                            },
                            onOpenLevels = {
                                navController.navigate(LevelsRoute) { launchSingleTop = true }
                            },
                            onOpenSettings = {
                                navController.navigate(SettingsRoute) { launchSingleTop = true }
                            }
                        )
                    }

                    composable<GameRoute> {
                        GameBoardScreen(
                            viewModel = viewModel,
                            widthSizeClass = windowSizeClass.widthSizeClass,
                            onOpenHome = {
                                navController.popBackStack<HomeRoute>(inclusive = false)
                            },
                            onOpenLevels = {
                                navController.navigate(LevelsRoute) { launchSingleTop = true }
                            },
                            onOpenSettings = {
                                navController.navigate(SettingsRoute) { launchSingleTop = true }
                            }
                        )
                    }

                    composable<LevelsRoute> {
                        LevelSelectScreen(
                            theme = activeThemeSpec,
                            currentLevel = uiState.levelNumber,
                            highestUnlockedLevel = maxOf(
                                preferences.highestUnlockedLevel,
                                uiState.levelNumber
                            ),
                            completedProgress = completedProgress,
                            tierBestTimes = tierBestTimes,
                            onSelectLevel = { level ->
                                viewModel.loadLevel(level)
                                navController.navigate(GameRoute) {
                                    popUpTo<HomeRoute> { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            theme = activeThemeSpec,
                            preferences = preferences,
                            completedCount = completedProgress.count { it.isCompleted },
                            transferState = transferState,
                            discoveredPeers = discoveredPeers,
                            fileBackupFeedback = fileBackupFeedback,
                            pendingBackupPreview = pendingBackupPreview,
                            onExportBackup = { onReady ->
                                viewModel.exportBackupForShare(this@MainActivity, onReady)
                            },
                            onImportBackupUri = { uri ->
                                viewModel.previewBackupFromUri(this@MainActivity, uri)
                            },
                            onConfirmReplaceBackup = viewModel::confirmReplaceWithPendingBackup,
                            onDismissBackupPreview = viewModel::dismissPendingBackupPreview,
                            onStartPeerReceive = viewModel::startPeerReceive,
                            onStartPeerDiscover = viewModel::startPeerDiscovery,
                            onSendToPeer = viewModel::sendProgressToPeer,
                            onStopPeerTransfer = viewModel::stopPeerTransfer,
                            onResetTransferState = viewModel::resetTransferState,
                            onSetSound = viewModel::setSoundEnabled,
                            onSetHaptics = viewModel::setHapticsEnabled,
                            onSetColorblind = viewModel::setColorblindEnabled,
                            onSetRandomizeTheme = viewModel::setRandomizeThemePerLevel,
                            onSetTimer = viewModel::setTimerEnabled,
                            onSelectPinnedTheme = viewModel::setPinnedTheme,
                            onBack = {
                                viewModel.stopPeerTransfer()
                                viewModel.clearFileBackupFeedback()
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
