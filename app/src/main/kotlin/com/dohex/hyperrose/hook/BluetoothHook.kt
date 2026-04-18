package com.dohex.hyperrose.hook

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.dohex.hyperrose.core.reflection.ReflectionHelper
import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel
import com.dohex.hyperrose.entry.HyperRoseXposedEntry.Companion.TAG
import com.dohex.hyperrose.bluetooth.hook.HookProcessGattClient
import com.dohex.hyperrose.bluetooth.protocol.RoseCommandSet as RosePackets
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * com.android.bluetooth 进程的 Hook。
 * 监听 A2DP 连接状态变化，识别 ROSE EARFREE 耳机并启动 GATT 通信。
 */
object BluetoothHook {

    /** 蓝牙名称关键字，用于识别目标耳机 */
    private const val DEVICE_NAME_KEYWORD = "ROSE EARFREE"

    private var gattManager: HookProcessGattClient? = null
    private var commandReceiverRegistered = false

    fun init(module: XposedModule, param: PackageLoadedParam) {
        val cl = param.getDefaultClassLoader()

        // Hook A2dpService.handleConnectionStateChanged(BluetoothDevice, int, int)
        try {
            val a2dpClass = cl.loadClass("com.android.bluetooth.a2dp.A2dpService")
            val method = a2dpClass.getDeclaredMethod(
                "handleConnectionStateChanged",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()

                val device = chain.getArg(0) as? BluetoothDevice
                val fromState = chain.getArg(1) as Int
                val currState = chain.getArg(2) as Int

                if (device != null && currState != fromState && isRoseEarphone(device)) {
                    val serviceObj = chain.thisObject
                    try {
                        when (currState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                module.log(Log.INFO, TAG, "ROSE EARFREE connected: ${device.address}")
                                onDeviceConnected(module, serviceObj, device)
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                module.log(Log.INFO, TAG, "ROSE EARFREE disconnected: ${device.address}")
                                onDeviceDisconnected(module, serviceObj, device)
                            }
                        }
                    } catch (e: Throwable) {
                        module.log(Log.ERROR, TAG, "Error handling connection state change", e)
                    }
                }
                result
            })

            module.log(Log.INFO, TAG, "BluetoothHook: A2dpService hooked successfully")
        } catch (e: Throwable) {
            module.log(Log.ERROR, TAG, "BluetoothHook: Failed to hook A2dpService", e)
        }
    }

    private fun isRoseEarphone(device: BluetoothDevice): Boolean {
        return device.name?.contains(DEVICE_NAME_KEYWORD, ignoreCase = true) == true
    }

    private fun onDeviceConnected(module: XposedModule, serviceObj: Any, device: BluetoothDevice) {
        val context = resolveContext(serviceObj) ?: return

        registerCommandReceiverIfNeeded(module, context)

        // 启动 GATT 通信
        gattManager?.disconnect()
        gattManager = HookProcessGattClient(context, module).also {
            it.connect(device)
        }

        // 广播连接事件（给 App）
        context.sendBroadcast(Intent(HyperRoseAction.DEVICE_CONNECTED).apply {
            putExtra(HyperRoseAction.EXTRA_DEVICE, device)
            setPackage(HyperRoseAction.PACKAGE_APP)
        })

        // 广播连接事件（给宿主蓝牙进程，用于超级岛状态重置）
        context.sendBroadcast(Intent(HyperRoseAction.DEVICE_CONNECTED).apply {
            putExtra(HyperRoseAction.EXTRA_DEVICE, device)
            setPackage(HyperRoseAction.PACKAGE_MI_BLUETOOTH)
        })
    }

    private fun onDeviceDisconnected(module: XposedModule, serviceObj: Any, device: BluetoothDevice) {
        // 断开 GATT
        gattManager?.disconnect()
        gattManager = null

        val context = resolveContext(serviceObj) ?: return

        // 广播断开事件（给 App）
        context.sendBroadcast(Intent(HyperRoseAction.DEVICE_DISCONNECTED).apply {
            putExtra(HyperRoseAction.EXTRA_DEVICE, device)
            setPackage(HyperRoseAction.PACKAGE_APP)
        })

        // 广播断开事件（给宿主蓝牙进程，关闭超级岛）
        context.sendBroadcast(Intent(HyperRoseAction.DEVICE_DISCONNECTED).apply {
            putExtra(HyperRoseAction.EXTRA_DEVICE, device)
            setPackage(HyperRoseAction.PACKAGE_MI_BLUETOOTH)
        })

    }

    private fun resolveContext(serviceObj: Any): Context? {
        return try {
            ReflectionHelper.callMethod(serviceObj, "getApplicationContext") as? Context
        } catch (_: Throwable) {
            try {
                ReflectionHelper.getField(serviceObj, "mContext") as? Context
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun registerCommandReceiverIfNeeded(module: XposedModule, context: Context) {
        if (commandReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(HyperRoseAction.SET_ANC)
            addAction(HyperRoseAction.SET_ANC_DEPTH)
            addAction(HyperRoseAction.SET_TRANS_LEVEL)
            addAction(HyperRoseAction.SET_EQ)
            addAction(HyperRoseAction.SET_GAME_MODE)
            addAction(HyperRoseAction.FIND_EARPHONE)
            addAction(HyperRoseAction.REFRESH_STATUS)
            addAction(HyperRoseAction.DISCONNECT_GATT)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val manager = gattManager ?: return
                try {
                    when (intent.action) {
                        HyperRoseAction.SET_ANC -> {
                            val mode = intent.getStringExtra(HyperRoseAction.EXTRA_MODE)
                                ?.let(AncMode::valueOf)
                                ?: return
                            manager.sendCommand(RosePackets.ancCommand(mode))
                        }

                        HyperRoseAction.SET_ANC_DEPTH -> {
                            val depth = intent.getStringExtra(HyperRoseAction.EXTRA_DEPTH)
                                ?.let(AncDepth::valueOf)
                                ?: return
                            manager.sendCommand(RosePackets.ancDepthCommand(depth))
                        }

                        HyperRoseAction.SET_TRANS_LEVEL -> {
                            val level = intent.getStringExtra(HyperRoseAction.EXTRA_LEVEL)
                                ?.let(TransparencyLevel::valueOf)
                                ?: return
                            manager.sendCommand(RosePackets.transLevelCommand(level))
                        }

                        HyperRoseAction.SET_EQ -> {
                            val mode = intent.getStringExtra(HyperRoseAction.EXTRA_MODE)
                                ?.let(EqPreset::valueOf)
                                ?: return
                            manager.sendCommand(RosePackets.eqCommand(mode))
                        }

                        HyperRoseAction.SET_GAME_MODE -> {
                            if (!intent.hasExtra(HyperRoseAction.EXTRA_ENABLED)) return
                            val enabled = intent.getBooleanExtra(HyperRoseAction.EXTRA_ENABLED, false)
                            manager.sendCommand(RosePackets.gameModeCommand(enabled))
                        }

                        HyperRoseAction.FIND_EARPHONE -> {
                            when (intent.getStringExtra(HyperRoseAction.EXTRA_SIDE)?.uppercase()) {
                                HyperRoseAction.SIDE_LEFT -> manager.sendCommand(RosePackets.FIND_LEFT_ON)
                                HyperRoseAction.SIDE_RIGHT -> manager.sendCommand(RosePackets.FIND_RIGHT_ON)
                                else -> manager.sendCommand(RosePackets.FIND_ALL_OFF)
                            }
                        }

                        HyperRoseAction.REFRESH_STATUS -> manager.refreshStatus()

                        HyperRoseAction.DISCONNECT_GATT -> {
                            manager.disconnect()
                            gattManager = null
                        }
                    }
                } catch (e: Throwable) {
                    module.log(Log.ERROR, TAG, "BluetoothHook: command receiver error", e)
                }
            }
        }

        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        commandReceiverRegistered = true
        module.log(Log.INFO, TAG, "BluetoothHook: command receiver registered")
    }

    /** 供外部获取当前 GattManager */
    fun getGattManager(): HookProcessGattClient? = gattManager
}
