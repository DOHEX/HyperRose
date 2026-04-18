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
import com.dohex.hyperrose.bluetooth.app.StandaloneGattClient
import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel
import com.dohex.hyperrose.domain.battery.EarBatteryState
import com.dohex.hyperrose.domain.battery.TwsBatteryState
import com.dohex.hyperrose.ipc.BluetoothCommandDispatcher
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class ConnectionTransport {
    NONE,
    DIRECT_BLE,
    HOOK_BRIDGE
}

data class RoseDeviceItem(
    val name: String,
    val address: String
)

/**
 * App 侧统一状态与控制入口。
 * - 直接模式：StandaloneGattClient
 * - 桥接模式：接收 Hook 广播 + BluetoothCommandDispatcher 下发控制命令
 */
class DeviceControlStore(private val context: Context) {
    private val appContext = context.applicationContext
    private val appGattManager = StandaloneGattClient(appContext)
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

    private var receiverRegistered = false

    private val bridgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                HyperRoseAction.DEVICE_CONNECTED -> {
                    val device = intent.getParcelableExtra(
                        HyperRoseAction.EXTRA_DEVICE,
                        android.bluetooth.BluetoothDevice::class.java
                    )
                    if (_transport.value != ConnectionTransport.DIRECT_BLE || _connectionState.value != DeviceConnectionState.CONNECTED) {
                        _transport.value = ConnectionTransport.HOOK_BRIDGE
                        _connectionState.value = DeviceConnectionState.CONNECTED
                        _deviceName.value = device?.name ?: _deviceName.value ?: "ROSE EARFREE"
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
                    _battery.value = parseBattery(intent)
                }

                HyperRoseAction.ANC_CHANGED -> {
                    intent.getStringExtra(HyperRoseAction.EXTRA_MODE)
                        ?.let { runCatching { AncMode.valueOf(it) }.getOrNull() }
                        ?.let { _ancMode.value = it }
                }

                HyperRoseAction.ANC_DEPTH_CHANGED -> {
                    intent.getStringExtra(HyperRoseAction.EXTRA_DEPTH)
                        ?.let { runCatching { AncDepth.valueOf(it) }.getOrNull() }
                        ?.let { _ancDepth.value = it }
                }

                HyperRoseAction.TRANS_LEVEL_CHANGED -> {
                    intent.getStringExtra(HyperRoseAction.EXTRA_LEVEL)
                        ?.let { runCatching { TransparencyLevel.valueOf(it) }.getOrNull() }
                        ?.let { _transLevel.value = it }
                }

                HyperRoseAction.EQ_CHANGED -> {
                    intent.getStringExtra(HyperRoseAction.EXTRA_MODE)
                        ?.let { runCatching { EqPreset.valueOf(it) }.getOrNull() }
                        ?.let { _eqMode.value = it }
                }

                HyperRoseAction.GAME_MODE_CHANGED -> {
                    if (intent.hasExtra(HyperRoseAction.EXTRA_ENABLED)) {
                        _gameMode.value = intent.getBooleanExtra(HyperRoseAction.EXTRA_ENABLED, false)
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
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val hasScan = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_SCAN
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

        _pairedDevices.value = adapter.bondedDevices
            .mapNotNull { device ->
                val name = device.name ?: return@mapNotNull null
                if (!name.contains("ROSE EARFREE", ignoreCase = true)) return@mapNotNull null
                RoseDeviceItem(name = name, address = device.address)
            }
            .sortedWith(compareBy<RoseDeviceItem> { it.name.lowercase() }.thenBy { it.address })
    }

    @SuppressLint("MissingPermission")
    fun connectDirect(address: String) {
        if (!_hasBluetoothPermission.value) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val bonded = adapter.bondedDevices.firstOrNull { it.address == address } ?: return

        _transport.value = ConnectionTransport.DIRECT_BLE
        _connectionState.value = DeviceConnectionState.CONNECTING
        _deviceName.value = bonded.name ?: address
        appGattManager.connect(bonded)
    }

    fun setAnc(mode: AncMode) {
        _ancMode.value = mode
        if (isDirectConnected()) {
            appGattManager.setAnc(mode)
        } else {
            BluetoothCommandDispatcher.setAnc(appContext, mode)
        }
    }
    fun setAncDepth(depth: AncDepth) {
        _ancDepth.value = depth
        if (isDirectConnected()) {
            appGattManager.setAncDepth(depth)
        } else {
            BluetoothCommandDispatcher.setAncDepth(appContext, depth)
        }
    }
    fun setTransLevel(level: TransparencyLevel) {
        _transLevel.value = level
        if (isDirectConnected()) {
            appGattManager.setTransLevel(level)
        } else {
            BluetoothCommandDispatcher.setTransLevel(appContext, level)
        }
    }
    fun setEq(mode: EqPreset) {
        _eqMode.value = mode
        if (isDirectConnected()) {
            appGattManager.setEq(mode)
        } else {
            BluetoothCommandDispatcher.setEq(appContext, mode)
        }
    }
    fun setGameMode(enabled: Boolean) {
        _gameMode.value = enabled
        if (isDirectConnected()) {
            appGattManager.setGameMode(enabled)
        } else {
            BluetoothCommandDispatcher.setGameMode(appContext, enabled)
        }
    }
    fun findLeft() {
        if (isDirectConnected()) {
            appGattManager.findLeft()
        } else {
            BluetoothCommandDispatcher.findLeft(appContext)
        }
    }
    fun findRight() {
        if (isDirectConnected()) {
            appGattManager.findRight()
        } else {
            BluetoothCommandDispatcher.findRight(appContext)
        }
    }
    fun stopFind() {
        if (isDirectConnected()) {
            appGattManager.stopFind()
        } else {
            BluetoothCommandDispatcher.stopFind(appContext)
        }
    }
    fun refreshStatus() {
        if (isDirectConnected()) {
            appGattManager.refreshStatus()
        } else {
            BluetoothCommandDispatcher.refreshStatus(appContext)
        }
    }
    fun disconnect() {
        if (isDirectConnected()) {
            appGattManager.disconnect()
        }
        BluetoothCommandDispatcher.disconnectGatt(appContext)
        _connectionState.value = DeviceConnectionState.DISCONNECTED
        _transport.value = ConnectionTransport.NONE
        clearState()
    }

    fun setTemporaryConnectionState(name: String, battery: TwsBatteryState?) {
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
        appGattManager.disconnect()
        scope.coroutineContext.cancel()
    }

    private fun observeDirectGatt() {
        appGattManager.connectionState
            .onEach { state ->
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
            }
            .launchIn(scope)

        appGattManager.deviceName
            .onEach { name ->
                if (!name.isNullOrBlank()) {
                    _deviceName.value = name
                }
            }
            .launchIn(scope)

        appGattManager.battery
            .onEach { _battery.value = it }
            .launchIn(scope)

        appGattManager.ancMode
            .onEach { if (it != null) _ancMode.value = it }
            .launchIn(scope)

        appGattManager.ancDepth
            .onEach { if (it != null) _ancDepth.value = it }
            .launchIn(scope)

        appGattManager.transLevel
            .onEach { if (it != null) _transLevel.value = it }
            .launchIn(scope)

        appGattManager.eqMode
            .onEach { if (it != null) _eqMode.value = it }
            .launchIn(scope)

        appGattManager.gameMode
            .onEach { if (it != null) _gameMode.value = it }
            .launchIn(scope)
    }

    private fun registerBridgeReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(HyperRoseAction.DEVICE_CONNECTED)
            addAction(HyperRoseAction.DEVICE_DISCONNECTED)
            addAction(HyperRoseAction.BATTERY_CHANGED)
            addAction(HyperRoseAction.ANC_CHANGED)
            addAction(HyperRoseAction.ANC_DEPTH_CHANGED)
            addAction(HyperRoseAction.TRANS_LEVEL_CHANGED)
            addAction(HyperRoseAction.EQ_CHANGED)
            addAction(HyperRoseAction.GAME_MODE_CHANGED)
        }
        appContext.registerReceiver(bridgeReceiver, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun isDirectConnected(): Boolean {
        return appGattManager.connectionState.value == StandaloneGattClient.ConnectionState.CONNECTED
    }

    private fun parseBattery(intent: Intent): TwsBatteryState? {
        val leftLevel = intent.getIntExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, -1)
        val rightLevel = intent.getIntExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, -1)
        val caseLevel = intent.getIntExtra(HyperRoseAction.EXTRA_CASE_LEVEL, -1)

        if (leftLevel < 0 && rightLevel < 0 && caseLevel < 0) {
            return null
        }

        val left = if (leftLevel >= 0) {
            EarBatteryState(
                level = leftLevel,
                isCharging = intent.getBooleanExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, false)
            )
        } else {
            null
        }

        val right = if (rightLevel >= 0) {
            EarBatteryState(
                level = rightLevel,
                isCharging = intent.getBooleanExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, false)
            )
        } else {
            null
        }

        return TwsBatteryState(
            left = left,
            right = right,
            caseBattery = caseLevel.takeIf { it >= 0 }
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
    }
}
