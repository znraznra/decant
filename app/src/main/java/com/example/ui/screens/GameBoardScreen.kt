package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.prefs.UserPreferences
import com.example.domain.PuzzleEngine
import com.example.domain.formatElapsedDuration
import com.example.ui.components.GestureTutorialOverlay
import com.example.ui.components.PhoneBottomActionDock
import com.example.ui.components.StuckBanner
import com.example.ui.components.TabletControlRail
import com.example.ui.components.VictoryCelebrationOverlay
import com.example.ui.components.WaterTubeItem
import com.example.ui.theme.StudioThemeSpec
import com.example.ui.theme.StudioThemes
import com.example.ui.viewmodel.GameBoardViewModel
import com.example.ui.viewmodel.GameUiState
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun GameBoardScreen(
    viewModel: GameBoardViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onOpenHome: () -> Unit = {},
    onOpenLevels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val dailySummary by viewModel.dailyChallengeSummary.collectAsStateWithLifecycle()
    val theme = StudioThemes.get(uiState.activeTheme)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setBoardActive(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setBoardActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.setBoardActive(true)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setBoardActive(false)
        }
    }

    GameBoardContent(
        uiState = uiState,
        preferences = preferences,
        dailyStreak = dailySummary.currentStreak,
        theme = theme,
        widthSizeClass = widthSizeClass,
        onTubeClick = viewModel::onTubeClick,
        onUndoClick = viewModel::onUndoClick,
        onRestartClick = viewModel::restartLevel,
        onAddExtraTubeClick = viewModel::onAddExtraTubeClick,
        onShuffleThemeClick = viewModel::onShuffleThemeClick,
        onNextLevelClick = viewModel::onNextLevelClick,
        onToggleColorblind = { viewModel.setColorblindEnabled(!preferences.colorblindEnabled) },
        onToggleSound = { viewModel.setSoundEnabled(!preferences.soundEnabled) },
        onOpenHome = onOpenHome,
        onOpenLevels = onOpenLevels,
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun GameBoardContent(
    uiState: GameUiState,
    preferences: UserPreferences,
    dailyStreak: Int = 0,
    theme: StudioThemeSpec,
    widthSizeClass: WindowWidthSizeClass,
    onTubeClick: (Int) -> Unit,
    onUndoClick: () -> Unit,
    onRestartClick: () -> Unit,
    onAddExtraTubeClick: () -> Unit,
    onShuffleThemeClick: () -> Unit,
    onNextLevelClick: () -> Unit,
    onToggleColorblind: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenHome: () -> Unit = {},
    onOpenLevels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val bgTop by animateColorAsState(theme.backgroundTop, tween(450), label = "bgTop")
    val bgBottom by animateColorAsState(theme.backgroundBottom, tween(450), label = "bgBottom")
    val ambientColor by animateColorAsState(theme.ambientAccent, tween(450), label = "ambient")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientColor.copy(alpha = if (theme.isDark) 0.16f else 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.42f),
                        radius = size.maxDimension * 0.55f
                    )
                )
            }
            .statusBarsPadding()
    ) {
        // Use two-pane layout on wide/tablet/landscape displays, vertical studio layout on compact portrait phones
        val useTwoPane = widthSizeClass == WindowWidthSizeClass.Expanded ||
            (widthSizeClass == WindowWidthSizeClass.Medium && maxWidth > maxHeight)

        if (useTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CompactTopHeader(
                        uiState = uiState,
                        timerEnabled = preferences.timerEnabled,
                        theme = theme,
                        showNavActions = false,
                        onShuffleThemeClick = onShuffleThemeClick,
                        onOpenHome = onOpenHome,
                        onOpenLevels = onOpenLevels,
                        onOpenSettings = onOpenSettings
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PuzzleStageArea(
                        uiState = uiState,
                        theme = theme,
                        colorblindEnabled = preferences.colorblindEnabled,
                        isWideStage = true,
                        onTubeClick = onTubeClick,
                        modifier = Modifier.weight(1f)
                    )
                    StuckBanner(
                        visible = !uiState.hasValidMovesLeft && !uiState.isLevelComplete,
                        theme = theme,
                        canUndo = uiState.canUndo,
                        canAddTube = !uiState.extraTubeUsed,
                        onUndoClick = onUndoClick,
                        onAddExtraTubeClick = onAddExtraTubeClick
                    )
                }

                TabletControlRail(
                    uiState = uiState,
                    preferences = preferences,
                    theme = theme,
                    onUndoClick = onUndoClick,
                    onRestartClick = onRestartClick,
                    onAddExtraTubeClick = onAddExtraTubeClick,
                    onShuffleThemeClick = onShuffleThemeClick,
                    onToggleColorblind = onToggleColorblind,
                    onToggleSound = onToggleSound,
                    onOpenHome = onOpenHome,
                    onOpenLevels = onOpenLevels,
                    onOpenSettings = onOpenSettings
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CompactTopHeader(
                    uiState = uiState,
                    timerEnabled = preferences.timerEnabled,
                    theme = theme,
                    showNavActions = true,
                    onShuffleThemeClick = onShuffleThemeClick,
                    onOpenHome = onOpenHome,
                    onOpenLevels = onOpenLevels,
                    onOpenSettings = onOpenSettings
                )

                PuzzleStageArea(
                    uiState = uiState,
                    theme = theme,
                    colorblindEnabled = preferences.colorblindEnabled,
                    isWideStage = widthSizeClass != WindowWidthSizeClass.Compact,
                    onTubeClick = onTubeClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                StuckBanner(
                    visible = !uiState.hasValidMovesLeft && !uiState.isLevelComplete,
                    theme = theme,
                    canUndo = uiState.canUndo,
                    canAddTube = !uiState.extraTubeUsed,
                    onUndoClick = onUndoClick,
                    onAddExtraTubeClick = onAddExtraTubeClick
                )

                Spacer(modifier = Modifier.height(6.dp))

                PhoneBottomActionDock(
                    uiState = uiState,
                    theme = theme,
                    onUndoClick = onUndoClick,
                    onRestartClick = onRestartClick,
                    onAddExtraTubeClick = onAddExtraTubeClick
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.isLevelComplete,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut()
        ) {
            VictoryCelebrationOverlay(
                levelNumber = uiState.levelNumber,
                moveCount = uiState.moveCount,
                isNewBest = uiState.isNewBestMoves,
                isNewBestTime = uiState.isNewBestTime,
                timerEnabled = preferences.timerEnabled,
                elapsedTimeMs = uiState.elapsedTimeMs,
                bestTierTimeMs = uiState.bestTierTimeMs,
                tierLabel = uiState.tier.label,
                isDailyChallenge = uiState.isDailyChallenge,
                dailyDisplayDate = uiState.dailyDisplayDate,
                dailyStreak = dailyStreak,
                theme = theme,
                onNextLevel = if (uiState.isDailyChallenge) onOpenHome else onNextLevelClick,
                onReplay = onRestartClick
            )
        }
    }
}

@Composable
private fun CompactTopHeader(
    uiState: GameUiState,
    timerEnabled: Boolean,
    theme: StudioThemeSpec,
    showNavActions: Boolean,
    onShuffleThemeClick: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showNavActions) {
            HeaderActionCircle(theme = theme, testTag = "home_button", onClick = onOpenHome) {
                Icon(Icons.Default.Home, contentDescription = "Main menu", tint = theme.textPrimary)
            }
        }

        Surface(
            color = theme.cardBackground,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .weight(1f, fill = showNavActions)
                .border(1.dp, theme.cardBorder, RoundedCornerShape(18.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (uiState.isDailyChallenge) {
                                "Daily • ${uiState.dailyDisplayDate}"
                            } else {
                                "Lv. ${uiState.levelNumber}"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = theme.textPrimary,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.accentSoft)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (uiState.isDailyChallenge) "Daily" else uiState.tier.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            append("${uiState.colorCount} colors • ${uiState.tubes.size} tubes • ${uiState.moveCount} moves")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (timerEnabled) {
                        val currentTime = formatElapsedDuration(uiState.elapsedTimeMs)
                        val bestText = uiState.bestTierTimeMs?.let { best ->
                            " • Best ${formatElapsedDuration(best)}"
                        }.orEmpty()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.testTag("level_timer_label")
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_timer),
                                contentDescription = null,
                                tint = theme.textSecondary.copy(alpha = 0.78f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$currentTime$bestText",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = theme.textSecondary.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (showNavActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HeaderActionCircle(theme = theme, testTag = "shuffle_theme_button", onClick = onShuffleThemeClick) {
                    Icon(painter = painterResource(R.drawable.ic_palette), contentDescription = "Switch studio theme", tint = theme.accent, modifier = Modifier.size(22.dp))
                }
                HeaderActionCircle(theme = theme, testTag = "levels_button", onClick = onOpenLevels) {
                    Icon(painter = painterResource(R.drawable.ic_grid_view), contentDescription = "Level select", tint = theme.textPrimary, modifier = Modifier.size(22.dp))
                }
                HeaderActionCircle(theme = theme, testTag = "settings_button", onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = theme.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun HeaderActionCircle(
    theme: StudioThemeSpec,
    testTag: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        color = theme.cardBackground,
        shape = CircleShape,
        modifier = Modifier.border(1.dp, theme.cardBorder, CircleShape)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag),
            content = icon
        )
    }
}

@Composable
private fun PuzzleStageArea(
    uiState: GameUiState,
    theme: StudioThemeSpec,
    colorblindEnabled: Boolean,
    isWideStage: Boolean,
    onTubeClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = theme.accent)
        }
        return
    }

    val tubes = uiState.tubes
    val tubeLipCenters = remember { mutableStateMapOf<Int, Offset>() }
    val stageRootOrigin = remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(tubes.size) {
        tubeLipCenters.clear()
    }

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coords ->
            stageRootOrigin.value = coords.positionInRoot()
        },
        contentAlignment = Alignment.Center
    ) {
        val count = tubes.size.coerceAtLeast(1)
        val maxPerRow = when {
            isWideStage && count <= 7 -> count
            isWideStage -> ceil(count / 2.0).toInt().coerceIn(4, 7)
            count <= 5 -> count
            count <= 10 -> ceil(count / 2.0).toInt()
            else -> ceil(count / 3.0).toInt().coerceAtMost(5)
        }

        val rows = remember(tubes, maxPerRow) { tubes.chunked(maxPerRow) }
        val numRows = rows.size.coerceAtLeast(1)
        val hGap = if (isWideStage) 18.dp else 10.dp
        val vGap = if (isWideStage) 20.dp else 16.dp

        val availableW = (maxWidth - hGap * (maxPerRow - 1) - 16.dp).coerceAtLeast(180.dp)
        val availableH = (maxHeight - vGap * (numRows - 1) - 24.dp).coerceAtLeast(160.dp)

        val maxTubeW = availableW / maxPerRow
        val maxTubeH = availableH / numRows
        val aspect = 0.37f

        val rawWidth = min(maxTubeW.value, maxTubeH.value * aspect)
        val tubeWidth: Dp = rawWidth.coerceIn(44f, 88f).dp
        val tubeHeight: Dp = (tubeWidth.value / aspect).coerceIn(118f, 238f).dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(vGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var globalIndex = 0
            rows.forEach { rowTubes ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(hGap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowTubes.forEach { tube ->
                        val idx = globalIndex++
                        WaterTubeItem(
                            index = idx,
                            tube = tube,
                            theme = theme,
                            isSelected = uiState.selectedTubeIndex == idx,
                            isValidTarget = idx in uiState.validTargetIndices,
                            shakeTrigger = if (uiState.invalidShakeIndex == idx) uiState.invalidShakeTick else 0,
                            pourAnimation = uiState.pourAnimation,
                            colorblindEnabled = colorblindEnabled,
                            tubeWidth = tubeWidth,
                            tubeHeight = tubeHeight,
                            onClick = { onTubeClick(idx) },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val rootPos = coords.positionInRoot() - stageRootOrigin.value
                                val lipX = rootPos.x + coords.size.width * 0.5f
                                val lipY = rootPos.y + coords.size.height * 0.06f
                                tubeLipCenters[idx] = Offset(lipX, lipY)
                            }
                        )
                    }
                }
            }
        }

        val showTutorial = uiState.levelNumber == 1 &&
            !uiState.isDailyChallenge &&
            uiState.moveCount == 0 &&
            !uiState.isLevelComplete &&
            uiState.pourAnimation == null
        val tutorialMove = remember(tubes, uiState.selectedTubeIndex, showTutorial) {
            if (showTutorial) PuzzleEngine.findTutorialMove(tubes, uiState.selectedTubeIndex) else null
        }

        val anim = uiState.pourAnimation
        if (anim != null) {
            val fromPos = tubeLipCenters[anim.fromIndex]
            val toPos = tubeLipCenters[anim.toIndex]
            if (fromPos != null && toPos != null) {
                val colorSpec = theme.liquids.getOrElse(anim.colorId) { theme.liquids.first() }
                PourStreamCanvas(
                    from = fromPos,
                    to = toPos,
                    color = colorSpec.topColor,
                    progress = anim.progress
                )
            }
        } else if (tutorialMove != null) {
            val fromPos = tubeLipCenters[tutorialMove.first]
            val toPos = tubeLipCenters[tutorialMove.second]
            if (fromPos != null && toPos != null) {
                GestureTutorialOverlay(
                    fromLip = fromPos,
                    toLip = toPos,
                    isTargetStep = uiState.selectedTubeIndex == tutorialMove.first,
                    theme = theme
                )
            }
        }
    }
}

@Composable
private fun PourStreamCanvas(
    from: Offset,
    to: Offset,
    color: Color,
    progress: Float
) {
    val streamPath = remember { Path() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val midX = (from.x + to.x) * 0.5f
        val arcHeight = minOf(from.y, to.y) - 42.dp.toPx()
        streamPath.rewind()
        streamPath.moveTo(from.x, from.y)
        streamPath.quadraticTo(midX, arcHeight, to.x, to.y + 18.dp.toPx())
        val alpha = sin(progress * PI.toFloat()).coerceIn(0.25f, 1f)
        drawPath(
            path = streamPath,
            color = color.copy(alpha = alpha * 0.85f),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = streamPath,
            color = Color.White.copy(alpha = alpha * 0.35f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
