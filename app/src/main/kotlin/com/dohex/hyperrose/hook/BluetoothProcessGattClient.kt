package com.dohex.hyperrose.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dohex.hyperrose.hook.HyperRoseModuleEntry.Companion.TAG
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.withLastKnownCaseBattery
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.DeviceResponse
import io.github.libxposed.api.XposedModule
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction

/**
 * Hook 进程中的 BLE GATT 通信管理器。 在 com.android.bluetooth 进程内运行，负责与目标耳机的 GATT 通信。
 * 通过广播将状态变化发送给其他进程（MiBluetooth、App）。
 */
@SuppressLint("MissingPermission")
class BluetoothProcessGattClient(
    private val context: Context,
    private val module: XposedModule,
    val profile: DeviceProfile,
) {
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    val connectedAddress: String? get() = connectedDevice?.address
    val connectedName: String? get() = connectedDevice?.name
    private val handler = Handler(Looper.getMainLooper())

    // 当前状态缓存
    var currentBattery: TwsBatteryState? = null
        private set

    var currentAnc: AncMode? = null
        private set

    var currentAncDepth: AncDepth? = null
        private set

    var currentTransLevel: TransparencyLevel? = null
        private set

    var currentEq: EqPreset? = null
        private set

    var currentGameMode: Boolean? = null
        private set

    fun connect(device: BluetoothDevice) {
        connectedDevice = device
        module.log(Log.INFO, TAG, "BluetoothProcessGattClient: connecting to ${device.address}")
        registerRefreshReceiver()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private var refreshReceiverRegistered = false

    /** 监听 ACTION_REFRESH_STATUS，收到后查询耳机状态并广播（和 OppoPods 的 RfcommController 一样） */
    private fun registerRefreshReceiver() {
        if (refreshReceiverRegistered) return
        refreshReceiverRegistered = true
        val filter = IntentFilter().apply {
            addAction(HyperRoseAction.REFRESH_STATUS)
        }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == HyperRoseAction.REFRESH_STATUS) {
                        module.log(
                            Log.INFO,
                            TAG,
                            "ACTION_REFRESH_STATUS received, querying all status"
                        )
                        queryAllStatus()
                    }
                }
            },
            filter,
            Context.RECEIVER_EXPORTED,
        )
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

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    module.log(
                        Log.INFO,
                        TAG,
                        "BluetoothProcessGattClient: GATT connected, discovering services",
                    )
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    module.log(Log.INFO, TAG, "BluetoothProcessGattClient: GATT disconnected")
                    handler.removeCallbacksAndMessages(null)
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    module.log(
                        Log.ERROR,
                        TAG,
                        "BluetoothProcessGattClient: service discovery failed: $status",
                    )
                    return
                }

                val service = gatt.getService(profile.gattSpec.serviceUuid)
                if (service == null) {
                    module.log(
                        Log.ERROR,
                        TAG,
                        "BluetoothProcessGattClient: service ${profile.gattSpec.serviceUuid} not found",
                    )
                    return
                }

                // 获取 Write 特征
                writeChar = service.getCharacteristic(profile.gattSpec.writeCharUuid)
                if (writeChar == null) {
                    module.log(
                        Log.ERROR,
                        TAG,
                        "BluetoothProcessGattClient: write characteristic ${profile.gattSpec.writeCharUuid} not found",
                    )
                    return
                }

                // 获取 Notify 特征并启用通知
                val notifyChar = service.getCharacteristic(profile.gattSpec.notifyCharUuid)
                if (notifyChar == null) {
                    module.log(
                        Log.ERROR,
                        TAG,
                        "BluetoothProcessGattClient: notify characteristic ${profile.gattSpec.notifyCharUuid} not found",
                    )
                    return
                }

                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(profile.gattSpec.cccdUuid)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }

                module.log(
                    Log.INFO,
                    TAG,
                    "BluetoothProcessGattClient: GATT ready, querying initial status",
                )

                // 优先发一次电量查询，尽快触发首次超级岛
                sendCommand(profile.protocol.queryBattery)

                // 延迟查询全部状态
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
                val battery = result.info.withLastKnownCaseBattery(currentBattery)
                currentBattery = battery

                broadcastState(HyperRoseAction.BATTERY_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, battery.left?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, battery.right?.level ?: -1)
                    putExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, battery.left?.isCharging ?: false)
                    putExtra(
                        HyperRoseAction.EXTRA_RIGHT_CHARGING,
                        battery.right?.isCharging ?: false
                    )
                    putExtra(HyperRoseAction.EXTRA_CASE_LEVEL, battery.caseBattery ?: -1)
                    putExtra(HyperRoseAction.EXTRA_DEVICE, connectedDevice)
                }

                // 岛触发交给宿主进程（com.xiaomi.bluetooth）发送，提升模板命中率
                context.sendBroadcast(
                    Intent(HyperRoseAction.SHOW_ISLAND).apply {
                        setPackage(HyperRoseAction.PACKAGE_MI_BLUETOOTH)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        putExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, battery.left?.level ?: -1)
                        putExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, battery.right?.level ?: -1)
                        putExtra(
                            HyperRoseAction.EXTRA_LEFT_CHARGING,
                            battery.left?.isCharging ?: false
                        )
                        putExtra(
                            HyperRoseAction.EXTRA_RIGHT_CHARGING,
                            battery.right?.isCharging ?: false
                        )
                        putExtra(HyperRoseAction.EXTRA_CASE_LEVEL, battery.caseBattery ?: -1)
                        putExtra(HyperRoseAction.EXTRA_DEVICE, connectedDevice)
                    },
                )
            }

            is DeviceResponse.Anc -> {
                currentAnc = result.mode
                broadcastState(HyperRoseAction.ANC_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_MODE, result.mode.name)
                }
            }

            is DeviceResponse.AncDepthChanged -> {
                currentAncDepth = result.depth
                broadcastState(HyperRoseAction.ANC_DEPTH_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_DEPTH, result.depth.name)
                }
            }

            is DeviceResponse.TransparencyChanged -> {
                currentTransLevel = result.level
                broadcastState(HyperRoseAction.TRANS_LEVEL_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_LEVEL, result.level.name)
                }
            }

            is DeviceResponse.Eq -> {
                currentEq = result.mode
                broadcastState(HyperRoseAction.EQ_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_MODE, result.mode.name)
                }
            }

            is DeviceResponse.GameMode -> {
                currentGameMode = result.enabled
                broadcastState(HyperRoseAction.GAME_MODE_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_ENABLED, result.enabled)
                }
            }

            is DeviceResponse.Unknown -> {
                module.log(
                    Log.DEBUG,
                    TAG,
                    "BluetoothProcessGattClient: unknown response: ${data.toHexString()}",
                )
            }
        }
    }

    // ==================== 状态查询 ====================

    /** 串行查询全部状态 */
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

    // ==================== 广播工具 ====================

    private fun broadcastState(
        action: String,
        extras: Intent.() -> Unit,
    ) {
        // 广播给 App、MiLink 和蓝牙进程（binder hook 接收器在蓝牙进程）
        listOf(
            HyperRoseAction.PACKAGE_APP,
            HyperRoseAction.PACKAGE_MILINK,
            HyperRoseAction.PACKAGE_BLUETOOTH
        ).forEach { pkg ->
            context.sendBroadcast(
                Intent(action).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    extras()
                },
            )
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}
