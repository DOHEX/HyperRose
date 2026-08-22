package com.dohex.hyperrose.ui.state

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.dohex.hyperrose.ipc.BluetoothCommandDispatcher
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.dohex.hyperrose.model.withLastKnownCaseBattery
import com.dohex.hyperrose.profile.DeviceCapabilities
import com.dohex.hyperrose.profile.DeviceCatalog
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.TransportSpec
import com.dohex.hyperrose.service.StandaloneClient
import com.dohex.hyperrose.service.StandaloneConnectionState
import com.dohex.hyperrose.service.StandaloneGattClient
import com.dohex.hyperrose.service.StandaloneRfcommClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction

enum class DeviceConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED,
}

enum class ConnectionTransport {
    NONE, DIRECT_BLE, DIRECT_RFCOMM, HOOK_BRIDGE,
}

data class RoseDeviceItem(
    val name: String,
    val address: String,
    val profileId: String? = null,
) {
    val isSupported: Boolean get() = profileId != null
}

/**
 * App 侧统一状态与控制入口。
 * - 直接模式：StandaloneGattClient
 * - 桥接模式：接收 Hook 广播 + BluetoothCommandDispatcher 下发控制命令
 */
class DeviceControlStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var directClient: StandaloneClient? = null
    private var directClientObserverJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var bridgeFallbackJob: Job? = null

    companion object {
        private const val BRIDGE_TIMEOUT_MS = 5_000L
    }

    private val _hasBluetoothPermission = MutableStateFlow(false)
    val hasBluetoothPermission: StateFlow<Boolean> = _hasBluetoothPermission.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<RoseDeviceItem>>(emptyList())
    val pairedDevices: StateFlow<List<RoseDeviceItem>> = _pairedDevices.asStateFlow()

    private val _activeAddress = MutableStateFlow<String?>(null)
    val activeAddress: StateFlow<String?> = _activeAddress.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _connectionState = MutableStateFlow(DeviceConnectionState.DISCONNECTED)
    val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

    private val _transport = MutableStateFlow(ConnectionTransport.NONE)
    val transport: StateFlow<ConnectionTransport> = _transport.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _battery = MutableStateFlow<TwsBatteryState?>(null)
    val battery: StateFlow<TwsBatteryState?> = _battery.asStateFlow()

    private val _ancMode = MutableStateFlow<AncMode?>(null)
    val ancMode: StateFlow<AncMode?> = _ancMode.asStateFlow()

    private val _ancDepth = MutableStateFlow<AncDepth?>(null)
    val ancDepth: StateFlow<AncDepth?> = _ancDepth.asStateFlow()

    private val _transLevel = MutableStateFlow<TransparencyLevel?>(null)
    val transLevel: StateFlow<TransparencyLevel?> = _transLevel.asStateFlow()

    private val _eqMode = MutableStateFlow<EqPreset?>(null)
    val eqMode: StateFlow<EqPreset?> = _eqMode.asStateFlow()

    private val _gameMode = MutableStateFlow(false)
    val gameMode: StateFlow<Boolean> = _gameMode.asStateFlow()

    private val _lowLatency = MutableStateFlow(false)
    val lowLatency: StateFlow<Boolean> = _lowLatency.asStateFlow()

    private val _profile = MutableStateFlow<DeviceProfile?>(null)
    val profile: StateFlow<DeviceProfile?> = _profile.asStateFlow()

    private val _capabilities = MutableStateFlow(DeviceCapabilities.NONE)
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    private var receiverRegistered = false

    private val bridgeReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    _bluetoothEnabled.value =
                        intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR,
                        ) == BluetoothAdapter.STATE_ON
                }

                HyperRoseAction.DEVICE_CONNECTED -> {
                    bridgeFallbackJob?.cancel()

                    // Bridge always takes priority over standalone.
                    // Disconnect any active direct client so we don't keep
                    // two connections to the same device.
                    clearDirectClient()

                    _transport.value = ConnectionTransport.HOOK_BRIDGE
                    _connectionState.value = DeviceConnectionState.CONNECTED

                    val device = intent.getParcelableExtra(
                        HyperRoseAction.EXTRA_DEVICE,
                        android.bluetooth.BluetoothDevice::class.java,
                    )
                    val profileId = intent.getStringExtra(HyperRoseAction.EXTRA_PROFILE_ID)
                    val resolvedProfile = profileId?.let { DeviceCatalog.findById(it)?.profile }
                        ?: device?.name?.let { DeviceCatalog.findByName(it)?.profile }
                    _activeAddress.value = device?.address ?: _activeAddress.value
                    _profile.value = resolvedProfile
                    _capabilities.value = resolvedProfile?.capabilities ?: DeviceCapabilities.NONE
                    _deviceName.value = device?.name ?: resolvedProfile?.displayName ?: _deviceName.value
                }

                HyperRoseAction.DEVICE_DISCONNECTED -> {
                    if (_transport.value == ConnectionTransport.HOOK_BRIDGE) {
                        _connectionState.value = DeviceConnectionState.DISCONNECTED
                        _transport.value = ConnectionTransport.NONE
                        clearState()
                    }
                }

                HyperRoseAction.BATTERY_CHANGED -> {
                    parseBattery(intent)?.let {
                        _battery.value = it.withLastKnownCaseBattery(_battery.value)
                    }
                }

                HyperRoseAction.ANC_CHANGED -> {
                    intent.enumExtra<AncMode>(HyperRoseAction.EXTRA_MODE)
                        ?.let { _ancMode.value = it }
                }

                HyperRoseAction.ANC_DEPTH_CHANGED -> {
                    intent.enumExtra<AncDepth>(HyperRoseAction.EXTRA_DEPTH)
                        ?.let { _ancDepth.value = it }
                }

                HyperRoseAction.TRANS_LEVEL_CHANGED -> {
                    intent.enumExtra<TransparencyLevel>(HyperRoseAction.EXTRA_LEVEL)?.let {
                        _transLevel.value = it
                    }
                }

                HyperRoseAction.EQ_CHANGED -> {
                    intent.enumExtra<EqPreset>(HyperRoseAction.EXTRA_MODE)
                        ?.let { _eqMode.value = it }
                }

                HyperRoseAction.GAME_MODE_CHANGED -> {
                    if (intent.hasExtra(HyperRoseAction.EXTRA_ENABLED)) {
                        _gameMode.value =
                            intent.getBooleanExtra(HyperRoseAction.EXTRA_ENABLED, false)
                    }
                }

                HyperRoseAction.LOW_LATENCY_CHANGED -> {
                    if (intent.hasExtra(HyperRoseAction.EXTRA_ENABLED)) {
                        _lowLatency.value =
                            intent.getBooleanExtra(HyperRoseAction.EXTRA_ENABLED, false)
                    }
                }
            }
        }
    }

    init {
        registerBridgeReceiver()
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        val hasConnect = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        val hasScan = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED
        _hasBluetoothPermission.value = hasConnect && hasScan
        _bluetoothEnabled.value = if (hasConnect) {
            runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }
                .getOrDefault(false)
        } else {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        if (!_hasBluetoothPermission.value) {
            _pairedDevices.value = emptyList()
            _bluetoothEnabled.value = false
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            _pairedDevices.value = emptyList()
            _bluetoothEnabled.value = false
            return
        }
        _bluetoothEnabled.value = adapter.isEnabled


        _pairedDevices.value = adapter.bondedDevices.mapNotNull { device ->
            val name = device.name ?: device.alias ?: return@mapNotNull null
            RoseDeviceItem(
                name = name,
                address = device.address,
                profileId = DeviceCatalog.findByName(name)?.id,
            )
        }.sortedWith(
            compareByDescending<RoseDeviceItem> {
                it.isSupported
            }.thenBy { it.name.lowercase() }.thenBy { it.address },
        )
    }

    @SuppressLint("MissingPermission")
    fun connectDirect(address: String) {
        if (!_hasBluetoothPermission.value) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val bonded = adapter.bondedDevices.firstOrNull { it.address == address } ?: return

        val profile = DeviceCatalog.findByName(bonded.name ?: "")?.profile ?: return
        _activeAddress.value = address
        com.dohex.hyperrose.data.RemoteDeviceStore.add(address)

        _deviceName.value = bonded.name ?: address
        _profile.value = profile
        _capabilities.value = profile.capabilities

        // Prefer hook bridge: optimistically assume LSPosed hooks are running.
        // If the device is already A2DP-connected, the hook process broadcasts
        // DEVICE_CONNECTED and BridgeReceiver cancels the fallback.
        // Falls back to standalone client after timeout.
        bridgeFallbackJob?.cancel()
        _transport.value = ConnectionTransport.HOOK_BRIDGE
        _connectionState.value = DeviceConnectionState.CONNECTING

        bridgeFallbackJob = scope.launch {
            delay(BRIDGE_TIMEOUT_MS)
            if (_transport.value == ConnectionTransport.HOOK_BRIDGE &&
                _connectionState.value == DeviceConnectionState.CONNECTING
            ) {
                connectStandalone(bonded, profile)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectStandalone(
        bonded: android.bluetooth.BluetoothDevice,
        profile: DeviceProfile,
    ) {
        clearDirectClient()
        val client = when (profile.transport) {
            is TransportSpec.Rfcomm -> {
                StandaloneRfcommClient(appContext, profile)
            }

            is TransportSpec.Gatt -> StandaloneGattClient(appContext, profile)
        }
        directClient = client
        _transport.value = when (profile.transport) {
            is TransportSpec.Gatt -> ConnectionTransport.DIRECT_BLE
            is TransportSpec.Rfcomm -> ConnectionTransport.DIRECT_RFCOMM
        }
        _connectionState.value = DeviceConnectionState.CONNECTING
        client.connect(bonded)
        observeDirectClient(client)
    }

    fun setAnc(mode: AncMode) {
        _ancMode.value = mode
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.setAnc(appContext, mode)
            else -> client.setAnc(mode)
        }
    }

    fun setAncDepth(depth: AncDepth) {
        _ancDepth.value = depth
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.setAncDepth(appContext, depth)
            else -> client.setAncDepth(depth)
        }
    }

    fun setTransLevel(level: TransparencyLevel) {
        _transLevel.value = level
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.setTransLevel(appContext, level)
            else -> client.setTransLevel(level)
        }
    }

    fun setEq(mode: EqPreset) {
        _eqMode.value = mode
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.setEq(appContext, mode)
            else -> client.setEq(mode)
        }
    }

    fun setGameMode(enabled: Boolean) {
        _gameMode.value = enabled
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.setGameMode(appContext, enabled)
            else -> client.setGameMode(enabled)
        }
    }

    fun setLowLatency(enabled: Boolean) {
        _lowLatency.value = enabled
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.setLowLatency(appContext, enabled)
            else -> client.setLowLatency(enabled)
        }
    }

    fun findLeft() {
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.findLeft(appContext)
            else -> client.findLeft()
        }
    }

    fun findRight() {
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.findRight(appContext)
            else -> client.findRight()
        }
    }

    fun stopFind() {
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.stopFind(appContext)
            else -> client.stopFind()
        }
    }

    /** 发送原始 hex 指令（供调试页使用），根据当前传输模式路由 */
    fun sendRawCommand(hex: String) {
        when (val client = activeDirectClient()) {
            null -> {
                Intent(HyperRoseAction.RAW_SEND).apply {
                    setPackage(HyperRoseAction.PACKAGE_BLUETOOTH)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    putExtra(HyperRoseAction.EXTRA_HEX, hex)
                    appContext.sendBroadcast(this)
                }
            }

            else -> client.sendRawCommand(hex)
        }
    }

    fun refreshStatus() {
        when (val client = activeDirectClient()) {
            null -> BluetoothCommandDispatcher.refreshStatus(appContext)
            else -> client.refreshStatus()
        }
    }

    fun disconnect() {
        bridgeFallbackJob?.cancel()
        clearDirectClient()
        BluetoothCommandDispatcher.disconnectGatt(appContext)
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        _transport.value = ConnectionTransport.NONE
        clearState()
    }

    fun setTemporaryConnectionState(
        profile: DeviceProfile,
        name: String?,
        battery: TwsBatteryState?,
    ) {
        if (_connectionState.value == DeviceConnectionState.CONNECTED) return
        _transport.value = ConnectionTransport.HOOK_BRIDGE
        _connectionState.value = DeviceConnectionState.CONNECTED
        _profile.value = profile
        _capabilities.value = profile.capabilities
        _deviceName.value = name ?: profile.displayName
        if (battery != null) {
            _battery.value = battery
        }
    }

    fun release() {
        bridgeFallbackJob?.cancel()
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(bridgeReceiver) }
            receiverRegistered = false
        }
        clearDirectClient()
        scope.coroutineContext.cancel()
    }

    private fun observeDirectClient(client: StandaloneClient) {
        directClientObserverJob?.cancel()
        directClientObserverJob = scope.launch {
            client.connectionState.onEach { state ->
                if (client !== directClient) return@onEach
                when (state) {
                    StandaloneConnectionState.DISCONNECTED -> {
                        _connectionState.value = DeviceConnectionState.DISCONNECTED
                        _transport.value = ConnectionTransport.NONE
                        clearState()
                    }

                    StandaloneConnectionState.CONNECTING -> {
                        _connectionState.value = DeviceConnectionState.CONNECTING
                    }

                    StandaloneConnectionState.CONNECTED -> {
                        _connectionState.value = DeviceConnectionState.CONNECTED
                    }
                }
            }.launchIn(this)
            client.deviceName.onEach { name ->
                if (client === directClient && !name.isNullOrBlank()) _deviceName.value = name
            }.launchIn(this)
            client.battery.onEach { battery ->
                if (client === directClient) _battery.value = battery?.withLastKnownCaseBattery(_battery.value)
            }.launchIn(this)
            client.ancMode.onEach { value ->
                if (client === directClient && value != null) _ancMode.value = value
            }.launchIn(this)
            client.ancDepth.onEach { value ->
                if (client === directClient && value != null) _ancDepth.value = value
            }.launchIn(this)
            client.transLevel.onEach { value ->
                if (client === directClient && value != null) _transLevel.value = value
            }.launchIn(this)
            client.eqMode.onEach { value ->
                if (client === directClient && value != null) _eqMode.value = value
            }.launchIn(this)
            client.gameMode.onEach { value ->
                if (client === directClient && value != null) _gameMode.value = value
            }.launchIn(this)
            client.lowLatency.onEach { value ->
                if (client === directClient && value != null) _lowLatency.value = value
            }.launchIn(this)
        }
    }

    private fun clearDirectClient() {
        directClientObserverJob?.cancel()
        directClientObserverJob = null
        directClient?.disconnect()
        directClient = null
    }

    private fun registerBridgeReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            HyperRoseAction.BRIDGE_STATE_ACTIONS.forEach(::addAction)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        appContext.registerReceiver(bridgeReceiver, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun activeDirectClient(): StandaloneClient? = when (_transport.value) {
        ConnectionTransport.DIRECT_BLE,
        ConnectionTransport.DIRECT_RFCOMM,
        -> directClient

        else -> null
    }


    private inline fun <reified T : Enum<T>> Intent.enumExtra(key: String): T? =
        getStringExtra(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private fun parseBattery(intent: Intent): TwsBatteryState? {
        val leftLevel =
            intent.getIntExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, -1).asBatteryLevelOrNull()
        val rightLevel =
            intent.getIntExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, -1).asBatteryLevelOrNull()
        val caseLevel =
            intent.getIntExtra(HyperRoseAction.EXTRA_CASE_LEVEL, -1).asBatteryLevelOrNull()

        if (leftLevel == null && rightLevel == null && caseLevel == null) {
            return null
        }

        val left = leftLevel?.let {
            EarBatteryState(
                level = it,
                isCharging = intent.getBooleanExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, false),
            )
        }

        val right = rightLevel?.let {
            EarBatteryState(
                level = it,
                isCharging = intent.getBooleanExtra(
                    HyperRoseAction.EXTRA_RIGHT_CHARGING, false
                ),
            )
        }

        return TwsBatteryState(
            left = left,
            right = right,
            caseBattery = caseLevel,
        )
    }

    private fun clearState() {
        _activeAddress.value = null
        _deviceName.value = null
        _profile.value = null
        _battery.value = null
        _ancMode.value = null
        _ancDepth.value = null
        _transLevel.value = null
        _eqMode.value = null
        _gameMode.value = false
        _lowLatency.value = false
        _capabilities.value = DeviceCapabilities.NONE
    }
}
