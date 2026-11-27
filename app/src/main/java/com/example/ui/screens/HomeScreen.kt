package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.prefs.UserPreferences
import com.example.domain.DailyChallengeCardSummary
import com.example.domain.PuzzleEngine
import com.example.domain.SyncStatus
import com.example.ui.theme.StudioThemeSpec
import com.example.ui.theme.StudioThemes
import com.example.ui.viewmodel.GameBoardViewModel
import com.example.ui.viewmodel.GameUiState
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HomeScreen(
    viewModel: GameBoardViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onPlayClick: () -> Unit,
    onPlayDailyChallenge: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val completedProgress by viewModel.completedProgress.collectAsStateWithLifecycle()
    val dailySummary by viewModel.dailyChallengeSummary.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val theme = StudioThemes.get(uiState.activeTheme)

    val standardActiveLevel = if (uiState.isDailyChallenge) {
        preferences.highestUnlockedLevel.coerceAtLeast(1)
    } else {
        uiState.levelNumber.coerceAtLeast(1)
    }

    HomeScreenContent(
        uiState = uiState,
        preferences = preferences,
        dailySummary = dailySummary,
        syncStatus = syncStatus,
        completedCount = completedProgress.count { it.isCompleted },
        standardActiveLevel = standardActiveLevel,
        highestUnlockedLevel = maxOf(preferences.highestUnlockedLevel, standardActiveLevel),
        theme = theme,
        widthSizeClass = widthSizeClass,
        onPlayClick = onPlayClick,
        onPlayDailyChallenge = onPlayDailyChallenge,
        onOpenLevels = onOpenLevels,
        onOpenSettings = onOpenSettings,
        onShuffleThemeClick = viewModel::onShuffleThemeClick,
        onToggleSound = { viewModel.setSoundEnabled(!preferences.soundEnabled) },
        onToggleColorblind = { viewModel.setColorblindEnabled(!preferences.colorblindEnabled) }
    )
}

@Composable
fun HomeScreenContent(
    uiState: GameUiState,
    preferences: UserPreferences,
    dailySummary: DailyChallengeCardSummary,
    syncStatus: SyncStatus = SyncStatus.Disabled,
    completedCount: Int,
    standardActiveLevel: Int,
    highestUnlockedLevel: Int,
    theme: StudioThemeSpec,
    widthSizeClass: WindowWidthSizeClass,
    onPlayClick: () -> Unit,
    onPlayDailyChallenge: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenSettings: () -> Unit,
    onShuffleThemeClick: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleColorblind: () -> Unit
) {
    val bgTop by animateColorAsState(theme.backgroundTop, tween(450), label = "homeBgTop")
    val bgBottom by animateColorAsState(theme.backgroundBottom, tween(450), label = "homeBgBottom")
    val ambientColor by animateColorAsState(theme.ambientAccent, tween(450), label = "homeAmbient")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientColor.copy(alpha = if (theme.isDark) 0.20f else 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.28f),
                        radius = size.maxDimension * 0.58f
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val useTwoPane = widthSizeClass == WindowWidthSizeClass.Expanded ||
            (widthSizeClass == WindowWidthSizeClass.Medium && maxWidth > maxHeight)
        val availableHeight = maxHeight

        if (useTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 520.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    HeroHeaderSection(
                        theme = theme,
                        syncStatus = syncStatus,
                        isCompactHeight = availableHeight < 560.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    ProgressStatsStrip(
                        currentLevel = standardActiveLevel,
                        completedCount = completedCount,
                        highestUnlockedLevel = highestUnlockedLevel,
                        theme = theme
                    )
                }

                Surface(
                    color = theme.cardBackground,
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier
                        .widthIn(min = 340.dp, max = 440.dp)
                        .border(1.dp, theme.cardBorder, RoundedCornerShape(30.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MainActionHub(
                            uiState = uiState,
                            preferences = preferences,
                            dailySummary = dailySummary,
                            standardActiveLevel = standardActiveLevel,
                            theme = theme,
                            onPlayClick = onPlayClick,
                            onPlayDailyChallenge = onPlayDailyChallenge,
                            onOpenLevels = onOpenLevels,
                            onOpenSettings = onOpenSettings,
                            onShuffleThemeClick = onShuffleThemeClick,
                            onToggleSound = onToggleSound,
                            onToggleColorblind = onToggleColorblind
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    HeroHeaderSection(
                        theme = theme,
                        syncStatus = syncStatus,
                        isCompactHeight = availableHeight < 680.dp
                    )

                    ProgressStatsStrip(
                        currentLevel = standardActiveLevel,
                        completedCount = completedCount,
                        highestUnlockedLevel = highestUnlockedLevel,
                        theme = theme
                    )

                    MainActionHub(
                        uiState = uiState,
                        preferences = preferences,
                        dailySummary = dailySummary,
                        standardActiveLevel = standardActiveLevel,
                        theme = theme,
                        onPlayClick = onPlayClick,
                        onPlayDailyChallenge = onPlayDailyChallenge,
                        onOpenLevels = onOpenLevels,
                        onOpenSettings = onOpenSettings,
                        onShuffleThemeClick = onShuffleThemeClick,
                        onToggleSound = onToggleSound,
                        onToggleColorblind = onToggleColorblind
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroHeaderSection(
    theme: StudioThemeSpec,
    syncStatus: SyncStatus,
    isCompactHeight: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PrivacyOfflineBadge(theme = theme, syncStatus = syncStatus)

        HeroBottlesShowcase(
            theme = theme,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompactHeight) 142.dp else 178.dp)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Decant!",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Infinite Water Sort • Ad-Free Studio",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textSecondary
            )
        }
    }
}

@Composable
private fun PrivacyOfflineBadge(
    theme: StudioThemeSpec,
    syncStatus: SyncStatus
) {
    val badgeText = if (syncStatus is SyncStatus.Synced) {
        "Play Games Saved • Zero Tracking"
    } else {
        "Zero Tracking • No Permissions"
    }
    Surface(
        color = theme.cardBackground,
        shape = CircleShape,
        modifier = Modifier.border(1.dp, theme.cardBorder, CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (syncStatus is SyncStatus.Synced) R.drawable.ic_cloud_done else R.drawable.ic_shield_lock
                ),
                contentDescription = null,
                tint = theme.tubeCompletedGlow,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textPrimary
            )
        }
    }
}

private val SHOWCASE_LEFT_COLORS = intArrayOf(0, 1, 2, 0)
private val SHOWCASE_CENTER_COLORS = intArrayOf(3, 3, 3, 3)
private val SHOWCASE_RIGHT_COLORS = intArrayOf(4, 2, 1, 4)
private val SHOWCASE_SPECULAR_COLORS = listOf(
    Color.White.copy(alpha = 0.36f),
    Color.White.copy(alpha = 0.04f)
)

@Composable
private fun HeroBottlesShowcase(
    theme: StudioThemeSpec,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroShowcase")
    val bobPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bobPhase"
    )
    val tiltPhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tiltPhase"
    )
    val bodyPath = remember { Path() }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height * 0.52f
        val vialWidth = (size.height * 0.27f).coerceIn(34.dp.toPx(), 52.dp.toPx())
        val vialHeight = (size.height * 0.72f).coerceIn(88.dp.toPx(), 132.dp.toPx())
        val spacing = vialWidth * 1.48f

        for (idx in 0..2) {
            val dx = when (idx) {
                0 -> -spacing
                1 -> 0f
                else -> spacing
            }
            val baseAngle = when (idx) {
                0 -> -8f + tiltPhase * 2.5f
                1 -> 0f
                else -> 8f - tiltPhase * 2.5f
            }
            val colorIds = when (idx) {
                0 -> SHOWCASE_LEFT_COLORS
                1 -> SHOWCASE_CENTER_COLORS
                else -> SHOWCASE_RIGHT_COLORS
            }
            val bobY = sin(bobPhase + idx * 1.3f) * 4.dp.toPx()
            val cx = centerX + dx
            val cy = centerY + bobY + if (idx == 1) (-4).dp.toPx() else 6.dp.toPx()

            rotate(degrees = baseAngle, pivot = Offset(cx, cy)) {
                drawShowcaseBottle(
                    bodyPath = bodyPath,
                    topLeft = Offset(cx - vialWidth / 2f, cy - vialHeight / 2f),
                    vialSize = Size(vialWidth, vialHeight),
                    colorIds = colorIds,
                    theme = theme,
                    wavePhase = bobPhase + idx,
                    isHighlighted = idx == 1
                )
            }
        }
    }
}

private fun DrawScope.drawShowcaseBottle(
    bodyPath: Path,
    topLeft: Offset,
    vialSize: Size,
    colorIds: IntArray,
    theme: StudioThemeSpec,
    wavePhase: Float,
    isHighlighted: Boolean
) {
    val bodyRect = Rect(
        left = topLeft.x,
        top = topLeft.y + 6.dp.toPx(),
        right = topLeft.x + vialSize.width,
        bottom = topLeft.y + vialSize.height
    )
    val radius = vialSize.width * 0.44f
    bodyPath.rewind()
    bodyPath.addRoundRect(
        RoundRect(
            rect = bodyRect,
            topLeft = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            topRight = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            bottomLeft = CornerRadius(radius, radius),
            bottomRight = CornerRadius(radius, radius)
        )
    )

    // Subtle glass backdrop
    drawPath(bodyPath, color = theme.tubeGlassFill)

    // Liquid layers inside bottle
    val innerHeight = bodyRect.height - 6.dp.toPx()
    val segHeight = innerHeight / 4.2f
    clipPath(bodyPath) {
        for (segIdx in colorIds.indices) {
            val colorId = colorIds[segIdx]
            val spec = theme.liquids[colorId % theme.liquids.size]
            val segBottom = bodyRect.bottom - segIdx * segHeight
            val segTop = segBottom - segHeight
            val waveBump = if (segIdx == colorIds.lastIndex) sin(wavePhase) * 2.dp.toPx() else 0f

            drawRect(
                brush = Brush.verticalGradient(
                    colors = spec.gradientColors,
                    startY = segTop,
                    endY = segBottom
                ),
                topLeft = Offset(bodyRect.left, segTop + waveBump),
                size = Size(bodyRect.width, segHeight + 3.dp.toPx())
            )
        }

        // Specular highlight stripe
        drawRect(
            brush = Brush.horizontalGradient(
                colors = SHOWCASE_SPECULAR_COLORS,
                startX = bodyRect.left + bodyRect.width * 0.12f,
                endX = bodyRect.left + bodyRect.width * 0.34f
            ),
            topLeft = Offset(bodyRect.left + bodyRect.width * 0.12f, bodyRect.top),
            size = Size(bodyRect.width * 0.20f, bodyRect.height)
        )
    }

    // Outer glass rim & collar
    val strokeColor = if (isHighlighted) theme.tubeCompletedGlow else theme.tubeStroke
    drawPath(
        path = bodyPath,
        color = strokeColor,
        style = Stroke(width = if (isHighlighted) 2.5.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round)
    )

    val rimOverflow = 3.dp.toPx()
    drawLine(
        color = strokeColor,
        start = Offset(bodyRect.left - rimOverflow, bodyRect.top),
        end = Offset(bodyRect.right + rimOverflow, bodyRect.top),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
}

@Composable
private fun ProgressStatsStrip(
    currentLevel: Int,
    completedCount: Int,
    highestUnlockedLevel: Int,
    theme: StudioThemeSpec
) {
    Surface(
        color = theme.cardBackground,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, theme.cardBorder, RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatColumn(
                value = "Lv. $currentLevel",
                label = "Active",
                theme = theme,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(theme.cardBorder)
            )
            StatColumn(
                value = "$completedCount",
                label = "Cleared",
                theme = theme,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(theme.cardBorder)
            )
            StatColumn(
                value = "Lv. $highestUnlockedLevel",
                label = "Unlocked",
                theme = theme,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    theme: StudioThemeSpec,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = theme.textPrimary,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun MainActionHub(
    uiState: GameUiState,
    preferences: UserPreferences,
    dailySummary: DailyChallengeCardSummary,
    standardActiveLevel: Int,
    theme: StudioThemeSpec,
    onPlayClick: () -> Unit,
    onPlayDailyChallenge: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenSettings: () -> Unit,
    onShuffleThemeClick: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleColorblind: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DailyChallengeBannerCard(
            summary = dailySummary,
            theme = theme,
            onClick = onPlayDailyChallenge
        )

        val (standardTier, standardColors) = remember(standardActiveLevel) {
            PuzzleEngine.difficultyForLevel(standardActiveLevel)
        }
        val hasStandardProgress = !uiState.isDailyChallenge && uiState.moveCount > 0 && !uiState.isLevelComplete
        val playTitle = if (hasStandardProgress) {
            "Continue Level $standardActiveLevel"
        } else {
            "Play Level $standardActiveLevel"
        }
        val playSubtitle = if (hasStandardProgress) {
            "${uiState.moveCount} moves played • ${standardTier.label}"
        } else {
            "${standardTier.label} • $standardColors Colors"
        }

        Button(
            onClick = onPlayClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.accent,
                contentColor = theme.onAccent
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .testTag("home_play_button")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = playTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = playSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.onAccent.copy(alpha = 0.85f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MenuNavCard(
                painter = painterResource(R.drawable.ic_grid_view),
                title = "Levels",
                subtitle = "Infinite Grid",
                theme = theme,
                testTag = "home_levels_button",
                onClick = onOpenLevels,
                modifier = Modifier.weight(1f)
            )
            MenuNavCard(
                painter = rememberVectorPainter(Icons.Default.Settings),
                title = "Settings",
                subtitle = "Privacy & Audio",
                theme = theme,
                testTag = "home_settings_button",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickStudioChip(
                painter = painterResource(R.drawable.ic_palette),
                label = uiState.activeTheme.displayName,
                active = true,
                theme = theme,
                testTag = "home_theme_button",
                onClick = onShuffleThemeClick,
                modifier = Modifier.weight(1.3f)
            )
            QuickStudioChip(
                painter = painterResource(
                    if (preferences.soundEnabled) R.drawable.ic_volume_up else R.drawable.ic_volume_off
                ),
                label = if (preferences.soundEnabled) "Sound" else "Muted",
                active = preferences.soundEnabled,
                theme = theme,
                testTag = "home_sound_button",
                onClick = onToggleSound,
                modifier = Modifier.weight(1f)
            )
            QuickStudioChip(
                painter = painterResource(R.drawable.ic_visibility),
                label = "Symbols",
                active = preferences.colorblindEnabled,
                theme = theme,
                testTag = "home_colorblind_button",
                onClick = onToggleColorblind,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DailyChallengeBannerCard(
    summary: DailyChallengeCardSummary,
    theme: StudioThemeSpec,
    onClick: () -> Unit
) {
    val highlightColor = if (summary.isCompleted) theme.tubeCompletedGlow else theme.accent
    val statusText = when {
        summary.isCompleted -> {
            val best = summary.bestMoves ?: summary.moveCount
            "Completed in $best moves • Tap to replay"
        }
        summary.inProgress -> "In progress (${summary.moveCount} moves) • ${summary.tier.label}"
        else -> "Same puzzle for everyone • ${summary.colorCount} Colors"
    }

    Surface(
        color = if (summary.isCompleted) theme.cardBackground else theme.accentSoft,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(22.dp))
            .border(
                1.5.dp,
                highlightColor.copy(alpha = if (summary.isCompleted) 0.55f else 0.45f),
                RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .testTag("home_daily_challenge_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(highlightColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar_today),
                    contentDescription = "Daily Challenge",
                    tint = highlightColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Daily Challenge",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = theme.textPrimary,
                        maxLines = 1
                    )
                    if (summary.displayDate.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.cardBackground.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = summary.displayDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = highlightColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (summary.currentStreak > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(highlightColor)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${summary.currentStreak}d Streak",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.onAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuNavCard(
    painter: Painter,
    title: String,
    subtitle: String,
    theme: StudioThemeSpec,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = theme.cardBackground,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, theme.cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = theme.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = theme.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickStudioChip(
    painter: Painter,
    label: String,
    active: Boolean,
    theme: StudioThemeSpec,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (active) theme.accentSoft else theme.cardBackground,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(48.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (active) theme.accent.copy(alpha = 0.45f) else theme.cardBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                painter = painter,
                contentDescription = label,
                tint = if (active) theme.accent else theme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) theme.accent else theme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
