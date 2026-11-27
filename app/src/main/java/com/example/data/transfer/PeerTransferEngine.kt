package com.example.data.transfer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.IntentCompat
import com.example.domain.DiscoveredPeer
import com.example.domain.ImportSummary
import com.example.domain.ProgressTransferPayload
import com.example.domain.TransferTransport
import com.example.domain.TransferUiState
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lightweight zero-dependency peer transfer over Local Wi-Fi (NSD + TCP socket)
 * and Bluetooth Classic (RFCOMM). Runs lazily only while the user opens Direct Transfer.
 */
class PeerTransferEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _transferState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val transferState: StateFlow<TransferUiState> = _transferState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private var activeJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var btServerSocket: BluetoothServerSocket? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var btReceiver: BroadcastReceiver? = null

    fun resetState() {
        stopAll()
        _transferState.value = TransferUiState.Idle
    }

    fun startReceiving(
        transport: TransferTransport,
        onReceived: suspend (ProgressTransferPayload) -> ImportSummary
    ) {
        stopAll()
        when (transport) {
            TransferTransport.WIFI_LOCAL -> startWifiReceiver(onReceived)
            TransferTransport.BLUETOOTH -> startBluetoothReceiver(onReceived)
        }
    }

    fun startDiscovering(transport: TransferTransport) {
        stopAll()
        _discoveredPeers.value = emptyList()
        when (transport) {
            TransferTransport.WIFI_LOCAL -> startWifiDiscovery()
            TransferTransport.BLUETOOTH -> startBluetoothDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun sendToPeer(
        peer: DiscoveredPeer,
        payloadProvider: suspend () -> ProgressTransferPayload
    ) {
        stopAll()
        activeJob = scope.launch {
            _transferState.value = TransferUiState.Transferring("Sending to ${peer.name}...")
            try {
                val payload = payloadProvider()
                val encoded = ProgressTransferCodec.encodeToJsonString(payload)
                    .toByteArray(Charsets.UTF_8)
                require(encoded.size <= ProgressTransferCodec.MAX_BACKUP_BYTES) {
                    "Backup exceeds maximum transfer size."
                }

                when (peer.transport) {
                    TransferTransport.WIFI_LOCAL -> sendOverWifi(peer, encoded)
                    TransferTransport.BLUETOOTH -> sendOverBluetooth(peer, encoded)
                }

                _transferState.value = TransferUiState.Success(
                    "Sent progress to ${peer.name} (Lv. ${payload.highestUnlockedLevel})."
                )
            } catch (sec: SecurityException) {
                _transferState.value = TransferUiState.Error(
                    "Permission required for ${peer.transport.label} transfer."
                )
            } catch (e: Exception) {
                _transferState.value = TransferUiState.Error(
                    e.localizedMessage ?: "Transfer failed. Make sure the other device is waiting to receive."
                )
            }
        }
    }

    fun stopAll() {
        activeJob?.cancel()
        activeJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { btServerSocket?.close() }
        btServerSocket = null

        val nsd = nsdManager
        registrationListener?.let { listener ->
            runCatching { nsd?.unregisterService(listener) }
        }
        registrationListener = null
        registeredServiceName = null

        discoveryListener?.let { listener ->
            runCatching { nsd?.stopServiceDiscovery(listener) }
        }
        discoveryListener = null

        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null

        btReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
        }
        btReceiver = null

        @SuppressLint("MissingPermission")
        runCatching {
            val adapter = getBluetoothAdapter()
            if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
        }
    }

    private fun startWifiReceiver(
        onReceived: suspend (ProgressTransferPayload) -> ImportSummary
    ) {
        activeJob = scope.launch {
            try {
                acquireMulticastLock()
                val socketServer = ServerSocket(0).apply { soTimeout = ACCEPT_TIMEOUT_MS }
                serverSocket = socketServer
                val localPort = socketServer.localPort
                val label = "Decant-${sanitizedDeviceName()}"
                registeredServiceName = label

                val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                    ?: error("Wi-Fi service discovery is unavailable on this device.")
                nsdManager = nsd

                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = label
                    serviceType = NSD_SERVICE_TYPE
                    port = localPort
                }
                val regListener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(info: NsdServiceInfo) {
                        registeredServiceName = info.serviceName
                    }

                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        _transferState.value = TransferUiState.Error(
                            "Could not advertise on Wi-Fi (code $errorCode)."
                        )
                    }

                    override fun onServiceUnregistered(arg0: NsdServiceInfo) = Unit
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                }
                registrationListener = regListener
                nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener)

                _transferState.value = TransferUiState.WaitingToReceive(
                    transport = TransferTransport.WIFI_LOCAL,
                    deviceName = label
                )

                val client = socketServer.accept()
                client.use { conn ->
                    conn.soTimeout = IO_TIMEOUT_MS
                    _transferState.value = TransferUiState.Transferring("Receiving backup over Wi-Fi...")
                    val summary = receiveFramedPayload(
                        DataInputStream(conn.getInputStream()),
                        DataOutputStream(conn.getOutputStream()),
                        onReceived
                    )
                    _transferState.value = TransferUiState.Success(formatImportSuccess(summary))
                }
            } catch (sec: SecurityException) {
                _transferState.value = TransferUiState.Error("Nearby Wi-Fi permission is required.")
            } catch (e: Exception) {
                if (activeJob?.isCancelled != true) {
                    _transferState.value = TransferUiState.Error(
                        e.localizedMessage ?: "Timed out waiting for Wi-Fi sender."
                    )
                }
            } finally {
                stopAll()
            }
        }
    }

    private fun startWifiDiscovery() {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            _transferState.value = TransferUiState.Error("Wi-Fi discovery is unavailable on this device.")
            return
        }
        nsdManager = nsd
        acquireMulticastLock()
        _transferState.value = TransferUiState.Discovering(TransferTransport.WIFI_LOCAL)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_decant")) return
                if (serviceInfo.serviceName == registeredServiceName) return
                resolveNsdService(nsd, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _discoveredPeers.update { list ->
                    list.filterNot { it.id == "wifi:${serviceInfo.serviceName}" }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _transferState.value = TransferUiState.Error(
                    "Wi-Fi scan failed (code $errorCode). Ensure Wi-Fi is connected."
                )
                stopAll()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        runCatching {
            nsd.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { err ->
            _transferState.value = TransferUiState.Error(
                err.localizedMessage ?: "Could not start Wi-Fi discovery."
            )
            stopAll()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNsdService(nsd: NsdManager, serviceInfo: NsdServiceInfo) {
        nsd.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val hostAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        resolved.hostAddresses.firstOrNull()?.hostAddress
                    } else {
                        resolved.host?.hostAddress
                    } ?: return
                    val peer = DiscoveredPeer(
                        id = "wifi:${resolved.serviceName}",
                        name = resolved.serviceName,
                        transport = TransferTransport.WIFI_LOCAL,
                        host = hostAddress,
                        port = resolved.port
                    )
                    _discoveredPeers.update { current ->
                        (current.filterNot { it.id == peer.id } + peer)
                    }
                }
            }
        )
    }

    private fun sendOverWifi(peer: DiscoveredPeer, encoded: ByteArray) {
        val host = peer.host ?: error("Peer Wi-Fi address unavailable.")
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, peer.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = IO_TIMEOUT_MS
            sendFramedPayload(
                DataOutputStream(socket.getOutputStream()),
                DataInputStream(socket.getInputStream()),
                encoded
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothReceiver(
        onReceived: suspend (ProgressTransferPayload) -> ImportSummary
    ) {
        val adapter = getBluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            _transferState.value = TransferUiState.Error(
                "Turn on Bluetooth first, or use Share Sheet / Local Wi-Fi."
            )
            return
        }

        activeJob = scope.launch {
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord("DecantTransfer", BT_SERVICE_UUID)
                btServerSocket = server
                val deviceName = runCatching { adapter.name }.getOrNull().orEmpty().ifBlank {
                    sanitizedDeviceName()
                }
                _transferState.value = TransferUiState.WaitingToReceive(
                    transport = TransferTransport.BLUETOOTH,
                    deviceName = deviceName
                )

                val client: BluetoothSocket = server.accept(ACCEPT_TIMEOUT_MS)
                client.use { conn ->
                    _transferState.value = TransferUiState.Transferring("Receiving backup via Bluetooth...")
                    val summary = receiveFramedPayload(
                        DataInputStream(conn.inputStream),
                        DataOutputStream(conn.outputStream),
                        onReceived
                    )
                    _transferState.value = TransferUiState.Success(formatImportSuccess(summary))
                }
            } catch (sec: SecurityException) {
                _transferState.value = TransferUiState.Error("Bluetooth permission is required.")
            } catch (e: Exception) {
                if (activeJob?.isCancelled != true) {
                    _transferState.value = TransferUiState.Error(
                        e.localizedMessage ?: "Timed out waiting for Bluetooth sender."
                    )
                }
            } finally {
                stopAll()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        val adapter = getBluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            _transferState.value = TransferUiState.Error(
                "Turn on Bluetooth first, or use Share Sheet / Local Wi-Fi."
            )
            return
        }

        try {
            val bonded = adapter.bondedDevices.orEmpty().mapNotNull { device ->
                device.toDiscoveredPeerOrNull()
            }
            _discoveredPeers.value = bonded
            _transferState.value = TransferUiState.Discovering(TransferTransport.BLUETOOTH)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = IntentCompat.getParcelableExtra(
                                intent,
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            ) ?: return
                            val peer = device.toDiscoveredPeerOrNull() ?: return
                            _discoveredPeers.update { current ->
                                if (current.any { it.id == peer.id }) current else current + peer
                            }
                        }
                    }
                }
            }
            btReceiver = receiver
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (sec: SecurityException) {
            _transferState.value = TransferUiState.Error("Bluetooth scan & connect permissions are required.")
        } catch (e: Exception) {
            _transferState.value = TransferUiState.Error(
                e.localizedMessage ?: "Could not scan Bluetooth devices."
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendOverBluetooth(peer: DiscoveredPeer, encoded: ByteArray) {
        val adapter = getBluetoothAdapter() ?: error("Bluetooth unavailable.")
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        val address = peer.bluetoothAddress ?: error("Bluetooth address missing.")
        val device = adapter.getRemoteDevice(address)
        val socket = device.createRfcommSocketToServiceRecord(BT_SERVICE_UUID)
        socket.use { conn ->
            conn.connect()
            sendFramedPayload(
                DataOutputStream(conn.outputStream),
                DataInputStream(conn.inputStream),
                encoded
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toDiscoveredPeerOrNull(): DiscoveredPeer? {
        val addr = runCatching { address }.getOrNull() ?: return null
        val displayName = runCatching { name }.getOrNull().orEmpty().ifBlank { addr }
        return DiscoveredPeer(
            id = "bt:$addr",
            name = displayName,
            transport = TransferTransport.BLUETOOTH,
            bluetoothAddress = addr
        )
    }

    private suspend fun receiveFramedPayload(
        input: DataInputStream,
        output: DataOutputStream,
        onReceived: suspend (ProgressTransferPayload) -> ImportSummary
    ): ImportSummary {
        val length = input.readInt()
        if (length <= 0 || length > ProgressTransferCodec.MAX_BACKUP_BYTES) {
            throw IOException("Invalid backup packet size ($length bytes).")
        }
        val buffer = ByteArray(length)
        input.readFully(buffer)
        val raw = String(buffer, Charsets.UTF_8)
        val payload = ProgressTransferCodec.decodeFromJsonString(raw).getOrThrow()
        val summary = onReceived(payload)
        output.writeByte(ACK_OK.toInt())
        output.flush()
        return summary
    }

    private fun sendFramedPayload(
        output: DataOutputStream,
        input: DataInputStream,
        encoded: ByteArray
    ) {
        output.writeInt(encoded.size)
        output.write(encoded)
        output.flush()
        val ack = input.readByte()
        if (ack != ACK_OK) {
            throw IOException("Receiver did not acknowledge transfer.")
        }
    }

    private fun formatImportSuccess(summary: ImportSummary): String =
        "Imported! +${summary.newLevelsAdded} levels, +${summary.newDailyAdded} dailies (Unlocked Lv. ${summary.highestUnlockedLevel})."

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi != null && multicastLock == null) {
                multicastLock = wifi.createMulticastLock("DecantNsdLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
    }

    private fun sanitizedDeviceName(): String {
        val raw = Build.MODEL.orEmpty().replace(Regex("[^A-Za-z0-9]"), "")
        return raw.take(12).ifEmpty { "Phone" }
    }

    companion object {
        private const val NSD_SERVICE_TYPE = "_decant._tcp."
        private val BT_SERVICE_UUID: UUID = UUID.fromString("8a4d2c10-6b7e-4b6c-9f2a-3d1e5f7c9b04")
        private const val ACCEPT_TIMEOUT_MS = 90_000
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val IO_TIMEOUT_MS = 10_000
        private const val ACK_OK: Byte = 0x01
    }
}
