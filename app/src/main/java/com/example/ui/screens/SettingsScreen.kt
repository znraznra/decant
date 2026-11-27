package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.prefs.UserPreferences
import com.example.domain.DiscoveredPeer
import com.example.domain.ProgressTransferPayload
import com.example.domain.StudioThemeId
import com.example.domain.TransferTransport
import com.example.domain.TransferUiState
import com.example.ui.components.TransferProgressSheet
import com.example.ui.components.drawColorblindPattern
import com.example.ui.theme.StudioThemeSpec
import com.example.ui.theme.StudioThemes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    theme: StudioThemeSpec,
    preferences: UserPreferences,
    completedCount: Int,
    transferState: TransferUiState = TransferUiState.Idle,
    discoveredPeers: List<DiscoveredPeer> = emptyList(),
    fileBackupFeedback: String? = null,
    pendingBackupPreview: ProgressTransferPayload? = null,
    onExportBackup: ((Uri) -> Unit) -> Unit = {},
    onImportBackupUri: (Uri) -> Unit = {},
    onConfirmReplaceBackup: () -> Unit = {},
    onDismissBackupPreview: () -> Unit = {},
    onStartPeerReceive: (TransferTransport) -> Unit = {},
    onStartPeerDiscover: (TransferTransport) -> Unit = {},
    onSendToPeer: (DiscoveredPeer) -> Unit = {},
    onStopPeerTransfer: () -> Unit = {},
    onResetTransferState: () -> Unit = {},
    onSetSound: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetColorblind: (Boolean) -> Unit,
    onSetRandomizeTheme: (Boolean) -> Unit,
    onSetTimer: (Boolean) -> Unit = {},
    onSelectPinnedTheme: (StudioThemeId) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showTransferSheet by remember { mutableStateOf(false) }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportBackupUri(uri)
        }
    }

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
                            text = "100% Offline & Privacy-First",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Zero tracking • Zero Play Games • Zero gameplay permissions",
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
                        text = "Decant! stores your progress on-device with zero analytics, accounts, or servers. Playing never asks for any permissions; nearby Bluetooth or Wi-Fi permissions are only requested if you open Direct Transfer below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Progress Transfer & Backup Card
            SettingsCard(theme = theme) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_swap_horiz),
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Transfer Progress to Another Device",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Recommended ways to move levels & daily streaks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = theme.accentSoft.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecommendationBullet(
                            title = "1. OS Setup Auto Backup (Active)",
                            detail = "Included automatically when copying apps during Android phone setup.",
                            theme = theme
                        )
                        RecommendationBullet(
                            title = "2. JSON Backup & Import (No Extra Permissions)",
                            detail = "Save or share a decant-backup.json file via Share Sheet, then import it anytime.",
                            theme = theme
                        )
                        RecommendationBullet(
                            title = "3. Direct Bluetooth or Local Wi-Fi",
                            detail = "Transfer directly between two nearby phones running Decant!.",
                            theme = theme
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onExportBackup { uri ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Decant! JSON Progress Backup")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Backup Decant! Progress")
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("backup_button")
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup", color = theme.textPrimary)
                    }

                    OutlinedButton(
                        onClick = { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("import_backup_button")
                    ) {
                        Text("Import Backup", color = theme.accent)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onResetTransferState()
                        showTransferSheet = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent,
                        contentColor = theme.onAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("open_direct_transfer_button")
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_swap_horiz),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Direct Bluetooth / Wi-Fi Transfer")
                }

                fileBackupFeedback?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.accent,
                        modifier = Modifier.testTag("backup_feedback_text")
                    )
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

            // Audio, Haptics & Timer Card
            SettingsCard(theme = theme) {
                ToggleRow(
                    painter = painterResource(R.drawable.ic_volume_up),
                    title = "Pouring, Mixing & Victory Audio",
                    subtitle = "Tactile sound clips for pouring, color blending & clears",
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
                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(
                    painter = painterResource(R.drawable.ic_timer),
                    title = "Subtle Level Timer",
                    subtitle = "Track solve time and best speed per difficulty level",
                    checked = preferences.timerEnabled,
                    onCheckedChange = onSetTimer,
                    theme = theme,
                    testTag = "timer_switch"
                )
            }

            // About & Developer Attribution Card
            SettingsCard(theme = theme) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_developer_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sparkles),
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Decant! • v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "Project ${BuildConfig.PROJECT_NAME} • ${BuildConfig.APPLICATION_ID}",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textSecondary
                        )
                    }
                    Surface(
                        color = theme.accentSoft,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "by ${BuildConfig.DEVELOPER_NAME}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = theme.accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Open-source offline puzzle under the MIT License • Designed for low-RAM speed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showTransferSheet) {
        TransferProgressSheet(
            theme = theme,
            transferState = transferState,
            discoveredPeers = discoveredPeers,
            onStartReceive = onStartPeerReceive,
            onStartDiscover = onStartPeerDiscover,
            onSendToPeer = onSendToPeer,
            onStopTransfer = onStopPeerTransfer,
            onDismiss = {
                onStopPeerTransfer()
                showTransferSheet = false
            }
        )
    }

    pendingBackupPreview?.let { preview ->
        val dailyCount = maxOf(preview.dailyChallenges.count { it.isCompleted }, preview.completedDailyMoves.size)
        AlertDialog(
            onDismissRequest = onDismissBackupPreview,
            containerColor = theme.cardBackground,
            title = {
                Text(
                    text = "Replace Local Database?",
                    color = theme.textPrimary
                )
            },
            text = {
                Text(
                    text = "Importing this JSON backup will replace your current local Room database with:\n\n" +
                        "• Highest unlocked level: ${preview.highestUnlockedLevel}\n" +
                        "• Completed levels: ${preview.completedLevels.size}\n" +
                        "• Completed dailies: $dailyCount\n" +
                        if (preview.activeSession != null) {
                            "• Saved active board: Lv. ${preview.activeSession.currentLevel}"
                        } else {
                            "• Saved active board: None"
                        },
                    color = theme.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmReplaceBackup,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent,
                        contentColor = theme.onAccent
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_import_backup_button")
                ) {
                    Text("Replace & Import")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissBackupPreview,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("cancel_import_backup_button")
                ) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun RecommendationBullet(
    title: String,
    detail: String,
    theme: StudioThemeSpec
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = theme.textPrimary
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textSecondary
        )
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
