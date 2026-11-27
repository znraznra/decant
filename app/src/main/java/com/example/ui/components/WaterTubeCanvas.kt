package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.domain.ColorblindPattern
import com.example.domain.PourAnimationState
import com.example.domain.TUBE_CAPACITY
import com.example.domain.TubeModel
import com.example.ui.theme.BottleShapeStyle
import com.example.ui.theme.LiquidColorSpec
import com.example.ui.theme.StudioThemeSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val SHAKE_PATTERN = floatArrayOf(-10f, 10f, -7f, 7f, -4f, 0f)
private val STRIPE_OFFSETS = floatArrayOf(-0.45f, 0f, 0.45f)
private val GLASS_REFLECTION_COLORS = listOf(
    Color.White.copy(alpha = 0.55f),
    Color.White.copy(alpha = 0.12f)
)
private val EXTRA_TUBE_DASH = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

@Composable
fun WaterTubeItem(
    index: Int,
    tube: TubeModel,
    theme: StudioThemeSpec,
    isSelected: Boolean,
    isValidTarget: Boolean = false,
    shakeTrigger: Int,
    pourAnimation: PourAnimationState?,
    colorblindEnabled: Boolean,
    tubeWidth: Dp,
    tubeHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liftOffset by animateDpAsState(
        targetValue = if (isSelected) (-14).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tubeLift"
    )

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tubeScale"
    )

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            shakeAnim.snapTo(0f)
            for (target in SHAKE_PATTERN) {
                shakeAnim.animateTo(target, tween(45))
            }
        }
    }

    val colorNames = remember(tube.segments, theme.id) {
        if (tube.segments.isEmpty()) "empty"
        else tube.segments.reversed().joinToString(", ") { colorId ->
            theme.liquids.getOrElse(colorId) { theme.liquids.first() }.name
        }
    }
    val accessibilityLabel = remember(index, tube.isCompleted, tube.isExtraTube, colorNames) {
        buildString {
            append("Tube ${index + 1}, ")
            if (tube.isCompleted) append("completed, ")
            if (tube.isExtraTube) append("extra rescue tube, ")
            append(colorNames)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val interiorPath = remember { Path() }
    val scratchPath = remember { Path() }

    Box(
        modifier = modifier
            .offset { IntOffset(shakeAnim.value.roundToInt(), liftOffset.roundToPx()) }
            .size(width = tubeWidth, height = tubeHeight)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .testTag("tube_$index")
            .semantics { contentDescription = accessibilityLabel }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = tubeWidth * 0.7f),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawWaterTube(
                interiorPath = interiorPath,
                scratchPath = scratchPath,
                index = index,
                tube = tube,
                theme = theme,
                isSelected = isSelected,
                isValidTarget = isValidTarget,
                pourAnimation = pourAnimation,
                colorblindEnabled = colorblindEnabled
            )
        }
    }
}

private fun DrawScope.drawWaterTube(
    interiorPath: Path,
    scratchPath: Path,
    index: Int,
    tube: TubeModel,
    theme: StudioThemeSpec,
    isSelected: Boolean,
    isValidTarget: Boolean,
    pourAnimation: PourAnimationState?,
    colorblindEnabled: Boolean
) {
    val w = size.width
    val h = size.height
    val rimHeight = h * 0.085f
    val bodyTop = rimHeight * 1.1f
    val bodyBottom = h * 0.96f
    val bodyHeight = bodyBottom - bodyTop

    val bodyInset = when (theme.bottleStyle) {
        BottleShapeStyle.APOTHECARY -> w * 0.10f
        BottleShapeStyle.SLIM_NEON -> w * 0.16f
        BottleShapeStyle.ROUNDED_LAB -> w * 0.13f
        BottleShapeStyle.CERAMIC_VIAL -> w * 0.12f
    }
    val bodyLeft = bodyInset
    val bodyRight = w - bodyInset
    val bodyWidth = bodyRight - bodyLeft
    val bottomRadius = when (theme.bottleStyle) {
        BottleShapeStyle.APOTHECARY -> bodyWidth * 0.48f
        BottleShapeStyle.SLIM_NEON -> bodyWidth * 0.34f
        BottleShapeStyle.ROUNDED_LAB -> bodyWidth * 0.45f
        BottleShapeStyle.CERAMIC_VIAL -> bodyWidth * 0.28f
    }

    interiorPath.rewind()
    interiorPath.addRoundRect(
        RoundRect(
            rect = Rect(bodyLeft, bodyTop, bodyRight, bodyBottom),
            topLeft = CornerRadius(4f, 4f),
            topRight = CornerRadius(4f, 4f),
            bottomLeft = CornerRadius(bottomRadius, bottomRadius),
            bottomRight = CornerRadius(bottomRadius, bottomRadius)
        )
    )

    // Soft ambient glow when selected, valid pour target, or completed
    if (isSelected || isValidTarget || tube.isCompleted) {
        val glowColor = when {
            tube.isCompleted -> theme.tubeCompletedGlow
            isSelected -> theme.tubeSelectedStroke
            else -> theme.accent
        }
        val glowAlpha = when {
            isSelected || tube.isCompleted -> if (theme.isDark) 0.26f else 0.16f
            else -> if (theme.isDark) 0.14f else 0.09f
        }
        drawRoundRect(
            color = glowColor.copy(alpha = glowAlpha),
            topLeft = Offset(bodyLeft - 5.dp.toPx(), bodyTop - 4.dp.toPx()),
            size = Size(bodyWidth + 10.dp.toPx(), bodyHeight + 10.dp.toPx()),
            cornerRadius = CornerRadius(bottomRadius + 4.dp.toPx(), bottomRadius + 4.dp.toPx())
        )
    }

    // Glass interior tint
    drawPath(path = interiorPath, color = theme.tubeGlassFill)

    // Compute animated liquid rendering state
    val isPourSource = pourAnimation?.fromIndex == index
    val isPourTarget = pourAnimation?.toIndex == index

    val segmentHeight = (bodyHeight * 0.88f) / TUBE_CAPACITY
    val liquidBottom = bodyBottom - bodyHeight * 0.03f

    clipPath(interiorPath) {
        val segments = tube.segments
        val animUnits = pourAnimation?.units ?: 0
        val animProgress = pourAnimation?.progress ?: 0f

        if (isPourSource && pourAnimation != null) {
            val baseCount = (segments.size - animUnits).coerceAtLeast(0)
            for (sIdx in 0 until baseCount) {
                val colorSpec = theme.liquids.getOrElse(segments[sIdx]) { theme.liquids.first() }
                drawLiquidLayer(
                    scratchPath = scratchPath,
                    left = bodyLeft,
                    right = bodyRight,
                    bottomY = liquidBottom - sIdx * segmentHeight,
                    layerHeight = segmentHeight,
                    spec = colorSpec,
                    isTopLayer = false,
                    colorblindEnabled = colorblindEnabled,
                    wavePhase = 0f
                )
            }
            val remainingFraction = (1f - animProgress) * animUnits
            if (remainingFraction > 0.01f) {
                val colorSpec = theme.liquids.getOrElse(pourAnimation.colorId) { theme.liquids.first() }
                drawLiquidLayer(
                    scratchPath = scratchPath,
                    left = bodyLeft,
                    right = bodyRight,
                    bottomY = liquidBottom - baseCount * segmentHeight,
                    layerHeight = segmentHeight * remainingFraction,
                    spec = colorSpec,
                    isTopLayer = true,
                    colorblindEnabled = colorblindEnabled && remainingFraction >= 0.45f,
                    wavePhase = animProgress * 2f * PI.toFloat()
                )
            }
        } else if (isPourTarget && pourAnimation != null) {
            for (sIdx in segments.indices) {
                val colorSpec = theme.liquids.getOrElse(segments[sIdx]) { theme.liquids.first() }
                drawLiquidLayer(
                    scratchPath = scratchPath,
                    left = bodyLeft,
                    right = bodyRight,
                    bottomY = liquidBottom - sIdx * segmentHeight,
                    layerHeight = segmentHeight,
                    spec = colorSpec,
                    isTopLayer = false,
                    colorblindEnabled = colorblindEnabled,
                    wavePhase = 0f
                )
            }
            val addedFraction = animProgress * animUnits
            if (addedFraction > 0.01f) {
                val colorSpec = theme.liquids.getOrElse(pourAnimation.colorId) { theme.liquids.first() }
                drawLiquidLayer(
                    scratchPath = scratchPath,
                    left = bodyLeft,
                    right = bodyRight,
                    bottomY = liquidBottom - segments.size * segmentHeight,
                    layerHeight = segmentHeight * addedFraction,
                    spec = colorSpec,
                    isTopLayer = true,
                    colorblindEnabled = colorblindEnabled && addedFraction >= 0.45f,
                    wavePhase = animProgress * 3f * PI.toFloat()
                )
            }
        } else {
            for (sIdx in segments.indices) {
                val colorSpec = theme.liquids.getOrElse(segments[sIdx]) { theme.liquids.first() }
                drawLiquidLayer(
                    scratchPath = scratchPath,
                    left = bodyLeft,
                    right = bodyRight,
                    bottomY = liquidBottom - sIdx * segmentHeight,
                    layerHeight = segmentHeight,
                    spec = colorSpec,
                    isTopLayer = sIdx == segments.lastIndex,
                    colorblindEnabled = colorblindEnabled,
                    wavePhase = 0f
                )
            }
        }
    }

    // Specular glass reflection streak on left side
    drawLine(
        brush = Brush.verticalGradient(
            colors = GLASS_REFLECTION_COLORS,
            startY = bodyTop + bodyHeight * 0.08f,
            endY = bodyBottom - bodyHeight * 0.14f
        ),
        start = Offset(bodyLeft + bodyWidth * 0.17f, bodyTop + bodyHeight * 0.08f),
        end = Offset(bodyLeft + bodyWidth * 0.17f, bodyBottom - bodyHeight * 0.14f),
        strokeWidth = (w * 0.055f).coerceAtLeast(2.dp.toPx()),
        cap = StrokeCap.Round
    )

    // Tube outer glass stroke
    val borderColor = when {
        isSelected -> theme.tubeSelectedStroke
        tube.isCompleted -> theme.tubeCompletedGlow
        else -> theme.tubeStroke
    }
    val strokeWidth = if (isSelected || tube.isCompleted) 2.6.dp.toPx() else 1.8.dp.toPx()
    val dashEffect = if (tube.isExtraTube && tube.isEmpty && !isSelected) EXTRA_TUBE_DASH else null

    drawPath(
        path = interiorPath,
        color = borderColor,
        style = Stroke(width = strokeWidth, pathEffect = dashEffect, join = StrokeJoin.Round)
    )

    // Flared top bottle rim
    val rimOverhang = w * 0.05f
    val rimRect = RoundRect(
        left = bodyLeft - rimOverhang,
        top = bodyTop - rimHeight * 0.85f,
        right = bodyRight + rimOverhang,
        bottom = bodyTop + rimHeight * 0.2f,
        cornerRadius = CornerRadius(rimHeight * 0.5f, rimHeight * 0.5f)
    )
    scratchPath.rewind()
    scratchPath.addRoundRect(rimRect)
    drawPath(path = scratchPath, color = theme.tubeGlassFill)
    drawPath(
        path = scratchPath,
        color = borderColor,
        style = Stroke(width = strokeWidth * 0.9f)
    )

    // Completed crown star / checkmark badge at rim
    if (tube.isCompleted) {
        val badgeCenter = Offset(w * 0.5f, bodyTop - rimHeight * 0.35f)
        drawCircle(
            color = theme.tubeCompletedGlow,
            radius = w * 0.11f,
            center = badgeCenter
        )
        drawCheckGlyph(scratchPath = scratchPath, center = badgeCenter, radius = w * 0.06f, color = Color.White)
    }
}

private fun DrawScope.drawLiquidLayer(
    scratchPath: Path,
    left: Float,
    right: Float,
    bottomY: Float,
    layerHeight: Float,
    spec: LiquidColorSpec,
    isTopLayer: Boolean,
    colorblindEnabled: Boolean,
    wavePhase: Float
) {
    val topY = bottomY - layerHeight
    val width = right - left
    val brush = Brush.verticalGradient(
        colors = spec.gradientColors,
        startY = topY,
        endY = bottomY
    )

    if (isTopLayer) {
        val waveAmp = if (wavePhase == 0f) layerHeight * 0.04f else layerHeight * 0.09f
        scratchPath.rewind()
        scratchPath.moveTo(left, bottomY + 1f)
        scratchPath.lineTo(left, topY)
        val steps = 16
        for (i in 0..steps) {
            val frac = i.toFloat() / steps
            val x = left + width * frac
            val yOffset = sin(frac * PI.toFloat() * 2f + wavePhase) * waveAmp
            scratchPath.lineTo(x, topY + yOffset)
        }
        scratchPath.lineTo(right, bottomY + 1f)
        scratchPath.close()
        drawPath(path = scratchPath, brush = brush)

        // Subtle meniscus rim highlight
        drawLine(
            color = Color.White.copy(alpha = 0.38f),
            start = Offset(left + width * 0.12f, topY + 1.5.dp.toPx()),
            end = Offset(right - width * 0.12f, topY + 1.5.dp.toPx()),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    } else {
        drawRect(
            brush = brush,
            topLeft = Offset(left, topY),
            size = Size(width, layerHeight + 1.5f)
        )
    }

    if (colorblindEnabled) {
        drawColorblindPattern(
            scratchPath = scratchPath,
            center = Offset((left + right) * 0.5f, (topY + bottomY) * 0.5f),
            boxSize = minOf(width, layerHeight) * 0.52f,
            pattern = spec.pattern,
            color = spec.symbolColor.copy(alpha = 0.88f)
        )
    }
}

fun DrawScope.drawColorblindPattern(
    center: Offset,
    boxSize: Float,
    pattern: ColorblindPattern,
    color: Color,
    scratchPath: Path = Path()
) {
    val r = boxSize * 0.5f
    val stroke = Stroke(width = (boxSize * 0.14f).coerceAtLeast(1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (pattern) {
        ColorblindPattern.CIRCLE -> {
            drawCircle(color = color, radius = r * 0.72f, center = center)
        }
        ColorblindPattern.TRIANGLE -> {
            scratchPath.rewind()
            scratchPath.moveTo(center.x, center.y - r * 0.8f)
            scratchPath.lineTo(center.x + r * 0.78f, center.y + r * 0.65f)
            scratchPath.lineTo(center.x - r * 0.78f, center.y + r * 0.65f)
            scratchPath.close()
            drawPath(scratchPath, color = color, style = stroke)
        }
        ColorblindPattern.DIAMOND -> {
            scratchPath.rewind()
            scratchPath.moveTo(center.x, center.y - r * 0.85f)
            scratchPath.lineTo(center.x + r * 0.75f, center.y)
            scratchPath.lineTo(center.x, center.y + r * 0.85f)
            scratchPath.lineTo(center.x - r * 0.75f, center.y)
            scratchPath.close()
            drawPath(scratchPath, color = color)
        }
        ColorblindPattern.WAVES -> {
            scratchPath.rewind()
            scratchPath.moveTo(center.x - r, center.y)
            scratchPath.quadraticTo(center.x - r * 0.5f, center.y - r * 0.65f, center.x, center.y)
            scratchPath.quadraticTo(center.x + r * 0.5f, center.y + r * 0.65f, center.x + r, center.y)
            drawPath(scratchPath, color = color, style = stroke)
        }
        ColorblindPattern.STRIPES -> {
            for (offset in STRIPE_OFFSETS) {
                drawLine(
                    color = color,
                    start = Offset(center.x + r * (offset - 0.35f), center.y + r * 0.6f),
                    end = Offset(center.x + r * (offset + 0.35f), center.y - r * 0.6f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
        }
        ColorblindPattern.DOTS -> {
            val dotR = r * 0.24f
            drawCircle(color, dotR, Offset(center.x - r * 0.45f, center.y - r * 0.35f))
            drawCircle(color, dotR, Offset(center.x + r * 0.45f, center.y - r * 0.35f))
            drawCircle(color, dotR, Offset(center.x, center.y + r * 0.42f))
        }
        ColorblindPattern.STAR -> {
            scratchPath.rewind()
            for (i in 0 until 10) {
                val angle = -PI / 2.0 + i * PI / 5.0
                val rad = if (i % 2 == 0) r * 0.9f else r * 0.4f
                val px = center.x + (cos(angle) * rad).toFloat()
                val py = center.y + (sin(angle) * rad).toFloat()
                if (i == 0) scratchPath.moveTo(px, py) else scratchPath.lineTo(px, py)
            }
            scratchPath.close()
            drawPath(scratchPath, color = color)
        }
        ColorblindPattern.PLUS -> {
            drawLine(color, Offset(center.x - r * 0.7f, center.y), Offset(center.x + r * 0.7f, center.y), strokeWidth = stroke.width * 1.25f, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - r * 0.7f), Offset(center.x, center.y + r * 0.7f), strokeWidth = stroke.width * 1.25f, cap = StrokeCap.Round)
        }
        ColorblindPattern.RING -> {
            drawCircle(color = color, radius = r * 0.72f, center = center, style = stroke)
        }
        ColorblindPattern.HEXAGON -> {
            scratchPath.rewind()
            for (i in 0 until 6) {
                val angle = i * PI / 3.0
                val px = center.x + (cos(angle) * r * 0.8f).toFloat()
                val py = center.y + (sin(angle) * r * 0.8f).toFloat()
                if (i == 0) scratchPath.moveTo(px, py) else scratchPath.lineTo(px, py)
            }
            scratchPath.close()
            drawPath(scratchPath, color = color, style = stroke)
        }
        ColorblindPattern.CHEVRON -> {
            scratchPath.rewind()
            scratchPath.moveTo(center.x - r * 0.7f, center.y + r * 0.3f)
            scratchPath.lineTo(center.x, center.y - r * 0.45f)
            scratchPath.lineTo(center.x + r * 0.7f, center.y + r * 0.3f)
            drawPath(scratchPath, color = color, style = stroke)
        }
        ColorblindPattern.GRID -> {
            val d = r * 0.38f
            drawLine(color, Offset(center.x - r * 0.7f, center.y - d), Offset(center.x + r * 0.7f, center.y - d), strokeWidth = stroke.width * 0.85f)
            drawLine(color, Offset(center.x - r * 0.7f, center.y + d), Offset(center.x + r * 0.7f, center.y + d), strokeWidth = stroke.width * 0.85f)
            drawLine(color, Offset(center.x - d, center.y - r * 0.7f), Offset(center.x - d, center.y + r * 0.7f), strokeWidth = stroke.width * 0.85f)
            drawLine(color, Offset(center.x + d, center.y - r * 0.7f), Offset(center.x + d, center.y + r * 0.7f), strokeWidth = stroke.width * 0.85f)
        }
    }
}

private fun DrawScope.drawCheckGlyph(scratchPath: Path, center: Offset, radius: Float, color: Color) {
    scratchPath.rewind()
    scratchPath.moveTo(center.x - radius * 0.7f, center.y)
    scratchPath.lineTo(center.x - radius * 0.15f, center.y + radius * 0.55f)
    scratchPath.lineTo(center.x + radius * 0.75f, center.y - radius * 0.5f)
    drawPath(
        path = scratchPath,
        color = color,
        style = Stroke(width = (radius * 0.38f).coerceAtLeast(2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
