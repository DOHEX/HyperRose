@file:SuppressLint("MissingPermission")

package com.dohex.hyperrose.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.withLastKnownCaseBattery
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.DeviceResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 独立 App 用的 BLE GATT 通信管理器。 所有状态通过 StateFlow 暴露给 Compose UI。 */
class StandaloneGattClient(
    private val context: Context,
    val profile: DeviceProfile,
) {
    companion object {
        private const val TAG = "HyperRose.StandaloneGattClient"
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
    }

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
        Log.d(TAG, "→ ${packet.toHexString()}")
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
    fun setAnc(mode: AncMode) = sendCommand(profile.protocol.ancCommand(mode))

    fun setAncDepth(depth: AncDepth) = sendCommand(profile.protocol.ancDepthCommand(depth))

    fun setTransLevel(level: TransparencyLevel) =
        sendCommand(profile.protocol.transLevelCommand(level))

    fun setEq(mode: EqPreset) = sendCommand(profile.protocol.eqCommand(mode))

    fun setGameMode(enabled: Boolean) = sendCommand(profile.protocol.gameModeCommand(enabled))

    fun findLeft() = sendCommand(profile.protocol.findLeftOn)

    fun findRight() = sendCommand(profile.protocol.findRightOn)

    fun stopFind() = sendCommand(profile.protocol.findAllOff)

    // ==================== GATT Callback ====================

    @Suppress("DEPRECATION")
    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
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

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Service discovery failed: $status")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return
                }

                val service = gatt.getService(profile.gattSpec.serviceUuid)
                if (service == null) {
                    Log.e(TAG, "Service not found")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return
                }

                writeChar = service.getCharacteristic(profile.gattSpec.writeCharUuid)
                val notifyChar = service.getCharacteristic(profile.gattSpec.notifyCharUuid)

                if (writeChar == null || notifyChar == null) {
                    Log.e(TAG, "Characteristics not found")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return
                }

                // 启用通知
                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(profile.gattSpec.cccdUuid)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }

                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "GATT ready")

                // 查询全部状态
                handler.postDelayed(
                    { queryAllStatus() },
                    profile.timing.initialStatusQueryDelayMs
                )
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                val data = characteristic.value ?: return
                handleResponse(data)
            }
        }

    // ==================== 回包处理 ====================

    private fun handleResponse(data: ByteArray) {
        when (val result = profile.protocol.parseResponse(data)) {
            is DeviceResponse.Battery -> {
                Log.d(TAG, "← ${data.toHexString()} → $result")
                _battery.value = result.info.withLastKnownCaseBattery(_battery.value)
            }

            is DeviceResponse.Anc -> {
                Log.d(TAG, "← ${data.toHexString()} → $result")
                _ancMode.value = result.mode
            }

            is DeviceResponse.AncDepthChanged -> {
                Log.d(TAG, "← ${data.toHexString()} → $result")
                _ancDepth.value = result.depth
            }

            is DeviceResponse.TransparencyChanged -> {
                Log.d(TAG, "← ${data.toHexString()} → $result")
                _transLevel.value = result.level
            }

            is DeviceResponse.Eq -> {
                Log.d(TAG, "← ${data.toHexString()} → $result")
                _eqMode.value = result.mode
            }

            is DeviceResponse.GameMode -> {
                Log.d(TAG, "← ${data.toHexString()} → $result")
                _gameMode.value = result.enabled
            }

            is DeviceResponse.Unknown -> {
                Log.d(TAG, "← ${data.toHexString()} → Unknown")
            }
        }
    }

    private fun queryAllStatus() {
        profile.protocol.statusQuerySequence.forEachIndexed { index, query ->
            handler.postDelayed(
                { sendCommand(query) },
                profile.timing.statusQueryStepDelayMs * index,
            )
        }
        // 启动电量轮询
        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    sendCommand(profile.protocol.queryBattery)

                    handler.postDelayed(this, profile.timing.batteryPollIntervalMs)
                }
            },
            profile.timing.batteryPollIntervalMs,
        )
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
