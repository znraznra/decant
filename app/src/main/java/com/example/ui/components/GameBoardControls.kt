package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.prefs.UserPreferences
import com.example.domain.MAX_UNDO_PER_STAGE
import com.example.ui.theme.StudioThemeSpec
import com.example.ui.viewmodel.GameUiState
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun StuckBanner(
    visible: Boolean,
    theme: StudioThemeSpec,
    canUndo: Boolean,
    canAddTube: Boolean,
    onUndoClick: () -> Unit,
    onAddExtraTubeClick: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Surface(
            color = theme.cardBackground,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(vertical = 4.dp)
                .border(1.dp, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "No moves left!",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canUndo) {
                        TextButton(onClick = onUndoClick) {
                            Text("Undo", color = theme.accent)
                        }
                    }
                    if (canAddTube) {
                        TextButton(onClick = onAddExtraTubeClick) {
                            Text("+1 Tube", color = theme.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneBottomActionDock(
    uiState: GameUiState,
    theme: StudioThemeSpec,
    onUndoClick: () -> Unit,
    onRestartClick: () -> Unit,
    onAddExtraTubeClick: () -> Unit
) {
    Surface(
        color = theme.cardBackground,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .border(1.dp, theme.cardBorder, RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockActionItem(
                painter = painterResource(R.drawable.ic_undo),
                label = if (uiState.remainingUndos == 0) "Undo 0/$MAX_UNDO_PER_STAGE" else "Undo",
                badgeCount = uiState.remainingUndos,
                enabled = uiState.canUndo && uiState.pourAnimation == null,
                theme = theme,
                testTag = "undo_button",
                onClick = onUndoClick
            )

            DockActionItem(
                painter = rememberVectorPainter(Icons.Default.Refresh),
                label = "Restart Level",
                badgeCount = 0,
                enabled = uiState.pourAnimation == null,
                theme = theme,
                testTag = "restart_button",
                onClick = onRestartClick
            )

            DockActionItem(
                painter = painterResource(R.drawable.ic_add_circle),
                label = if (uiState.extraTubeUsed) "Used" else "+1 Tube",
                badgeCount = 0,
                enabled = !uiState.extraTubeUsed && uiState.pourAnimation == null,
                theme = theme,
                testTag = "extra_tube_button",
                onClick = onAddExtraTubeClick
            )
        }
    }
}

@Composable
private fun DockActionItem(
    painter: Painter,
    label: String,
    badgeCount: Int,
    enabled: Boolean,
    theme: StudioThemeSpec,
    testTag: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = theme.accentSoft,
            contentColor = theme.accent,
            disabledContainerColor = theme.accentSoft.copy(alpha = 0.35f),
            disabledContentColor = theme.textSecondary.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(containerColor = theme.accent, contentColor = theme.onAccent) {
                        Text(badgeCount.coerceAtMost(99).toString())
                    }
                }
            }
        ) {
            Icon(painter = painter, contentDescription = label, modifier = Modifier.size(19.dp))
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun TabletControlRail(
    uiState: GameUiState,
    preferences: UserPreferences,
    theme: StudioThemeSpec,
    onUndoClick: () -> Unit,
    onRestartClick: () -> Unit,
    onAddExtraTubeClick: () -> Unit,
    onShuffleThemeClick: () -> Unit,
    onToggleColorblind: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenHome: () -> Unit = {},
    onOpenLevels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = theme.cardBackground,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .width(256.dp)
            .fillMaxHeight()
            .border(1.dp, theme.cardBorder, RoundedCornerShape(26.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (uiState.isDailyChallenge) {
                    "Daily • ${uiState.dailyDisplayDate}"
                } else {
                    "Decant!"
                },
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary
            )
            Text(
                text = "${uiState.colorCount} colors • ${uiState.tubes.size} tubes",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onUndoClick,
                enabled = uiState.canUndo && uiState.pourAnimation == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = theme.onAccent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("undo_button")
            ) {
                Icon(painter = painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Undo (${uiState.remainingUndos}/$MAX_UNDO_PER_STAGE)")
            }

            FilledTonalButton(
                onClick = onAddExtraTubeClick,
                enabled = !uiState.extraTubeUsed && uiState.pourAnimation == null,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = theme.accentSoft,
                    contentColor = theme.accent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("extra_tube_button")
            ) {
                Icon(painter = painterResource(R.drawable.ic_add_circle), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (uiState.extraTubeUsed) "Extra Tube Used" else "Add +1 Tube")
            }

            OutlinedButton(
                onClick = onRestartClick,
                enabled = uiState.pourAnimation == null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("restart_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = theme.textPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restart Level", color = theme.textPrimary)
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleColorblind,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_visibility),
                        contentDescription = "Colorblind mode",
                        tint = if (preferences.colorblindEnabled) theme.accent else theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                OutlinedButton(
                    onClick = onToggleSound,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (preferences.soundEnabled) R.drawable.ic_volume_up else R.drawable.ic_volume_off
                        ),
                        contentDescription = "Toggle sound",
                        tint = if (preferences.soundEnabled) theme.accent else theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                OutlinedButton(
                    onClick = onShuffleThemeClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("shuffle_theme_button")
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_palette),
                        contentDescription = "Shuffle theme",
                        tint = theme.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenLevels,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("levels_button")
                ) {
                    Icon(painter = painterResource(R.drawable.ic_grid_view), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Levels")
                }
                FilledTonalButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Settings")
                }
            }

            OutlinedButton(
                onClick = onOpenHome,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("home_button")
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = theme.textPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Main Menu", color = theme.textPrimary)
            }
        }
    }
}

private val TUTORIAL_ARC_DASH = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

@Composable
fun GestureTutorialOverlay(
    fromLip: Offset,
    toLip: Offset,
    isTargetStep: Boolean,
    theme: StudioThemeSpec,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "gestureTutorial")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    val arcPath = remember { Path() }
    val arrowPath = remember { Path() }
    val activeAnchor = if (isTargetStep) toLip else fromLip
    val badgeColor = if (isTargetStep) theme.tubeCompletedGlow else theme.accent

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("gesture_tutorial_overlay")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle curved directional arc showing source -> target pour path
            val midX = (fromLip.x + toLip.x) * 0.5f
            val controlY = minOf(fromLip.y, toLip.y) - 44.dp.toPx()
            val startY = fromLip.y - 6.dp.toPx()
            val endY = toLip.y - 6.dp.toPx()

            arcPath.rewind()
            arcPath.moveTo(fromLip.x, startY)
            arcPath.quadraticTo(midX, controlY, toLip.x, endY)

            drawPath(
                path = arcPath,
                color = badgeColor.copy(alpha = if (isTargetStep) 0.85f else 0.45f),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = TUTORIAL_ARC_DASH
                )
            )

            // Arrowhead pointing cleanly down into target tube lip
            val tangentAngle = atan2((endY - controlY).toDouble(), (toLip.x - midX).toDouble()).toFloat()
            val headLen = 10.dp.toPx()
            val leftAngle = tangentAngle - 2.55f
            val rightAngle = tangentAngle + 2.55f
            arrowPath.rewind()
            arrowPath.moveTo(toLip.x + cos(leftAngle) * headLen, endY + sin(leftAngle) * headLen)
            arrowPath.lineTo(toLip.x, endY)
            arrowPath.lineTo(toLip.x + cos(rightAngle) * headLen, endY + sin(rightAngle) * headLen)
            drawPath(
                path = arrowPath,
                color = badgeColor.copy(alpha = if (isTargetStep) 0.95f else 0.55f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Concentric tap ripple around active gesture target
            val tapCenter = Offset(activeAnchor.x, activeAnchor.y + 18.dp.toPx())
            val ringRadius = (10f + pulse * 18f).dp.toPx()
            drawCircle(
                color = badgeColor.copy(alpha = (1f - pulse) * 0.65f),
                radius = ringRadius,
                center = tapCenter,
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = badgeColor.copy(alpha = 0.22f),
                radius = 10.dp.toPx(),
                center = tapCenter
            )
        }

        // Floating hand/finger tap gesture icon anchored gently below tube lip
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (activeAnchor.x - 10.dp.toPx()).roundToInt(),
                        y = (activeAnchor.y + 14.dp.toPx() + bob * 5.dp.toPx()).roundToInt()
                    )
                }
                .size(36.dp)
                .clip(CircleShape)
                .background(theme.cardBackground.copy(alpha = 0.92f))
                .border(1.5.dp, badgeColor, CircleShape)
                .graphicsLayer {
                    val scale = 1f - bob * 0.08f
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_touch_tap),
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

private const val CONFETTI_COUNT = 44

@Composable
private fun ConfettiBurstCanvas(
    theme: StudioThemeSpec,
    burstSeed: Int,
    modifier: Modifier = Modifier
) {
    val confettiColors = remember(theme.id) {
        val base = theme.liquids.take(6).map { it.topColor }
        if (base.isEmpty()) listOf(theme.accent, theme.tubeCompletedGlow) else base
    }
    val xNorm = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val yNorm = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val vxNorm = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val vyNorm = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val sizeDp = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val angleDeg = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val spinDeg = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val wobblePhase = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val wobbleFreq = remember(burstSeed) { FloatArray(CONFETTI_COUNT) }
    val colorIdx = remember(burstSeed) { IntArray(CONFETTI_COUNT) }
    val shapeKind = remember(burstSeed) { IntArray(CONFETTI_COUNT) }
    val diamondPath = remember { Path() }
    var elapsedMs by remember(burstSeed) { mutableLongStateOf(0L) }

    LaunchedEffect(burstSeed) {
        val rng = Random(burstSeed * 7919 + 31)
        for (i in 0 until CONFETTI_COUNT) {
            xNorm[i] = 0.5f + (rng.nextFloat() - 0.5f) * 0.16f
            yNorm[i] = 0.34f + (rng.nextFloat() - 0.5f) * 0.08f
            val angle = (-158f + rng.nextFloat() * 136f) * (PI.toFloat() / 180f)
            val speed = 0.42f + rng.nextFloat() * 0.85f
            vxNorm[i] = cos(angle) * speed * 0.9f
            vyNorm[i] = sin(angle) * speed
            sizeDp[i] = 5f + rng.nextFloat() * 5.5f
            angleDeg[i] = rng.nextFloat() * 360f
            spinDeg[i] = (rng.nextFloat() - 0.5f) * 520f
            wobblePhase[i] = rng.nextFloat() * 6.283f
            wobbleFreq[i] = 4f + rng.nextFloat() * 5f
            colorIdx[i] = i % confettiColors.size
            shapeKind[i] = i % 3
        }

        var prevNanos = 0L
        var totalSec = 0f
        while (totalSec < 4.2f) {
            val frameNanos = withFrameNanos { it }
            if (prevNanos == 0L) {
                prevNanos = frameNanos
                continue
            }
            val dt = ((frameNanos - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            prevNanos = frameNanos
            totalSec += dt

            var anyVisible = false
            val drag = (1f - 1.15f * dt).coerceAtLeast(0.85f)
            for (i in 0 until CONFETTI_COUNT) {
                vxNorm[i] *= drag
                vyNorm[i] = vyNorm[i] * drag + 0.92f * dt
                xNorm[i] += (vxNorm[i] + sin(wobblePhase[i] + totalSec * wobbleFreq[i]) * 0.11f) * dt
                yNorm[i] += vyNorm[i] * dt
                angleDeg[i] += spinDeg[i] * dt
                if (yNorm[i] <= 1.08f) {
                    anyVisible = true
                }
            }
            elapsedMs = (totalSec * 1000f).toLong()
            if (!anyVisible) break
        }
    }

    Canvas(modifier = modifier) {
        val tick = elapsedMs // Subscribe only Canvas draw scope to frame ticks
        val fadeFactor = if (tick > 3200L) (1f - (tick - 3200L) / 1000f).coerceIn(0f, 1f) else 1f
        if (fadeFactor <= 0.01f) return@Canvas

        val w = size.width
        val h = size.height
        for (i in 0 until CONFETTI_COUNT) {
            val py = yNorm[i] * h
            if (py < -24f || py > h + 24f) continue
            val px = xNorm[i] * w
            val pSize = sizeDp[i].dp.toPx()
            val color = confettiColors[colorIdx[i]].copy(alpha = 0.9f * fadeFactor)

            when (shapeKind[i]) {
                0 -> {
                    rotate(degrees = angleDeg[i], pivot = Offset(px, py)) {
                        drawRect(
                            color = color,
                            topLeft = Offset(px - pSize * 0.55f, py - pSize * 0.28f),
                            size = Size(pSize * 1.1f, pSize * 0.56f)
                        )
                    }
                }
                1 -> {
                    drawCircle(
                        color = color,
                        radius = pSize * 0.36f,
                        center = Offset(px, py)
                    )
                }
                else -> {
                    rotate(degrees = angleDeg[i], pivot = Offset(px, py)) {
                        val half = pSize * 0.48f
                        diamondPath.rewind()
                        diamondPath.moveTo(px, py - half)
                        diamondPath.lineTo(px + half * 0.72f, py)
                        diamondPath.lineTo(px, py + half)
                        diamondPath.lineTo(px - half * 0.72f, py)
                        diamondPath.close()
                        drawPath(path = diamondPath, color = color)
                    }
                }
            }
        }
    }
}

@Composable
fun VictoryCelebrationOverlay(
    levelNumber: Int,
    moveCount: Int,
    isNewBest: Boolean,
    isDailyChallenge: Boolean = false,
    dailyDisplayDate: String = "",
    dailyStreak: Int = 0,
    theme: StudioThemeSpec,
    onNextLevel: () -> Unit,
    onReplay: () -> Unit
) {
    val burstSeed = levelNumber * 37 + moveCount + if (isDailyChallenge) 10007 else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center
    ) {
        ConfettiBurstCanvas(
            theme = theme,
            burstSeed = burstSeed,
            modifier = Modifier
                .fillMaxSize()
                .testTag("confetti_canvas")
        )

        Surface(
            color = theme.cardBackground,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(1.5.dp, theme.cardBorder, RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(theme.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sparkles),
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = if (isDailyChallenge) "Daily Challenge Complete!" else "Level $levelNumber Cleared!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isDailyChallenge && dailyStreak > 0) {
                        "$dailyDisplayDate • Solved in $moveCount moves • $dailyStreak-day streak"
                    } else {
                        "Solved in $moveCount moves"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center
                )

                if (isNewBest) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.tubeCompletedGlow.copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isDailyChallenge) "New Daily Best!" else "New Best Record!",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = theme.tubeCompletedGlow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onNextLevel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent,
                        contentColor = theme.onAccent
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("next_level_button")
                ) {
                    Text(
                        text = if (isDailyChallenge) "Back to Main Menu" else "Next Level (${levelNumber + 1})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                TextButton(
                    onClick = onReplay,
                    modifier = Modifier.testTag("replay_level_button")
                ) {
                    Text(
                        text = if (isDailyChallenge) "Replay Daily" else "Replay Level",
                        color = theme.textSecondary
                    )
                }
            }
        }
    }
}
