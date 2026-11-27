package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.prefs.UserPreferences
import com.example.domain.StudioThemeId
import com.example.domain.SyncStatus
import com.example.ui.components.drawColorblindPattern
import com.example.ui.theme.StudioThemeSpec
import com.example.ui.theme.StudioThemes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    theme: StudioThemeSpec,
    preferences: UserPreferences,
    completedCount: Int,
    syncStatus: SyncStatus = SyncStatus.Disabled,
    onSetPlayGamesSync: (Boolean) -> Unit = {},
    onManualSync: () -> Unit = {},
    onSetSound: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetColorblind: (Boolean) -> Unit,
    onSetRandomizeTheme: (Boolean) -> Unit,
    onSelectPinnedTheme: (StudioThemeId) -> Unit,
    onBack: () -> Unit
) {
    val bgBrush = remember(theme.id) {
        Brush.verticalGradient(colors = listOf(theme.backgroundTop, theme.backgroundBottom))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to puzzle",
                        tint = theme.textPrimary
                    )
                }
                Column(modifier = Modifier.padding(start = 6.dp)) {
                    Text(
                        text = "Studio Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = theme.textPrimary
                    )
                    Text(
                        text = "Ad-free • Privacy-first • $completedCount levels completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textSecondary
                    )
                }
            }

            // Privacy-First Guarantee Card
            SettingsCard(theme = theme) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shield_lock),
                        contentDescription = null,
                        tint = theme.tubeCompletedGlow,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy-First Game",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Zero tracking • Zero telemetry • No permissions asked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = theme.accentSoft.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Decant! never collects analytics or personal data and requests zero runtime permissions (no photos, contacts, SMS, or location). Your only online connection is saving or loading level progress with Google Play Games.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Google Play Games Cross-Device Save/Load Card
            SettingsCard(theme = theme) {
                ToggleRow(
                    painter = painterResource(R.drawable.ic_cloud_done),
                    title = "Google Play Games Save & Load",
                    subtitle = "Transfer level & daily progress across devices",
                    checked = preferences.playGamesSyncEnabled,
                    onCheckedChange = onSetPlayGamesSync,
                    theme = theme,
                    testTag = "play_games_sync_switch"
                )

                Spacer(modifier = Modifier.height(8.dp))

                val statusLabel = when {
                    !preferences.playGamesSyncEnabled -> "Local device save only"
                    syncStatus is SyncStatus.NeedsAppId -> "Play Games ID not configured in app strings"
                    syncStatus is SyncStatus.Syncing -> "Syncing progress with Play Games..."
                    syncStatus is SyncStatus.Synced -> "Progress backed up to Google Play Games"
                    syncStatus is SyncStatus.SignedOut -> "Sign in with Google Play Games to transfer"
                    syncStatus is SyncStatus.Error -> syncStatus.message
                    else -> "Ready to sync"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.textSecondary,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    OutlinedButton(
                        onClick = onManualSync,
                        enabled = preferences.playGamesSyncEnabled && syncStatus != SyncStatus.Syncing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        Text(
                            text = if (syncStatus == SyncStatus.Syncing) "Syncing..." else "Save / Load",
                            color = if (preferences.playGamesSyncEnabled) theme.accent else theme.textSecondary
                        )
                    }
                }
            }

            // Colorblind & Accessibility Card
            SettingsCard(theme = theme) {
                ToggleRow(
                    painter = painterResource(R.drawable.ic_visibility),
                    title = "Colorblind Symbols & Patterns",
                    subtitle = "Draws unique geometric glyphs inside every liquid layer",
                    checked = preferences.colorblindEnabled,
                    onCheckedChange = onSetColorblind,
                    theme = theme,
                    testTag = "colorblind_switch"
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    theme.liquids.forEach { spec ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(spec.topColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (preferences.colorblindEnabled) {
                                Canvas(modifier = Modifier.size(20.dp)) {
                                    drawColorblindPattern(
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        boxSize = size.minDimension * 0.85f,
                                        pattern = spec.pattern,
                                        color = spec.symbolColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Randomized Studio Themes Card
            SettingsCard(theme = theme) {
                ToggleRow(
                    painter = painterResource(R.drawable.ic_palette),
                    title = "Randomize Theme Per Level",
                    subtitle = "Varies glass style & studio mood as you progress",
                    checked = preferences.randomizeThemePerLevel,
                    onCheckedChange = onSetRandomizeTheme,
                    theme = theme,
                    testTag = "randomize_theme_switch"
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (preferences.randomizeThemePerLevel) "Or tap a theme to pin it:" else "Pinned studio theme:",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioThemeId.entries.forEach { id ->
                        val preview = StudioThemes.get(id)
                        val selected = !preferences.randomizeThemePerLevel && preferences.pinnedTheme == id
                        OutlinedButton(
                            onClick = { onSelectPinnedTheme(id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) theme.accentSoft else theme.cardBackground
                            ),
                            modifier = Modifier.testTag("theme_option_${id.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(preview.accent)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = id.displayName,
                                color = theme.textPrimary
                            )
                        }
                    }
                }
            }

            // Audio & Haptics Card
            SettingsCard(theme = theme) {
                ToggleRow(
                    painter = painterResource(R.drawable.ic_volume_up),
                    title = "Pouring Sound Effects",
                    subtitle = "Procedural glass chimes & water pour audio",
                    checked = preferences.soundEnabled,
                    onCheckedChange = onSetSound,
                    theme = theme,
                    testTag = "sound_switch"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(
                    painter = painterResource(R.drawable.ic_vibration),
                    title = "Haptic Feedback",
                    subtitle = "Tactile ticks on pours, selection & victories",
                    checked = preferences.hapticsEnabled,
                    onCheckedChange = onSetHaptics,
                    theme = theme,
                    testTag = "haptics_switch"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    theme: StudioThemeSpec,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(theme.cardBackground)
            .border(1.dp, theme.cardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    painter: Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    theme: StudioThemeSpec,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(painter = painter, contentDescription = null, tint = theme.accent, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
