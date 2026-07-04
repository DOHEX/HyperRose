package com.dohex.hyperrose.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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
 * 设备通信会话基类 — 广播、状态缓存、回包处理等共享逻辑。
 * 子类实现具体的传输方式（GATT / RFCOMM）。
 */
@SuppressLint("MissingPermission")
abstract class DeviceSession(
    protected val context: Context,
    protected val module: XposedModule,
    val profile: DeviceProfile,
) {
    protected var connectedDevice: BluetoothDevice? = null
    val connectedAddress: String? get() = connectedDevice?.address
    val connectedName: String? get() = connectedDevice?.name
    protected val handler = Handler(Looper.getMainLooper())

    var currentBattery: TwsBatteryState? = null; protected set
    var currentAnc: AncMode? = null; protected set
    var currentAncDepth: AncDepth? = null; protected set
    var currentTransLevel: TransparencyLevel? = null; protected set
    var currentEq: EqPreset? = null; protected set
    var currentGameMode: Boolean? = null; protected set
    var currentLowLatency: Boolean? = null; protected set

    abstract fun connect(device: BluetoothDevice)
    abstract fun disconnect()
    abstract fun sendCommand(packet: ByteArray)

    fun refreshStatus() {
        queryAllStatus()
    }

    protected fun handleResponse(data: ByteArray) {
        val hex = data.toHexString()
        val result = profile.protocol.parseResponse(data)
        module.log(Log.DEBUG, TAG, "← $hex → $result")

        when (result) {
            is DeviceResponse.Battery -> {
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
                context.sendBroadcast(
                    Intent(HyperRoseAction.SHOW_ISLAND).apply {
                        setPackage(HyperRoseAction.PACKAGE_MI_BLUETOOTH)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        putExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, battery.left?.level ?: -1)
                        putExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, battery.right?.level ?: -1)
                        putExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, battery.left?.isCharging ?: false)
                        putExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, battery.right?.isCharging ?: false)
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
            is DeviceResponse.LowLatencyChanged -> {
                currentLowLatency = result.enabled
                broadcastState(HyperRoseAction.LOW_LATENCY_CHANGED) {
                    putExtra(HyperRoseAction.EXTRA_ENABLED, result.enabled)
                }
            }
            is DeviceResponse.Unknown -> {
                module.log(Log.DEBUG, TAG, "DeviceSession: unknown response: $hex")
            }
        }
    }

    protected fun queryAllStatus() {
        profile.protocol.statusQuerySequence.forEachIndexed { index, query ->
            handler.postDelayed(
                { sendCommand(query) },
                (profile.gattTiming?.statusQueryStepDelayMs ?: 100L) * index,
            )
        }
        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    sendCommand(profile.protocol.queryBattery)
                    handler.postDelayed(this, profile.gattTiming?.batteryPollIntervalMs ?: 30_000L)
                }
            },
            profile.gattTiming?.batteryPollIntervalMs ?: 30_000L,
        )
    }

    protected fun broadcastState(action: String, extras: Intent.() -> Unit) {
        listOf(
            HyperRoseAction.PACKAGE_APP,
            HyperRoseAction.PACKAGE_MILINK,
            HyperRoseAction.PACKAGE_BLUETOOTH,
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

    private var refreshReceiverRegistered = false

    protected fun registerRefreshReceiver() {
        if (refreshReceiverRegistered) return
        refreshReceiverRegistered = true
        val filter = IntentFilter().apply { addAction(HyperRoseAction.REFRESH_STATUS) }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == HyperRoseAction.REFRESH_STATUS) queryAllStatus()
                }
            },
            filter,
            Context.RECEIVER_EXPORTED,
        )
    }

    protected fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}
