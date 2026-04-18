@file:SuppressLint("MissingPermission")

package com.dohex.hyperrose.bluetooth.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel
import com.dohex.hyperrose.domain.battery.TwsBatteryState
import com.dohex.hyperrose.bluetooth.hook.HookProcessGattClient
import com.dohex.hyperrose.bluetooth.protocol.RoseCommandSet as RosePackets
import com.dohex.hyperrose.bluetooth.protocol.RoseResponse
import com.dohex.hyperrose.bluetooth.protocol.RoseResponseParser as RoseParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 独立 App 用的 BLE GATT 通信管理器。
 * 所有状态通过 StateFlow 暴露给 Compose UI。
 */
class StandaloneGattClient(private val context: Context) {

    companion object {
        private const val TAG = "HyperRose.AppGatt"
        private const val BATTERY_POLL_INTERVAL_MS = 30_000L
        private const val QUERY_DELAY_MS = 300L
    }

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    // 状态 Flow
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

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

    private val _gameMode = MutableStateFlow<Boolean?>(null)
    val gameMode: StateFlow<Boolean?> = _gameMode.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())

    // ==================== 公开方法 ====================

    fun connect(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.CONNECTING
        _deviceName.value = device.name
        Log.i(TAG, "Connecting to ${device.address}")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _battery.value = null
        _ancMode.value = null
        _ancDepth.value = null
        _transLevel.value = null
        _eqMode.value = null
        _gameMode.value = null
        _deviceName.value = null
    }

    fun sendCommand(packet: ByteArray) {
        val char = writeChar ?: return
        val g = gatt ?: return
        @Suppress("DEPRECATION")
        char.value = packet
        @Suppress("DEPRECATION")
        g.writeCharacteristic(char)
    }

    fun refreshStatus() {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        queryAllStatus()
    }

    // 便捷方法
    fun setAnc(mode: AncMode) = sendCommand(RosePackets.ancCommand(mode))
    fun setAncDepth(depth: AncDepth) = sendCommand(RosePackets.ancDepthCommand(depth))
    fun setTransLevel(level: TransparencyLevel) = sendCommand(RosePackets.transLevelCommand(level))
    fun setEq(mode: EqPreset) = sendCommand(RosePackets.eqCommand(mode))
    fun setGameMode(enabled: Boolean) = sendCommand(RosePackets.gameModeCommand(enabled))
    fun findLeft() = sendCommand(RosePackets.FIND_LEFT_ON)
    fun findRight() = sendCommand(RosePackets.FIND_RIGHT_ON)
    fun stopFind() = sendCommand(RosePackets.FIND_ALL_OFF)

    // ==================== GATT Callback ====================

    @Suppress("DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT connected, discovering services")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    handler.removeCallbacksAndMessages(null)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            val service = gatt.getService(HookProcessGattClient.SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Service not found")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            writeChar = service.getCharacteristic(HookProcessGattClient.WRITE_UUID)
            val notifyChar = service.getCharacteristic(HookProcessGattClient.NOTIFY_UUID)

            if (writeChar == null || notifyChar == null) {
                Log.e(TAG, "Characteristics not found")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            // 启用通知
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(HookProcessGattClient.CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            _connectionState.value = ConnectionState.CONNECTED
            Log.i(TAG, "GATT ready")

            // 查询全部状态
            handler.postDelayed({ queryAllStatus() }, 500)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            handleResponse(data)
        }
    }

    // ==================== 回包处理 ====================

    private fun handleResponse(data: ByteArray) {
        when (val result = RoseParser.parse(data)) {
            is RoseResponse.Battery -> _battery.value = result.info
            is RoseResponse.Anc -> _ancMode.value = result.mode
            is RoseResponse.AncDepthChanged -> _ancDepth.value = result.depth
            is RoseResponse.TransparencyChanged -> _transLevel.value = result.level
            is RoseResponse.Eq -> _eqMode.value = result.mode
            is RoseResponse.GameMode -> _gameMode.value = result.enabled
            is RoseResponse.Unknown -> Log.d(TAG, "Unknown: ${data.joinToString(" ") { "%02X".format(it) }}")
        }
    }

    private fun queryAllStatus() {
        val queries = listOf(
            RosePackets.QUERY_BATTERY,
            RosePackets.QUERY_ANC,
            RosePackets.QUERY_ANC_DEPTH,
            RosePackets.QUERY_TRANS_LEVEL,
            RosePackets.QUERY_EQ,
            RosePackets.QUERY_GAME_MODE
        )
        queries.forEachIndexed { index, query ->
            handler.postDelayed({ sendCommand(query) }, QUERY_DELAY_MS * index)
        }
        // 启动电量轮询
        handler.postDelayed(batteryPollRunnable, BATTERY_POLL_INTERVAL_MS)
    }

    private val batteryPollRunnable = object : Runnable {
        override fun run() {
            sendCommand(RosePackets.QUERY_BATTERY)
            handler.postDelayed(this, BATTERY_POLL_INTERVAL_MS)
        }
    }
}
