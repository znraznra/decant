package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.DiscoveredPeer
import com.example.domain.TransferTransport
import com.example.domain.TransferUiState
import com.example.ui.theme.StudioThemeSpec

private enum class PendingTransferAction {
    RECEIVE,
    DISCOVER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferProgressSheet(
    theme: StudioThemeSpec,
    transferState: TransferUiState,
    discoveredPeers: List<DiscoveredPeer>,
    onStartReceive: (TransferTransport) -> Unit,
    onStartDiscover: (TransferTransport) -> Unit,
    onSendToPeer: (DiscoveredPeer) -> Unit,
    onStopTransfer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTransport by remember { mutableStateOf(TransferTransport.WIFI_LOCAL) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<PendingTransferAction?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val action = pendingAction
        pendingAction = null
        val allGranted = grants.values.all { it }
        if (allGranted && action != null) {
            permissionNotice = null
            when (action) {
                PendingTransferAction.RECEIVE -> onStartReceive(selectedTransport)
                PendingTransferAction.DISCOVER -> onStartDiscover(selectedTransport)
            }
        } else {
            permissionNotice = "Permission declined. You can still transfer anytime without permissions using Share Sheet Backup."
        }
    }

    fun triggerWithPermissions(action: PendingTransferAction) {
        val required = requiredPermissionsFor(selectedTransport, action)
        val missing = required.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            permissionNotice = null
            when (action) {
                PendingTransferAction.RECEIVE -> onStartReceive(selectedTransport)
                PendingTransferAction.DISCOVER -> onStartDiscover(selectedTransport)
            }
        } else {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onStopTransfer()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = theme.cardBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Direct Nearby Transfer",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = theme.textPrimary
            )
            Text(
                text = "Send or receive progress directly between two phones over Local Wi-Fi or Bluetooth—no cloud servers needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TransportChoiceChip(
                    label = "Local Wi-Fi",
                    iconRes = R.drawable.ic_wifi,
                    selected = selectedTransport == TransferTransport.WIFI_LOCAL,
                    theme = theme,
                    testTag = "transfer_mode_wifi",
                    onClick = {
                        onStopTransfer()
                        selectedTransport = TransferTransport.WIFI_LOCAL
                    },
                    modifier = Modifier.weight(1f)
                )
                TransportChoiceChip(
                    label = "Bluetooth",
                    iconRes = R.drawable.ic_bluetooth,
                    selected = selectedTransport == TransferTransport.BLUETOOTH,
                    theme = theme,
                    testTag = "transfer_mode_bluetooth",
                    onClick = {
                        onStopTransfer()
                        selectedTransport = TransferTransport.BLUETOOTH
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { triggerWithPermissions(PendingTransferAction.RECEIVE) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_receive_button")
                ) {
                    Text("Receive", color = theme.textPrimary)
                }
                Button(
                    onClick = { triggerWithPermissions(PendingTransferAction.DISCOVER) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent,
                        contentColor = theme.onAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_find_peers_button")
                ) {
                    Text("Find Phone")
                }
            }

            permissionNotice?.let { notice ->
                Surface(
                    color = theme.accentSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            TransferStatusCard(
                theme = theme,
                transferState = transferState,
                transport = selectedTransport,
                onStopTransfer = onStopTransfer
            )

            if (transferState is TransferUiState.Discovering || discoveredPeers.isNotEmpty()) {
                Text(
                    text = if (discoveredPeers.isEmpty()) {
                        "Tap 'Receive' on the other phone first..."
                    } else {
                        "Tap a device to send your progress:"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textSecondary
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredPeers, key = { it.id }) { peer ->
                        Surface(
                            color = theme.accentSoft.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, theme.cardBorder, RoundedCornerShape(14.dp))
                                .clickable { onSendToPeer(peer) }
                                .testTag("transfer_peer_${peer.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(
                                            if (peer.transport == TransferTransport.WIFI_LOCAL) R.drawable.ic_wifi else R.drawable.ic_bluetooth
                                        ),
                                        contentDescription = null,
                                        tint = theme.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = peer.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = theme.textPrimary
                                        )
                                        Text(
                                            text = peer.transport.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = theme.textSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = "Send",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = theme.accent
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        onStopTransfer()
                        onDismiss()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("transfer_close_button")
                ) {
                    Text("Close", color = theme.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun TransportChoiceChip(
    label: String,
    iconRes: Int,
    selected: Boolean,
    theme: StudioThemeSpec,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) theme.accentSoft else theme.cardBackground,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) theme.accent else theme.cardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = if (selected) theme.accent else theme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) theme.accent else theme.textPrimary
            )
        }
    }
}

@Composable
private fun TransferStatusCard(
    theme: StudioThemeSpec,
    transferState: TransferUiState,
    transport: TransferTransport,
    onStopTransfer: () -> Unit
) {
    Surface(
        color = theme.accentSoft.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (transferState) {
                is TransferUiState.Idle -> {
                    Text(
                        text = if (transport == TransferTransport.WIFI_LOCAL) {
                            "Connect both phones to the same Wi-Fi, tap Receive on the target phone, then tap Find Phone here."
                        } else {
                            "Turn Bluetooth on for both phones (pairing first is fastest), tap Receive on the target phone, then Find Phone here."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary
                    )
                }

                is TransferUiState.WaitingToReceive -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.accent)
                    Text(
                        text = "Listening as ${transferState.deviceName} (${transferState.transport.label})...",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onStopTransfer) {
                        Text("Stop", color = theme.accent)
                    }
                }

                is TransferUiState.Discovering -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.accent)
                    Text(
                        text = "Scanning nearby over ${transferState.transport.label}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onStopTransfer) {
                        Text("Stop", color = theme.accent)
                    }
                }

                is TransferUiState.Transferring -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.accent)
                    Text(
                        text = transferState.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textPrimary
                    )
                }

                is TransferUiState.Success -> {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(theme.tubeCompletedGlow)
                    )
                    Text(
                        text = transferState.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = theme.textPrimary
                    )
                }

                is TransferUiState.Error -> {
                    Text(
                        text = transferState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun requiredPermissionsFor(
    transport: TransferTransport,
    action: PendingTransferAction
): List<String> = when (transport) {
    TransferTransport.BLUETOOTH -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else if (action == PendingTransferAction.DISCOVER) {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            emptyList()
        }
    }

    TransferTransport.WIFI_LOCAL -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
