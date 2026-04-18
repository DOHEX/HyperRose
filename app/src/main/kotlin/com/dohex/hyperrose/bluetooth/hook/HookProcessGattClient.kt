package com.dohex.hyperrose.bluetooth.hook

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dohex.hyperrose.entry.HyperRoseXposedEntry.Companion.TAG
import com.dohex.hyperrose.bluetooth.protocol.RoseCommandSet as RosePackets
import com.dohex.hyperrose.bluetooth.protocol.RoseResponse
import com.dohex.hyperrose.bluetooth.protocol.RoseResponseParser as RoseParser
import com.dohex.hyperrose.model.*
import io.github.libxposed.api.XposedModule
import java.util.UUID

/**
 * Hook 进程中的 BLE GATT 通信管理器。
 * 在 com.android.bluetooth 进程内运行，负责与 ROSE EARFREE i5 的 GATT 通信。
 * 通过广播将状态变化发送给其他进程（MiBluetooth、App）。
 */
@SuppressLint("MissingPermission")
class HookProcessGattClient(
    private val context: Context,
    private val module: XposedModule
) {

    companion object {
        // BLE GATT UUIDs（从 nRF Connect 推导，如连接失败需替换为完整 128-bit UUID）
        val SERVICE_UUID: UUID = UUID.fromString("011bf5da-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("00007777-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("00008888-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val BATTERY_POLL_INTERVAL_MS = 30_000L
        private const val QUERY_DELAY_MS = 300L
    }

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    private val handler = Handler(Looper.getMainLooper())

    // 当前状态缓存
    var currentBattery: TwsBatteryInfo? = null; private set
    var currentAnc: AncMode? = null; private set
    var currentAncDepth: AncDepth? = null; private set
    var currentTransLevel: TransLevel? = null; private set
    var currentEq: EqMode? = null; private set
    var currentGameMode: Boolean? = null; private set

    fun connect(device: BluetoothDevice) {
        connectedDevice = device
        module.log(Log.INFO, TAG, "GattManager: Connecting to ${device.address}")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        connectedDevice = null
        currentBattery = null
        currentAnc = null
        currentAncDepth = null
        currentTransLevel = null
        currentEq = null
        currentGameMode = null
    }

    fun refreshStatus() {
        if (gatt == null || writeChar == null) return
        queryAllStatus()
    }

    /** 发送命令到耳机 */
    fun sendCommand(packet: ByteArray) {
        val char = writeChar ?: return
        val g = gatt ?: return
        char.value = packet
        g.writeCharacteristic(char)
    }

    // ==================== GATT Callback ====================

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                module.log(Log.INFO, TAG, "GattManager: GATT connected, discovering services")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                module.log(Log.INFO, TAG, "GattManager: GATT disconnected")
                handler.removeCallbacksAndMessages(null)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                module.log(Log.ERROR, TAG, "GattManager: Service discovery failed: $status")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                module.log(Log.ERROR, TAG, "GattManager: Service $SERVICE_UUID not found")
                return
            }

            // 获取 Write 特征
            writeChar = service.getCharacteristic(WRITE_UUID)
            if (writeChar == null) {
                module.log(Log.ERROR, TAG, "GattManager: Write characteristic $WRITE_UUID not found")
                return
            }

            // 获取 Notify 特征并启用通知
            val notifyChar = service.getCharacteristic(NOTIFY_UUID)
            if (notifyChar == null) {
                module.log(Log.ERROR, TAG, "GattManager: Notify characteristic $NOTIFY_UUID not found")
                return
            }

            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            module.log(Log.INFO, TAG, "GattManager: GATT ready, querying initial status")

            // 延迟查询全部状态
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
            is RoseResponse.Battery -> {
                currentBattery = result.info

                broadcastState(HyperRoseAction.BATTERY_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, result.info.left?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, result.info.right?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, result.info.left?.isCharging ?: false)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, result.info.right?.isCharging ?: false)
                    putExtra(HyperRoseAction.EXTRA_CASE_LEVEL, result.info.caseBattery ?: -1)
                    putExtra(HyperRoseAction.EXTRA_DEVICE, connectedDevice)
                }

                // 岛触发交给宿主进程（com.xiaomi.bluetooth）发送，提升模板命中率
                context.sendBroadcast(Intent(HyperRoseAction.SHOW_ISLAND).apply {
                    setPackage("com.xiaomi.bluetooth")
                    putExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, result.info.left?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, result.info.right?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, result.info.left?.isCharging ?: false)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, result.info.right?.isCharging ?: false)
                    putExtra(HyperRoseAction.EXTRA_CASE_LEVEL, result.info.caseBattery ?: -1)
                    putExtra(HyperRoseAction.EXTRA_DEVICE, connectedDevice)
                })
            }
            is RoseResponse.Anc -> {
                currentAnc = result.mode
                broadcastState(HyperRoseAction.ANC_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_MODE, result.mode.name)
                }
            }
            is RoseResponse.AncDepthChanged -> {
                currentAncDepth = result.depth
                broadcastState(HyperRoseAction.ANC_DEPTH_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_DEPTH, result.depth.name)
                }
            }
            is RoseResponse.TransparencyChanged -> {
                currentTransLevel = result.level
                broadcastState(HyperRoseAction.TRANS_LEVEL_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_LEVEL, result.level.name)
                }
            }
            is RoseResponse.Eq -> {
                currentEq = result.mode
                broadcastState(HyperRoseAction.EQ_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_MODE, result.mode.name)
                }
            }
            is RoseResponse.GameMode -> {
                currentGameMode = result.enabled
                broadcastState(HyperRoseAction.GAME_MODE_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_ENABLED, result.enabled)
                }
            }
            is RoseResponse.Unknown -> {
                module.log(Log.DEBUG, TAG, "GattManager: Unknown response: ${data.toHexString()}")
            }
        }
    }

    // ==================== 状态查询 ====================

    /** 串行查询全部状态 */
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
        scheduleBatteryPoll()
    }

    private fun scheduleBatteryPoll() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                sendCommand(RosePackets.QUERY_BATTERY)
                handler.postDelayed(this, BATTERY_POLL_INTERVAL_MS)
            }
        }, BATTERY_POLL_INTERVAL_MS)
    }

    // ==================== 广播工具 ====================

    private fun broadcastState(action: String, extras: Intent.() -> Unit) {
        context.sendBroadcast(Intent(action).apply {
            setPackage("com.dohex.hyperrose")
            extras()
        })
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it) }
}
