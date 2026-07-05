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
import com.dohex.hyperrose.profile.TransportSpec
import com.dohex.hyperrose.service.StandaloneClient
import com.dohex.hyperrose.service.StandaloneGattClient
import com.dohex.hyperrose.service.StandaloneRfcommClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
)

/**
 * App 侧统一状态与控制入口。
 * - 直接模式：StandaloneGattClient
 * - 桥接模式：接收 Hook 广播 + BluetoothCommandDispatcher 下发控制命令
 */
class DeviceControlStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val directGattClient = StandaloneGattClient(
        appContext, com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile
    )
    private var directRfcommClient: StandaloneRfcommClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _hasBluetoothPermission = MutableStateFlow(false)
    val hasBluetoothPermission: StateFlow<Boolean> = _hasBluetoothPermission.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<RoseDeviceItem>>(emptyList())
    val pairedDevices: StateFlow<List<RoseDeviceItem>> = _pairedDevices.asStateFlow()

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

    private val _capabilities = MutableStateFlow(
        com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.capabilities
    )
    val capabilities: StateFlow<com.dohex.hyperrose.profile.DeviceCapabilities> =
        _capabilities.asStateFlow()

    private var receiverRegistered = false

    private val bridgeReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            when (intent.action) {
                HyperRoseAction.DEVICE_CONNECTED -> {
                    val device = intent.getParcelableExtra(
                        HyperRoseAction.EXTRA_DEVICE,
                        android.bluetooth.BluetoothDevice::class.java,
                    )
                    val needsHookBridge =
                        (_transport.value != ConnectionTransport.DIRECT_BLE && _transport.value != ConnectionTransport.DIRECT_RFCOMM) || _connectionState.value != DeviceConnectionState.CONNECTED
                    if (needsHookBridge) {
                        _transport.value = ConnectionTransport.HOOK_BRIDGE
                        _connectionState.value = DeviceConnectionState.CONNECTED
                        _deviceName.value = device?.name ?: _deviceName.value
                                ?: com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.displayName
                        val profileId = intent.getStringExtra(HyperRoseAction.EXTRA_PROFILE_ID)
                        if (profileId != null) {
                            _capabilities.value =
                                com.dohex.hyperrose.profile.DeviceProfileRegistry.findById(profileId)?.capabilities
                                    ?: com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.capabilities
                        }
                    }
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
        observeDirectGatt()
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
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        if (!_hasBluetoothPermission.value) {
            _pairedDevices.value = emptyList()
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            _pairedDevices.value = emptyList()
            return
        }

        _pairedDevices.value = adapter.bondedDevices.mapNotNull { device ->
            val name = device.name ?: device.alias ?: return@mapNotNull null
            RoseDeviceItem(name = name, address = device.address)
        }.sortedWith(
            compareByDescending<RoseDeviceItem> {
                com.dohex.hyperrose.profile.DeviceProfileRegistry.findByName(it.name) != null
            }.thenBy { it.name.lowercase() }.thenBy { it.address },
        )
    }

    @SuppressLint("MissingPermission")
    fun connectDirect(address: String) {
        if (!_hasBluetoothPermission.value) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val bonded = adapter.bondedDevices.firstOrNull { it.address == address } ?: return

        com.dohex.hyperrose.data.AuthorizedDeviceStore.add(appContext, address)

        val profile = com.dohex.hyperrose.profile.DeviceProfileRegistry.findByName(
            bonded.name ?: ""
        )
        _deviceName.value = bonded.name ?: address

        when (profile?.transport) {
            is TransportSpec.Rfcomm -> {
                directRfcommClient?.disconnect()
                val client = StandaloneRfcommClient(appContext, profile)
                directRfcommClient = client
                observeDirectRfcomm(client)
                _capabilities.value = profile.capabilities
                _transport.value = ConnectionTransport.DIRECT_RFCOMM
                _connectionState.value = DeviceConnectionState.CONNECTING
                client.connect(bonded)
            }

            else -> {
                _transport.value = ConnectionTransport.DIRECT_BLE
                _connectionState.value = DeviceConnectionState.CONNECTING
                directGattClient.connect(bonded)
            }
        }
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
        directRfcommClient?.disconnect()
        directGattClient.disconnect()
        BluetoothCommandDispatcher.disconnectGatt(appContext)
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        _transport.value = ConnectionTransport.NONE
        clearState()
    }

    fun setTemporaryConnectionState(
        name: String,
        battery: TwsBatteryState?,
    ) {
        if (_connectionState.value == DeviceConnectionState.CONNECTED) return
        _transport.value = ConnectionTransport.HOOK_BRIDGE
        _connectionState.value = DeviceConnectionState.CONNECTED
        _deviceName.value = name
        if (battery != null) {
            _battery.value = battery
        }
    }

    fun release() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(bridgeReceiver) }
            receiverRegistered = false
        }
        directRfcommClient?.disconnect()
        directRfcommClient = null
        directGattClient.disconnect()
        scope.coroutineContext.cancel()
    }

    private fun observeDirectGatt() {
        directGattClient.connectionState.onEach { state ->
            if (_transport.value != ConnectionTransport.DIRECT_BLE && state == StandaloneGattClient.ConnectionState.CONNECTED) {
                _transport.value = ConnectionTransport.DIRECT_BLE
            }
            when (state) {
                StandaloneGattClient.ConnectionState.DISCONNECTED -> {
                    if (_transport.value == ConnectionTransport.DIRECT_BLE) {
                        _connectionState.value = DeviceConnectionState.DISCONNECTED
                        _transport.value = ConnectionTransport.NONE
                        clearState()
                    }
                }

                StandaloneGattClient.ConnectionState.CONNECTING -> {
                    _connectionState.value = DeviceConnectionState.CONNECTING
                    _transport.value = ConnectionTransport.DIRECT_BLE
                }

                StandaloneGattClient.ConnectionState.CONNECTED -> {
                    _connectionState.value = DeviceConnectionState.CONNECTED
                    _transport.value = ConnectionTransport.DIRECT_BLE
                }
            }
        }.launchIn(scope)

        directGattClient.deviceName.onEach { name ->
            if (!name.isNullOrBlank()) {
                _deviceName.value = name
            }
        }.launchIn(scope)

        directGattClient.battery.onEach {
            _battery.value = it?.withLastKnownCaseBattery(_battery.value)
        }.launchIn(scope)

        directGattClient.ancMode.onEach { if (it != null) _ancMode.value = it }.launchIn(scope)

        directGattClient.ancDepth.onEach { if (it != null) _ancDepth.value = it }.launchIn(scope)

        directGattClient.transLevel.onEach { if (it != null) _transLevel.value = it }
            .launchIn(scope)

        directGattClient.eqMode.onEach { if (it != null) _eqMode.value = it }.launchIn(scope)

        directGattClient.gameMode.onEach { if (it != null) _gameMode.value = it }.launchIn(scope)
    }

    private fun observeDirectRfcomm(client: StandaloneRfcommClient) {
        client.connectionState.onEach { state ->
            when (state) {
                StandaloneRfcommClient.ConnectionState.DISCONNECTED -> {
                    if (_transport.value == ConnectionTransport.DIRECT_RFCOMM) {
                        _connectionState.value = DeviceConnectionState.DISCONNECTED
                        _transport.value = ConnectionTransport.NONE
                        clearState()
                    }
                }

                StandaloneRfcommClient.ConnectionState.CONNECTING -> {
                    _connectionState.value = DeviceConnectionState.CONNECTING
                }

                StandaloneRfcommClient.ConnectionState.CONNECTED -> {
                    _connectionState.value = DeviceConnectionState.CONNECTED
                }
            }
        }.launchIn(scope)

        client.deviceName.onEach { name ->
            if (!name.isNullOrBlank()) _deviceName.value = name
        }.launchIn(scope)

        client.battery.onEach {
            _battery.value = it?.withLastKnownCaseBattery(_battery.value)
        }.launchIn(scope)

        client.ancMode.onEach { if (it != null) _ancMode.value = it }.launchIn(scope)
        client.ancDepth.onEach { if (it != null) _ancDepth.value = it }.launchIn(scope)
        client.transLevel.onEach { if (it != null) _transLevel.value = it }.launchIn(scope)
        client.eqMode.onEach { if (it != null) _eqMode.value = it }.launchIn(scope)
        client.gameMode.onEach { if (it != null) _gameMode.value = it }.launchIn(scope)
        client.lowLatency.onEach { if (it != null) _lowLatency.value = it }.launchIn(scope)
    }

    private fun registerBridgeReceiver() {
        if (receiverRegistered) return
        val filter =
            IntentFilter().apply { HyperRoseAction.BRIDGE_STATE_ACTIONS.forEach(::addAction) }
        appContext.registerReceiver(bridgeReceiver, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun activeDirectClient(): StandaloneClient? = when (_transport.value) {
        ConnectionTransport.DIRECT_BLE -> directGattClient
        ConnectionTransport.DIRECT_RFCOMM -> directRfcommClient
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
        _deviceName.value = null
        _battery.value = null
        _ancMode.value = null
        _ancDepth.value = null
        _transLevel.value = null
        _eqMode.value = null
        _gameMode.value = false
        _lowLatency.value = false
        _capabilities.value =
            com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.capabilities
    }
}
