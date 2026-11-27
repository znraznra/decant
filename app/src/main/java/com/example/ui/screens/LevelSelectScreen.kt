package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LevelProgressEntity
import com.example.domain.DifficultyTier
import com.example.domain.PuzzleEngine
import com.example.domain.formatElapsedDuration
import com.example.ui.theme.StudioThemeSpec

@Composable
fun LevelSelectScreen(
    theme: StudioThemeSpec,
    currentLevel: Int,
    highestUnlockedLevel: Int,
    completedProgress: List<LevelProgressEntity>,
    tierBestTimes: Map<DifficultyTier, Long> = emptyMap(),
    onSelectLevel: (Int) -> Unit,
    onBack: () -> Unit
) {
    val completedMap = remember(completedProgress) {
        completedProgress.filter { it.isCompleted }.associateBy { it.levelNumber }
    }
    var selectedTierFilter by remember { mutableStateOf<DifficultyTier?>(null) }

    // Show all unlocked levels + 12 upcoming preview levels so infinite horizon feels inviting
    val totalVisibleLevels = remember(highestUnlockedLevel) {
        maxOf(highestUnlockedLevel + 12, 30)
    }

    val displayedLevels = remember(totalVisibleLevels, selectedTierFilter) {
        (1..totalVisibleLevels).filter { level ->
            selectedTierFilter == null || PuzzleEngine.difficultyForLevel(level).first == selectedTierFilter
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(theme.backgroundTop, theme.backgroundBottom)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("levels_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to puzzle",
                        tint = theme.textPrimary
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(
                        text = "Infinite Levels",
                        style = MaterialTheme.typography.headlineSmall,
                        color = theme.textPrimary
                    )
                    Text(
                        text = "${completedMap.size} solved • Unlocked up to Level $highestUnlockedLevel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textSecondary
                    )
                }
            }

            // Tier filter strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTierFilter == null,
                    onClick = { selectedTierFilter = null },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.accent,
                        selectedLabelColor = theme.onAccent
                    )
                )
                DifficultyTier.entries.forEach { tier ->
                    val tierBest = tierBestTimes[tier]
                    val chipText = if (tierBest != null) {
                        "${tier.label} • ${formatElapsedDuration(tierBest)}"
                    } else {
                        tier.label
                    }
                    FilterChip(
                        selected = selectedTierFilter == tier,
                        onClick = {
                            selectedTierFilter = if (selectedTierFilter == tier) null else tier
                        },
                        label = { Text(chipText) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.accent,
                            selectedLabelColor = theme.onAccent
                        )
                    )
                }
            }

            // Adaptive level grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 88.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedLevels, key = { it }) { level ->
                    val progress = completedMap[level]
                    val isUnlocked = level <= highestUnlockedLevel
                    val isCurrent = level == currentLevel
                    val (tier, colorCount) = PuzzleEngine.difficultyForLevel(level)

                    LevelGridCard(
                        level = level,
                        tierLabel = tier.label,
                        colorCount = colorCount,
                        bestMoves = progress?.bestMoves,
                        isCompleted = progress != null,
                        isUnlocked = isUnlocked,
                        isCurrent = isCurrent,
                        theme = theme,
                        onClick = {
                            if (isUnlocked) onSelectLevel(level)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelGridCard(
    level: Int,
    tierLabel: String,
    colorCount: Int,
    bestMoves: Int?,
    isCompleted: Boolean,
    isUnlocked: Boolean,
    isCurrent: Boolean,
    theme: StudioThemeSpec,
    onClick: () -> Unit
) {
    val bgColor = when {
        isCurrent -> theme.accent
        isCompleted -> theme.cardBackground
        isUnlocked -> theme.accentSoft
        else -> theme.cardBackground.copy(alpha = 0.45f)
    }
    val borderColor = when {
        isCurrent -> theme.accent
        isCompleted -> theme.tubeCompletedGlow.copy(alpha = 0.7f)
        isUnlocked -> theme.cardBorder
        else -> theme.cardBorder.copy(alpha = 0.4f)
    }
    val titleColor = if (isCurrent) theme.onAccent else if (isUnlocked) theme.textPrimary else theme.textSecondary.copy(alpha = 0.6f)
    val subColor = if (isCurrent) theme.onAccent.copy(alpha = 0.85f) else theme.textSecondary

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .minimumInteractiveComponentSize()
            .testTag("level_card_$level")
            .clickable(enabled = isUnlocked, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$level",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = titleColor
            )
            when {
                isCompleted -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = if (isCurrent) theme.onAccent else theme.tubeCompletedGlow,
                    modifier = Modifier.size(16.dp)
                )
                isCurrent -> Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Current level",
                    tint = theme.onAccent,
                    modifier = Modifier.size(16.dp)
                )
                !isUnlocked -> Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = "Locked",
                    tint = subColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Text(
            text = if (bestMoves != null) "$bestMoves moves" else "$colorCount colors",
            style = MaterialTheme.typography.labelSmall,
            color = subColor,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isCurrent) theme.onAccent.copy(alpha = 0.16f) else theme.accentSoft.copy(alpha = 0.7f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = tierLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) theme.onAccent else theme.textSecondary
            )
        }
    }
}
