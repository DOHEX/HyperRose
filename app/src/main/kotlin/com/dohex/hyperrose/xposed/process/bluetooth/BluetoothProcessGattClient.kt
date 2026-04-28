package com.dohex.hyperrose.xposed.process.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel
import com.dohex.hyperrose.domain.battery.TwsBatteryState
import com.dohex.hyperrose.domain.battery.withLastKnownCaseBattery
import com.dohex.hyperrose.bluetooth.protocol.RoseGattSpec
import com.dohex.hyperrose.bluetooth.protocol.RoseGattTiming
import com.dohex.hyperrose.bluetooth.protocol.RoseGattQueryScheduler
import com.dohex.hyperrose.bluetooth.protocol.RoseCommandSet as RosePackets
import com.dohex.hyperrose.bluetooth.protocol.RoseResponse
import com.dohex.hyperrose.bluetooth.protocol.RoseResponseParser as RoseParser
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction
import com.dohex.hyperrose.xposed.entry.HyperRoseModuleEntry.Companion.TAG
import io.github.libxposed.api.XposedModule

/**
 * Hook 进程中的 BLE GATT 通信管理器。
 * 在 com.android.bluetooth 进程内运行，负责与 ROSE EARFREE i5 的 GATT 通信。
 * 通过广播将状态变化发送给其他进程（MiBluetooth、App）。
 */
@SuppressLint("MissingPermission")
class BluetoothProcessGattClient(
    private val context: Context,
    private val module: XposedModule
) {

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    private val handler = Handler(Looper.getMainLooper())

    // 当前状态缓存
    var currentBattery: TwsBatteryState? = null; private set
    var currentAnc: AncMode? = null; private set
    var currentAncDepth: AncDepth? = null; private set
    var currentTransLevel: TransparencyLevel? = null; private set
    var currentEq: EqPreset? = null; private set
    var currentGameMode: Boolean? = null; private set

    fun connect(device: BluetoothDevice) {
        connectedDevice = device
        module.log(Log.INFO, TAG, "BluetoothProcessGattClient: connecting to ${device.address}")
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
                module.log(Log.INFO, TAG, "BluetoothProcessGattClient: GATT connected, discovering services")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                module.log(Log.INFO, TAG, "BluetoothProcessGattClient: GATT disconnected")
                handler.removeCallbacksAndMessages(null)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                module.log(Log.ERROR, TAG, "BluetoothProcessGattClient: service discovery failed: $status")
                return
            }

            val service = gatt.getService(RoseGattSpec.SERVICE_UUID)
            if (service == null) {
                module.log(Log.ERROR, TAG, "BluetoothProcessGattClient: service ${RoseGattSpec.SERVICE_UUID} not found")
                return
            }

            // 获取 Write 特征
            writeChar = service.getCharacteristic(RoseGattSpec.WRITE_UUID)
            if (writeChar == null) {
                module.log(Log.ERROR, TAG, "BluetoothProcessGattClient: write characteristic ${RoseGattSpec.WRITE_UUID} not found")
                return
            }

            // 获取 Notify 特征并启用通知
            val notifyChar = service.getCharacteristic(RoseGattSpec.NOTIFY_UUID)
            if (notifyChar == null) {
                module.log(Log.ERROR, TAG, "BluetoothProcessGattClient: notify characteristic ${RoseGattSpec.NOTIFY_UUID} not found")
                return
            }

            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(RoseGattSpec.CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            module.log(Log.INFO, TAG, "BluetoothProcessGattClient: GATT ready, querying initial status")

            // 优先发一次电量查询，尽快触发首次超级岛
            sendCommand(RosePackets.QUERY_BATTERY)

            // 延迟查询全部状态
            handler.postDelayed({ queryAllStatus() }, RoseGattTiming.INITIAL_STATUS_QUERY_DELAY_MS)
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
                val battery = result.info.withLastKnownCaseBattery(currentBattery)
                currentBattery = battery

                broadcastState(HyperRoseAction.BATTERY_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, battery.left?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, battery.right?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, battery.left?.isCharging ?: false)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, battery.right?.isCharging ?: false)
                    putExtra(HyperRoseAction.EXTRA_CASE_LEVEL, battery.caseBattery ?: -1)
                    putExtra(HyperRoseAction.EXTRA_DEVICE, connectedDevice)
                }

                // 岛触发交给宿主进程（com.xiaomi.bluetooth）发送，提升模板命中率
                context.sendBroadcast(Intent(HyperRoseAction.SHOW_ISLAND).apply {
                    setPackage(HyperRoseAction.PACKAGE_MI_BLUETOOTH)
                    putExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, battery.left?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, battery.right?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, battery.left?.isCharging ?: false)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, battery.right?.isCharging ?: false)
                    putExtra(HyperRoseAction.EXTRA_CASE_LEVEL, battery.caseBattery ?: -1)
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
                module.log(Log.DEBUG, TAG, "BluetoothProcessGattClient: unknown response: ${data.toHexString()}")
            }
        }
    }

    // ==================== 状态查询 ====================

    /** 串行查询全部状态 */
    private fun queryAllStatus() {
        RoseGattQueryScheduler.scheduleStatusQueries(handler, ::sendCommand)

        // 启动电量轮询
        RoseGattQueryScheduler.scheduleBatteryPolling(handler, ::sendCommand)
    }

    // ==================== 广播工具 ====================

    private fun broadcastState(action: String, extras: Intent.() -> Unit) {
        context.sendBroadcast(Intent(action).apply {
            setPackage(HyperRoseAction.PACKAGE_APP)
            extras()
        })
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it) }
}
