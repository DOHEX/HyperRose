package com.dohex.hyperrose.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.dohex.hyperrose.hook.HyperRoseModuleEntry.Companion.TAG
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.DeviceProfileRegistry
import com.dohex.hyperrose.util.ReflectionHelper
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction

/** com.android.bluetooth 进程的 Hook。 监听 A2DP 连接状态变化，识别目标耳机并启动 GATT 通信。 */
object BluetoothProcessHook {

    @SuppressLint("StaticFieldLeak")
    private var session: DeviceSession? = null

    /** 同进程内直接访问当前 GATT 客户端（供 HeadsetServiceBinderHook 使用） */
    internal fun currentSession(): DeviceSession? = session

    private var commandReceiverRegistered = false

    @SuppressLint("PrivateApi")
    fun init(
        module: XposedModule,
        param: PackageLoadedParam,
    ) {
        val cl = param.defaultClassLoader

        // 加载白名单（从 App 进程 ContentProvider 查询）
        runCatching {
            val clazz = Class.forName("android.app.ActivityThread")
            val appCtx =
                clazz.getMethod("currentApplication").invoke(null) as? Context
            if (appCtx != null) com.dohex.hyperrose.ipc.AuthorizedDeviceClient.ensureLoaded(appCtx)
        }

        // Hook A2dpService.handleConnectionStateChanged(BluetoothDevice, int, int)
        try {
            val a2dpClass = cl.loadClass("com.android.bluetooth.a2dp.A2dpService")
            val method =
                a2dpClass.getDeclaredMethod(
                    "handleConnectionStateChanged",
                    BluetoothDevice::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )

            module.hook(method)?.intercept { chain ->
                val result = chain.proceed()

                val device = chain.getArg(0) as? BluetoothDevice
                val fromState = chain.getArg(1) as Int
                val currState = chain.getArg(2) as Int

                if (device != null && currState != fromState && isSupportedDevice(device)) {
                    val serviceObj = chain.thisObject
                    try {
                        when (currState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                module.log(
                                    Log.INFO,
                                    TAG,
                                    "Device connected: ${device.address}",
                                )
                                onDeviceConnected(module, serviceObj, device)
                            }

                            BluetoothProfile.STATE_DISCONNECTED -> {
                                module.log(
                                    Log.INFO,
                                    TAG,
                                    "Device disconnected: ${device.address}",
                                )
                                onDeviceDisconnected(module, serviceObj, device)
                            }
                        }
                    } catch (e: Throwable) {
                        module.log(Log.ERROR, TAG, "Error handling connection state change", e)
                    }
                }
                result
            }

            module.log(Log.INFO, TAG, "BluetoothProcessHook: hooked A2dpService")
        } catch (e: Throwable) {
            module.log(Log.ERROR, TAG, "BluetoothProcessHook: failed to hook A2dpService", e)
        }

        // 初始化 HeadsetService Binder Hook（系统原生耳机 UI 数据注入）
        try {
            HeadsetServiceBinderHook.init(module, cl)
        } catch (e: Throwable) {
            module.log(
                Log.ERROR,
                TAG,
                "BluetoothProcessHook: failed to init HeadsetServiceBinderHook",
                e
            )
        }
        module.log(
            Log.INFO,
            TAG,
            "BluetoothProcessHook: init complete, command receiver will register on device connect"
        )
    }

    @SuppressLint("MissingPermission")
    private fun isSupportedDevice(device: BluetoothDevice): Boolean {
        val name = device.name ?: device.alias
        if (name != null && DeviceProfileRegistry.findByName(name) != null) return true
        val address = device.address ?: return false
        return com.dohex.hyperrose.ipc.AuthorizedDeviceClient.isAuthorized(address)
    }

    private fun onDeviceConnected(
        module: XposedModule,
        serviceObj: Any,
        device: BluetoothDevice,
    ) {
        val context = resolveContext(serviceObj) ?: return

        val profile = (device.name ?: device.alias)?.let { DeviceProfileRegistry.findByName(it) }
            ?: run {
                module.log(Log.WARN, TAG, "No profile for ${device.name}")
                return
            }

        registerCommandReceiverIfNeeded(module, context)

        // 启动 GATT 通信
        // 根据 TransportSpec 选择对应的传输实现
        session?.disconnect()
        session = when (profile.transport) {
            is com.dohex.hyperrose.profile.TransportSpec.Gatt ->
                GattDeviceSession(context, module, profile)
            is com.dohex.hyperrose.profile.TransportSpec.Rfcomm ->
                RfcommDeviceSession(context, module, profile)
        }.also { it.connect(device) }

        // 广播连接事件（给 App、MiBluetooth、MiLink、蓝牙进程 binder hook）
        listOf(
            HyperRoseAction.PACKAGE_APP,
            HyperRoseAction.PACKAGE_MI_BLUETOOTH,
            HyperRoseAction.PACKAGE_MILINK,
            HyperRoseAction.PACKAGE_BLUETOOTH,
        ).forEach { pkg ->
            context.sendBroadcast(
                Intent(HyperRoseAction.DEVICE_CONNECTED).apply {
                    putExtra(HyperRoseAction.EXTRA_DEVICE, device)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
        }
    }

    private fun onDeviceDisconnected(
        module: XposedModule,
        serviceObj: Any,
        device: BluetoothDevice,
    ) {
        // 断开 GATT
        session?.disconnect()
        session = null

        val context = resolveContext(serviceObj) ?: return

        // 广播断开事件（给 App、MiBluetooth、MiLink、蓝牙进程 binder hook）
        listOf(
            HyperRoseAction.PACKAGE_APP,
            HyperRoseAction.PACKAGE_MI_BLUETOOTH,
            HyperRoseAction.PACKAGE_MILINK,
            HyperRoseAction.PACKAGE_BLUETOOTH,
        ).forEach { pkg ->
            context.sendBroadcast(
                Intent(HyperRoseAction.DEVICE_DISCONNECTED).apply {
                    putExtra(HyperRoseAction.EXTRA_DEVICE, device)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
        }
    }

    private fun resolveContext(serviceObj: Any): Context? = try {
        ReflectionHelper.callMethod(serviceObj, "getApplicationContext") as? Context
    } catch (_: Throwable) {
        try {
            ReflectionHelper.getField(serviceObj, "mContext") as? Context
        } catch (_: Throwable) {
            null
        }
    }

    private fun registerCommandReceiverIfNeeded(
        module: XposedModule,
        context: Context,
    ) {
        if (commandReceiverRegistered) return

        val filter =
            IntentFilter().apply { HyperRoseAction.APP_CONTROL_ACTIONS.forEach(::addAction) }

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    module.log(
                        Log.DEBUG,
                        TAG,
                        ">>> CommandReceiver: action=${intent.action} session=${session != null} extras=${
                            intent.extras?.keySet()?.joinToString()
                        }"
                    )
                    val manager = session ?: run {
                        module.log(
                            Log.WARN,
                            TAG,
                            "!!! CommandReceiver: session is NULL, dropping ${intent.action}"
                        )
                        return
                    }
                    try {
                        when (intent.action) {
                            HyperRoseAction.SET_ANC -> {
                                val mode =
                                    intent.getStringExtra(HyperRoseAction.EXTRA_MODE)
                                        ?.let(AncMode::valueOf)
                                        ?: return
                                manager.sendCommand(manager.profile.protocol.ancCommand(mode))
                            }

                            HyperRoseAction.SET_ANC_DEPTH -> {
                                val depth =
                                    intent.getStringExtra(HyperRoseAction.EXTRA_DEPTH)
                                        ?.let(AncDepth::valueOf)
                                        ?: return
                                manager.sendCommand(manager.profile.protocol.ancDepthCommand(depth))
                            }

                            HyperRoseAction.SET_TRANS_LEVEL -> {
                                val level =
                                    intent
                                        .getStringExtra(HyperRoseAction.EXTRA_LEVEL)
                                        ?.let(TransparencyLevel::valueOf) ?: return
                                manager.sendCommand(manager.profile.protocol.transLevelCommand(level))
                            }

                            HyperRoseAction.SET_EQ -> {
                                val mode =
                                    intent.getStringExtra(HyperRoseAction.EXTRA_MODE)
                                        ?.let(EqPreset::valueOf)
                                        ?: return
                                manager.sendCommand(manager.profile.protocol.eqCommand(mode))
                            }

                            HyperRoseAction.SET_GAME_MODE -> {
                                if (!intent.hasExtra(HyperRoseAction.EXTRA_ENABLED)) return
                                val enabled =
                                    intent.getBooleanExtra(HyperRoseAction.EXTRA_ENABLED, false)
                                manager.sendCommand(manager.profile.protocol.gameModeCommand(enabled))
                            }

                            HyperRoseAction.FIND_EARPHONE -> {
                                when (intent.getStringExtra(HyperRoseAction.EXTRA_SIDE)
                                    ?.uppercase()) {
                                    HyperRoseAction.SIDE_LEFT -> {
                                        manager.sendCommand(
                                            manager.profile.protocol.findLeftOn,
                                        )
                                    }

                                    HyperRoseAction.SIDE_RIGHT -> {
                                        manager.sendCommand(
                                            manager.profile.protocol.findRightOn,
                                        )
                                    }

                                    else -> {
                                        manager.sendCommand(manager.profile.protocol.findAllOff)
                                    }
                                }
                            }

                            HyperRoseAction.ANC_SELECT -> {
                                val modeName = intent.getStringExtra(HyperRoseAction.EXTRA_MODE)
                                val mode =
                                    modeName?.let { runCatching { AncMode.valueOf(it) }.getOrNull() }
                                module.log(
                                    Log.DEBUG,
                                    TAG,
                                    ">>> CommandReceiver: ANC_SELECT modeName=$modeName mode=$mode"
                                )
                                if (mode == null) {
                                    module.log(
                                        Log.WARN,
                                        TAG,
                                        "!!! CommandReceiver: ANC_SELECT mode parse FAILED — modeName=$modeName"
                                    )
                                    return
                                }
                                manager.sendCommand(manager.profile.protocol.ancCommand(mode))
                                module.log(
                                    Log.DEBUG,
                                    TAG,
                                    "<<< CommandReceiver: ANC_SELECT command sent to earbuds: $mode"
                                )
                            }

                            HyperRoseAction.REFRESH_STATUS -> {
                                manager.refreshStatus()
                            }

                            HyperRoseAction.DISCONNECT_GATT -> {
                                manager.disconnect()
                                session = null
                            }
                        }
                    } catch (e: Throwable) {
                        module.log(
                            Log.ERROR,
                            TAG,
                            "BluetoothProcessHook: command handling failed",
                            e
                        )
                    }
                }
            }

        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        commandReceiverRegistered = true
        module.log(Log.INFO, TAG, "BluetoothProcessHook: command receiver ready")
    }
}
