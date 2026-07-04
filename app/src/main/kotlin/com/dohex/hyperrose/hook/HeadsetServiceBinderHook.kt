package com.dohex.hyperrose.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.dohex.hyperrose.util.ReflectionHelper
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction

/**
 * BluetoothHeadsetService AIDL Binder Hook。
 * 在 com.android.bluetooth 进程中运行，拦截系统 MiuiHeadsetActivity 的数据通道。
 *
 * 核心机制：
 * 1. hook BluetoothHeadsetService.onBind() 获取 binder 类
 * 2. 拦截 IMiuiHeadsetCallback 注册，保存回调引用
 * 3. 通过 callback.refreshStatus(address, payload) 推送真实耳机电量/ANC 数据
 * 4. 拦截系统 UI 发来的 ANC 命令，转换为 HyperRose 协议
 *
 * 使系统原生耳机控制界面能正确显示目标耳机的状态。
 */
@SuppressLint("MissingPermission")
object HeadsetServiceBinderHook {
    private const val LOG_TAG = "HyperRose-Binder"
    private const val SERVICE_CLASS =
        "com.android.bluetooth.ble.app.headset.BluetoothHeadsetService"
    private const val DESCRIPTOR = "com.android.bluetooth.ble.app.IMiuiHeadsetService"

    private val knownAddresses = linkedSetOf<String>()
    private val callbacks = linkedMapOf<IBinder, Any>()
    private val handler = Handler(Looper.getMainLooper())
    private val hookedBinderClasses = linkedSetOf<String>()

    private var module: XposedModule? = null
    private var context: Context? = null
    private var receiverRegistered = false
    private var currentDevice: BluetoothDevice? = null
    private var currentAddress: String? = null
    private var currentName: String? = null

    // 本地状态缓存（广播更新 + GATT 直读兜底）
    private var cachedBattery: TwsBatteryState? = null
    private var cachedAnc: AncMode? = null
    private var cachedAncDepth: AncDepth? = null
    private var cachedTransLevel: TransparencyLevel? = null

    // 待重放的 ANC 命令（GATT 客户端未就绪时缓存，连接恢复后自动重放）
    private var pendingAncMode: AncMode? = null
    private var pendingAncDepth: AncDepth? = null
    private var pendingTransLevel: TransparencyLevel? = null
    private var pendingRetryCount = 0
    private const val MAX_PENDING_RETRIES = 5
    private const val PENDING_RETRY_DELAY_MS = 500L

    /** LSPosed 日志（优先 module.log，回退 Log.println） */
    private fun mlog(level: Int, msg: String) {
        module?.log(level, LOG_TAG, msg) ?: Log.println(level, LOG_TAG, msg)
    }

    fun init(
        moduleRef: XposedModule,
        classLoader: ClassLoader,
    ) {
        module = moduleRef
        hookHeadsetService(moduleRef, classLoader)
        mlog(Log.WARN, "HeadsetServiceBinderHook initialized")
    }

    // ==================== BluetoothHeadsetService Hook ====================

    private fun hookHeadsetService(moduleRef: XposedModule, cl: ClassLoader) {
        mlog(Log.WARN, "=== hookHeadsetService START ===")
        mlog(Log.WARN, "hookHeadsetService: loading $SERVICE_CLASS")
        val serviceClass = runCatching { cl.loadClass(SERVICE_CLASS) }.getOrNull()
        if (serviceClass == null) {
            mlog(Log.ERROR, "!!! BluetoothHeadsetService NOT FOUND in classloader")
            return
        }
        mlog(Log.WARN, "BluetoothHeadsetService found: ${serviceClass.name}")

        // hook onBind 获取 binder 类
        runCatching {
            val onBind = serviceClass.getDeclaredMethod("onBind", Intent::class.java)
            moduleRef.hook(onBind)?.intercept { chain ->
                mlog(Log.WARN, ">>> onBind FIRED")
                val result = chain.proceed()
                registerStatusReceiver(chain.thisObject as? Context)
                val binder = result
                mlog(Log.WARN, ">>> onBind binder=${binder?.javaClass?.name}")
                if (binder != null) {
                    installBinderHooks(moduleRef, binder.javaClass)
                }
                result
            }
            mlog(Log.WARN, "Hooked BluetoothHeadsetService.onBind")
        }.onFailure { mlog(Log.ERROR, "onBind hook failed: ${it.message}") }

        // hook onCreate 也能获取 context
        runCatching {
            val onCreate = serviceClass.getDeclaredMethod("onCreate")
            moduleRef.hook(onCreate)?.intercept { chain ->
                mlog(Log.WARN, ">>> onCreate FIRED")
                val result = chain.proceed()
                registerStatusReceiver(chain.thisObject as? Context)
                result
            }
        }.onFailure { /* optional */ }

        // 尝试直接 hook 已知的 binder 类名
        listOf(
            "com.android.bluetooth.ble.app.headset.BinderC6776v",
            "com.android.bluetooth.ble.app.headset.v",
        ).forEach { className ->
            runCatching {
                val binderClass = cl.loadClass(className)
                mlog(Log.WARN, "Found known binder class: $className")
                installBinderHooks(moduleRef, binderClass)
            }.onFailure { mlog(Log.WARN, "Known binder class NOT found: $className") }
        }

        // onTransact 兜底：方法名混淆失效时仍能拦截 Parcel 级别调用
        hookMiuiHeadsetBinder(moduleRef, cl)
        mlog(Log.WARN, "=== hookHeadsetService DONE ===")
    }

    // ==================== onTransact Parcel 级别兜底 ====================

    /**
     * Hook IMiuiHeadsetService$Stub.onTransact()，通过 transaction code 拦截 ANC 等命令。
     * 当直接方法 hook 因混淆失效时，此兜底仍可工作。
     * 参考 OppoPods 的 hookMiuiHeadsetBinder() 实现。
     */
    private var stubClassLoader: ClassLoader? = null

    private fun hookMiuiHeadsetBinder(moduleRef: XposedModule, cl: ClassLoader) {
        val stubClass = listOf(
            "com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub",
        ).firstNotNullOfOrNull { name -> runCatching { cl.loadClass(name) }.getOrNull() }
        if (stubClass == null) {
            mlog(Log.ERROR, "!!! IMiuiHeadsetService.Stub NOT FOUND — onTransact fallback DISABLED")
            return
        }
        mlog(Log.WARN, "IMiuiHeadsetService.Stub FOUND: ${stubClass.name}")
        stubClassLoader = stubClass.classLoader
        runCatching {
            val onTransact = findMethodByParamTypes(
                stubClass,
                "onTransact",
                Int::class.javaPrimitiveType!!,
                Parcel::class.java,
                Parcel::class.java,
                Int::class.javaPrimitiveType!!,
            ) ?: return@runCatching
            moduleRef.hook(onTransact)?.intercept { chain ->
                val code = chain.getArg(0) as? Int ?: return@intercept chain.proceed()
                val data = chain.getArg(1) as? Parcel ?: return@intercept chain.proceed()
                val reply = chain.getArg(2) as? Parcel ?: return@intercept chain.proceed()
                handleTransaction(moduleRef, code, data, reply)?.let { return@intercept it }
                chain.proceed()
            }
            moduleRef.log(
                Log.INFO,
                LOG_TAG,
                "IMiuiHeadsetService.Stub.onTransact hooked (fallback)"
            )
        }.onFailure { module?.log(Log.WARN, LOG_TAG, "onTransact fallback hook failed", it) }
    }

    private fun handleTransaction(
        module: XposedModule,
        code: Int,
        data: Parcel,
        reply: Parcel
    ): Boolean? {
        val originalPosition = data.dataPosition()
        return runCatching {
            data.enforceInterface(DESCRIPTOR)
            when (code) {
                1 -> txnCheckSupport(data, reply)
                2 -> txnRegister(module, data, reply)
                3 -> txnUnregister(data)
                4 -> txnDeviceVoid("connect", data, reply)
                9 -> txnAncMode(data, reply)
                10 -> txnAncLevel(data, reply)
                11 -> txnAddressString("getDeviceInfo", data, reply, fakeSupport())
                12 -> txnDeviceVoid("getDeviceConfig", data, reply)
                14 -> txnSetCommonCommand(data, reply)
                15 -> txnCommonConfig(data, reply)
                16 -> txnRegisterCallbackDevice(module, data, reply)
                18 -> txnAddressBoolean("isMiTWS", data, reply, true)
                19 -> txnAddressBoolean("checkIsMiTWS", data, reply, true)
                20 -> txnAddressString("isSupportAudioSwitch", data, reply, "1")
                24 -> txnAddressBoolean("getRingFindState", data, reply, false)
                else -> null
            }
        }.onFailure {
            module.log(Log.WARN, LOG_TAG, "onTransact inspect failed code=$code", it)
        }.also {
            data.setDataPosition(originalPosition)
        }.getOrNull()
    }

    private fun txnCheckSupport(data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice() ?: return null
        if (!isRoseEarphone(device)) return null
        reply.writeNoException()
        reply.writeString(fakeSupport())
        moduleLog("txn checkSupport forced for ${device.address}")
        return true
    }

    private fun txnRegister(module: XposedModule, data: Parcel, reply: Parcel): Boolean? {
        val callback = data.readCallbackBinder(module) ?: return null
        if (currentDevice == null) return null
        rememberCallback(callback)
        reply.writeNoException()
        sendRealStatus(currentDevice!!, "txn-register")
        sendRealStatusDelayed(currentDevice!!, "txn-register-refresh", 350L)
        moduleLog("txn register captured")
        return true
    }

    private fun txnUnregister(data: Parcel): Boolean? {
        val binder = data.readStrongBinder() ?: return null
        callbacks.remove(binder)
        moduleLog("txn unregister removed")
        return null
    }

    private fun txnDeviceVoid(method: String, data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice() ?: return null
        if (!isRoseEarphone(device)) return null
        reply.writeNoException()
        sendRealStatus(device, "txn-$method")
        sendRealStatusDelayed(device, "txn-$method-refresh", 350L)
        moduleLog("txn $method no-op for ${device.address}")
        return true
    }

    private fun txnAncMode(data: Parcel, reply: Parcel): Boolean? {
        val mode = data.readInt()
        val device = data.readDevice() ?: return null
        if (!isRoseEarphone(device)) return null
        val roseAnc = roseAncFromMiuiMode(mode)
        cachedAnc = roseAnc // 乐观更新缓存，确保 sendRealStatus 推送新状态
        sendAncToGatt(roseAnc)
        sendRealStatus(device, "txn-changeAncMode:$mode")
        reply.writeNoException()
        moduleLog("txn changeAncMode miui=$mode → rose=$roseAnc")
        return true
    }

    private fun txnAncLevel(data: Parcel, reply: Parcel): Boolean? {
        val level = data.readString()
        val device = data.readDevice() ?: return null
        if (!isRoseEarphone(device)) return null
        handleAncLevel(level) // handleAncLevel 内部已乐观更新 cachedAnc/cachedAncDepth/cachedTransLevel
        sendRealStatus(device, "txn-changeAncLevel:$level")
        reply.writeNoException()
        moduleLog("txn changeAncLevel level=$level")
        return true
    }

    private fun txnAddressString(
        method: String,
        data: Parcel,
        reply: Parcel,
        forced: String
    ): Boolean? {
        val address = data.readString() ?: return null
        if (!isRoseAddress(address)) return null
        reply.writeNoException()
        reply.writeString(forced)
        moduleLog("txn $method forced $forced")
        return true
    }

    private fun txnAddressBoolean(
        method: String,
        data: Parcel,
        reply: Parcel,
        forced: Boolean
    ): Boolean? {
        val address = data.readString() ?: return null
        if (!isRoseAddress(address)) return null
        reply.writeNoException()
        reply.writeInt(if (forced) 1 else 0)
        moduleLog("txn $method forced $forced")
        return true
    }

    private fun txnSetCommonCommand(data: Parcel, reply: Parcel): Boolean? {
        val command = data.readInt()
        val value = data.readString()
        val device = data.readDevice() ?: return null
        if (!isRoseEarphone(device)) return null
        reply.writeNoException()
        reply.writeString(
            when (command) {
                102 -> "1"
                123 -> "4"
                else -> "1"
            },
        )
        sendRealStatus(device, "txn-setCommonCommand:$command")
        moduleLog("txn setCommonCommand cmd=$command val=$value")
        return true
    }

    private fun txnCommonConfig(data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice() ?: return null
        val type = data.readString()
        if (!isRoseEarphone(device)) return null
        reply.writeNoException()
        sendRealStatus(device, "txn-getCommonConfig:$type")
        moduleLog("txn getCommonConfig type=$type")
        return true
    }

    private fun txnRegisterCallbackDevice(
        module: XposedModule,
        data: Parcel,
        reply: Parcel
    ): Boolean? {
        val callback = data.readCallbackBinder(module) ?: return null
        val device = data.readDevice() ?: return null
        if (!isRoseEarphone(device)) return null
        currentDevice = device
        rememberCallback(callback)
        reply.writeNoException()
        sendRealStatus(device, "txn-registerCallbackDevice")
        sendRealStatusDelayed(device, "txn-registerCallbackDevice-refresh", 350L)
        moduleLog("txn registerCallbackDevice captured for ${device.address}")
        return true
    }

    // ==================== Parcel 工具方法 ====================

    private fun Parcel.readDevice(): BluetoothDevice? =
        if (readInt() != 0) BluetoothDevice.CREATOR.createFromParcel(this) else null

    private fun Parcel.readCallbackBinder(module: XposedModule): Any? {
        val binder = readStrongBinder() ?: return null
        val cl = binderClass?.classLoader ?: stubClassLoader ?: return null
        return runCatching {
            val stub = cl.loadClass("com.android.bluetooth.ble.app.IMiuiHeadsetCallback\$Stub")
            stub.getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, binder)
        }.onFailure {
            module.log(Log.WARN, LOG_TAG, "readCallbackBinder failed", it)
        }.getOrNull()
    }

    /** 保存 binder 类引用供 readCallbackBinder 使用 */
    private var binderClass: Class<*>? = null

    // ==================== Binder 方法 Hook ====================

    private fun installBinderHooks(module: XposedModule, binderClass: Class<*>) {
        val className = binderClass.name
        if (!hookedBinderClasses.add(className)) return
        this.binderClass = binderClass
        module.log(
            Log.INFO,
            LOG_TAG,
            "Installing binder hooks on $className (${binderClass.declaredMethods.size} methods)"
        )

        // 设备身份伪装
        hookBeforeDeviceResult(module, binderClass, "checkSupport") { fakeSupport() }
        hookBeforeAddressStringResult(
            module,
            binderClass,
            listOf("getDeviceInfo")
        ) { fakeSupport() }
        hookBeforeAddressStringResult(
            module,
            binderClass,
            listOf("isSupportAudioSwitch", "mo19775z1", "z1")
        ) { "1" }
        hookBeforeAddressBooleanResult(
            module,
            binderClass,
            listOf("isMiTWS", "mo19771O0", "O0"),
            true
        )
        hookBeforeAddressBooleanResult(
            module,
            binderClass,
            listOf("checkIsMiTWS", "mo19766B", "B"),
            true
        )
        hookBeforeAddressBooleanResult(
            module,
            binderClass,
            listOf("getRingFindState", "mo19772m0", "m0"),
            false
        )

        // setCommonCommand 拦截
        runCatching {
            val method = findMethodByParamTypes(
                binderClass,
                "setCommonCommand",
                Int::class.javaPrimitiveType!!,
                String::class.java,
                BluetoothDevice::class.java
            )
                ?: return@runCatching
            module.hook(method)?.intercept { chain ->
                val device = chain.getArg(2) as? BluetoothDevice
                if (device != null && isRoseEarphone(device)) {
                    val command = chain.getArg(0) as? Int
                    module.log(Log.DEBUG, LOG_TAG, "setCommonCommand intercepted cmd=$command")
                    sendRealStatus(device, "setCommonCommand:$command")
                    return@intercept when (command) {
                        102 -> "1"
                        123 -> "4"
                        else -> "1"
                    }
                }
                chain.proceed()
            }
        }.onFailure { module.log(Log.WARN, LOG_TAG, "setCommonCommand hook skipped", it) }

        // connect / getDeviceConfig / getCommonConfig — 拦截后推送真实状态
        hookVoidDevice(module, binderClass, "connect")
        hookVoidDevice(module, binderClass, "getDeviceConfig")
        hookVoidDeviceString(module, binderClass, "getCommonConfig")

        // ANC 命令拦截
        hookAncMode(module, binderClass)
        hookAncLevel(module, binderClass)

        // Callback 注册 — 捕获系统 UI 的回调
        hookCallbackRegistration(module, binderClass)
    }

    // ==================== Callback 注册拦截 ====================

    private fun hookCallbackRegistration(module: XposedModule, binderClass: Class<*>) {
        val callbackClass = runCatching {
            binderClass.classLoader.loadClass("com.android.bluetooth.ble.app.IMiuiHeadsetCallback")
        }.getOrNull() ?: return

        // register(callback)
        runCatching {
            val method =
                findMethodByParamTypes(binderClass, "register", callbackClass) ?: return@runCatching
            module.hook(method)?.intercept { chain ->
                val callback = chain.getArg(0)
                if (callback != null && currentDevice != null) {
                    rememberCallback(callback)
                    module.log(Log.INFO, LOG_TAG, "register callback captured")
                    sendRealStatus(currentDevice!!, "register")
                    sendRealStatusDelayed(currentDevice!!, "register-refresh", 350L)
                    return@intercept null // 吞掉原始调用
                }
                chain.proceed()
            }
            module.log(Log.INFO, LOG_TAG, "Hooked register(callback)")
        }.onFailure { module.log(Log.WARN, LOG_TAG, "register hook skipped", it) }

        // registerCallbackDevice(callback, device)
        runCatching {
            val method = findMethodByParamTypes(
                binderClass,
                "registerCallbackDevice",
                callbackClass,
                BluetoothDevice::class.java
            )
                ?: return@runCatching
            module.hook(method)?.intercept { chain ->
                val callback = chain.getArg(0)
                val device = chain.getArg(1) as? BluetoothDevice
                if (device != null && isRoseEarphone(device) && callback != null) {
                    currentDevice = device
                    rememberCallback(callback)
                    module.log(
                        Log.INFO,
                        LOG_TAG,
                        "registerCallbackDevice captured for ${device.address}"
                    )
                    sendRealStatus(device, "registerCallbackDevice")
                    sendRealStatusDelayed(device, "registerCallbackDevice-refresh", 350L)
                    return@intercept null
                }
                chain.proceed()
            }
            module.log(Log.INFO, LOG_TAG, "Hooked registerCallbackDevice")
        }.onFailure { module.log(Log.WARN, LOG_TAG, "registerCallbackDevice hook skipped", it) }

        // unregister(callback, device)
        runCatching {
            val method = findMethodByParamTypes(
                binderClass,
                "unregister",
                callbackClass,
                BluetoothDevice::class.java
            )
                ?: return@runCatching
            module.hook(method)?.intercept { chain ->
                val callback = chain.getArg(0)
                val device = chain.getArg(1) as? BluetoothDevice
                if (device != null && isRoseEarphone(device) && callback != null) {
                    forgetCallback(callback)
                    module.log(Log.INFO, LOG_TAG, "unregister callback removed")
                    return@intercept null
                }
                chain.proceed()
            }
        }.onFailure { /* optional */ }
    }

    // ==================== ANC 命令拦截 ====================

    private fun hookAncMode(module: XposedModule, binderClass: Class<*>) {
        runCatching {
            module.log(Log.INFO, LOG_TAG, "hookAncMode: searching in ${binderClass.name}")
            val method = findMethodByParamTypes(
                binderClass,
                "changeAncMode",
                Int::class.javaPrimitiveType!!,
                BluetoothDevice::class.java
            )
            if (method == null) {
                module.log(
                    Log.WARN,
                    LOG_TAG,
                    "changeAncMode(Int, BluetoothDevice) NOT FOUND in ${binderClass.name}"
                )
                module.log(Log.WARN, LOG_TAG, "Listing all 2-param methods of ${binderClass.name}:")
                binderClass.declaredMethods.forEach { m ->
                    if (m.parameterCount == 2) {
                        module.log(
                            Log.WARN,
                            LOG_TAG,
                            "  ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})"
                        )
                    }
                }
                return
            }
            module.log(
                Log.INFO,
                LOG_TAG,
                "changeAncMode found: ${method.declaringClass.name}.${method.name}"
            )
            module.hook(method)?.intercept { chain ->
                val mode = chain.getArg(0) as? Int
                val device = chain.getArg(1) as? BluetoothDevice
                val deviceName = runCatching { device?.name ?: device?.alias }.getOrNull().orEmpty()
                mlog(
                    Log.WARN,
                    ">>> changeAncMode FIRED mode=$mode device=$deviceName addr=${device?.address}"
                )
                if (device != null && isRoseEarphone(device)) {
                    val roseAnc = roseAncFromMiuiMode(mode ?: 0)
                    cachedAnc = roseAnc // 乐观更新缓存，确保 sendRealStatus 推送的是新状态
                    sendAncToGatt(roseAnc)
                    sendRealStatus(device, "changeAncMode:$mode")
                    mlog(Log.WARN, ">>> changeAncMode HANDLED miui=$mode → rose=$roseAnc")
                    return@intercept null
                }
                module.log(Log.INFO, LOG_TAG, ">>> changeAncMode PASSED THROUGH (not our device)")
                chain.proceed()
            }
            module.log(Log.INFO, LOG_TAG, "Hooked changeAncMode OK")
        }.onFailure { module.log(Log.WARN, LOG_TAG, "changeAncMode hook FAILED", it) }
    }

    private fun hookAncLevel(module: XposedModule, binderClass: Class<*>) {
        runCatching {
            val method = findMethodByParamTypes(
                binderClass,
                "changeAncLevel",
                String::class.java,
                BluetoothDevice::class.java
            )
            if (method == null) {
                module.log(Log.WARN, LOG_TAG, "changeAncLevel not found in ${binderClass.name}")
                return
            }
            module.hook(method)?.intercept { chain ->
                val level = chain.getArg(0) as? String
                val device = chain.getArg(1) as? BluetoothDevice
                if (device != null && isRoseEarphone(device)) {
                    handleAncLevel(level)
                    sendRealStatus(device, "changeAncLevel:$level")
                    module.log(Log.INFO, LOG_TAG, "changeAncLevel intercepted level=$level")
                    return@intercept null
                }
                chain.proceed()
            }
            module.log(Log.INFO, LOG_TAG, "Hooked changeAncLevel")
        }.onFailure { module.log(Log.WARN, LOG_TAG, "changeAncLevel hook skipped", it) }
    }

    // ==================== 通用 Binder 方法拦截 ====================

    private fun hookBeforeDeviceResult(
        module: XposedModule,
        clazz: Class<*>,
        methodName: String,
        result: () -> Any?,
    ) {
        runCatching {
            val method = findMethodByParamTypes(clazz, methodName, BluetoothDevice::class.java)
            if (method == null) {
                module.log(
                    Log.WARN,
                    LOG_TAG,
                    "$methodName(BluetoothDevice) not found in ${clazz.name}"
                )
                return
            }
            module.hook(method)?.intercept { chain ->
                val device = chain.getArg(0) as? BluetoothDevice
                if (device != null && isRoseEarphone(device)) {
                    currentDevice = device
                    module.log(Log.DEBUG, LOG_TAG, "$methodName intercepted for ${device.address}")
                    return@intercept result()
                }
                chain.proceed()
            }
            module.log(Log.INFO, LOG_TAG, "Hooked $methodName(BluetoothDevice)")
        }.onFailure {
            module.log(
                Log.WARN,
                LOG_TAG,
                "Hook $methodName(BluetoothDevice) skipped",
                it
            )
        }
    }

    private fun hookBeforeAddressStringResult(
        module: XposedModule,
        clazz: Class<*>,
        methodNames: List<String>,
        result: () -> String,
    ) {
        val name = methodNames.firstOrNull {
            findMethodByParamTypes(
                clazz,
                it,
                String::class.java
            ) != null
        } ?: return
        runCatching {
            val method = findMethodByParamTypes(clazz, name, String::class.java) ?: return
            module.hook(method)?.intercept { chain ->
                val address = chain.getArg(0) as? String
                if (address != null && isRoseAddress(address)) {
                    return@intercept result()
                }
                chain.proceed()
            }
            module.log(Log.INFO, LOG_TAG, "Hooked $name(String)")
        }.onFailure { module.log(Log.WARN, LOG_TAG, "Hook $name(String) skipped", it) }
    }

    private fun hookBeforeAddressBooleanResult(
        module: XposedModule,
        clazz: Class<*>,
        methodNames: List<String>,
        forced: Boolean,
    ) {
        val name = methodNames.firstOrNull {
            findMethodByParamTypes(
                clazz,
                it,
                String::class.java
            ) != null
        } ?: return
        runCatching {
            val method = findMethodByParamTypes(clazz, name, String::class.java) ?: return
            module.hook(method)?.intercept { chain ->
                val address = chain.getArg(0) as? String
                if (address != null && isRoseAddress(address)) {
                    return@intercept forced
                }
                chain.proceed()
            }
            module.log(Log.INFO, LOG_TAG, "Hooked $name(String) → $forced")
        }.onFailure { module.log(Log.WARN, LOG_TAG, "Hook $name(String) skipped", it) }
    }

    private fun hookVoidDevice(module: XposedModule, clazz: Class<*>, methodName: String) {
        runCatching {
            val method =
                findMethodByParamTypes(clazz, methodName, BluetoothDevice::class.java) ?: return
            module.hook(method)?.intercept { chain ->
                val device = chain.getArg(0) as? BluetoothDevice
                if (device != null && isRoseEarphone(device)) {
                    currentDevice = device
                    sendRealStatus(device, methodName)
                    sendRealStatusDelayed(device, "$methodName-refresh", 350L)
                    return@intercept null
                }
                chain.proceed()
            }
        }.onFailure { /* optional */ }
    }

    private fun hookVoidDeviceString(module: XposedModule, clazz: Class<*>, methodName: String) {
        runCatching {
            val method = findMethodByParamTypes(
                clazz,
                methodName,
                BluetoothDevice::class.java,
                String::class.java
            ) ?: return
            module.hook(method)?.intercept { chain ->
                val device = chain.getArg(0) as? BluetoothDevice
                if (device != null && isRoseEarphone(device)) {
                    currentDevice = device
                    sendRealStatus(device, methodName)
                    sendRealStatusDelayed(device, "$methodName-refresh", 350L)
                    return@intercept null
                }
                chain.proceed()
            }
        }.onFailure { /* optional */ }
    }

    // ==================== 状态广播接收器（OppoPods 模式） ====================

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx

        val filter = IntentFilter().apply {
            addAction(HyperRoseAction.DEVICE_CONNECTED)
            addAction(HyperRoseAction.DEVICE_DISCONNECTED)
            addAction(HyperRoseAction.BATTERY_CHANGED)
            addAction(HyperRoseAction.ANC_CHANGED)
            addAction(HyperRoseAction.ANC_DEPTH_CHANGED)
            addAction(HyperRoseAction.TRANS_LEVEL_CHANGED)
        }

        context?.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        HyperRoseAction.DEVICE_CONNECTED -> {
                            currentDevice = intent.getParcelableExtra(HyperRoseAction.EXTRA_DEVICE)
                            currentAddress = currentDevice?.address
                            currentName = currentDevice?.name
                            currentAddress?.let { knownAddresses.add(it.uppercase()) }
                            // GATT 客户端在 DEVICE_CONNECTED 广播后被创建，
                            // 延迟重放设备重连前缓存的 ANC 命令
                            if (pendingAncMode != null || pendingAncDepth != null || pendingTransLevel != null) {
                                handler.postDelayed({ replayPendingAncCommands() }, 800L)
                            }
                        }

                        HyperRoseAction.DEVICE_DISCONNECTED -> {
                            currentAddress = null
                            currentName = null
                        }

                        HyperRoseAction.BATTERY_CHANGED -> {
                            // 从 Intent 解析电池状态并缓存（跨进程兼容：com.xiaomi.bluetooth 中无 GATT client）
                            cachedBattery = intent.parseBatteryFromExtras() ?: cachedBattery
                        }

                        HyperRoseAction.ANC_CHANGED -> {
                            // 从 Intent 解析 ANC 模式并缓存
                            intent.getStringExtra(HyperRoseAction.EXTRA_MODE)?.let { name ->
                                runCatching { cachedAnc = AncMode.valueOf(name) }
                            }
                        }

                        HyperRoseAction.ANC_DEPTH_CHANGED -> {
                            // 从 Intent 解析降噪深度并缓存
                            intent.getStringExtra(HyperRoseAction.EXTRA_DEPTH)?.let { name ->
                                runCatching { cachedAncDepth = AncDepth.valueOf(name) }
                            }
                        }

                        HyperRoseAction.TRANS_LEVEL_CHANGED -> {
                            // 从 Intent 解析通透强度并缓存
                            intent.getStringExtra(HyperRoseAction.EXTRA_LEVEL)?.let { name ->
                                runCatching { cachedTransLevel = TransparencyLevel.valueOf(name) }
                            }
                        }
                    }
                    moduleLog("state action=${intent.action} addr=$currentAddress anc=$cachedAnc")
                    notifyCallbacks("broadcast:${intent.action}")
                }
            },
            filter,
            Context.RECEIVER_EXPORTED,
        )

        receiverRegistered = true
        moduleLog("State receiver registered")

        // 请求 GATT 客户端立即广播当前状态（和 OppoPods 的 ACTION_REFRESH_STATUS 一样）
        context?.sendBroadcast(
            Intent(HyperRoseAction.REFRESH_STATUS).apply {
                `package` = HyperRoseAction.PACKAGE_BLUETOOTH
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            },
        )
        moduleLog("Sent REFRESH_STATUS")
    }

    /**
     * 从广播 Intent 解析 TwsBatteryState。
     * 在 com.xiaomi.bluetooth 进程中没有 GATT client，必须从广播中解析状态。
     * 与 GattDeviceSession.broadcastState() 的 extras 格式一致。
     */
    private fun Intent.parseBatteryFromExtras(): TwsBatteryState? {
        val leftLevelRaw = getIntExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, -1)
        val rightLevelRaw = getIntExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, -1)
        val leftCharging = getBooleanExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, false)
        val rightCharging = getBooleanExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, false)
        val caseLevelRaw = getIntExtra(HyperRoseAction.EXTRA_CASE_LEVEL, -1)

        val leftLevel = leftLevelRaw.asBatteryLevelOrNull()
        val rightLevel = rightLevelRaw.asBatteryLevelOrNull()
        val caseLevel = caseLevelRaw.asBatteryLevelOrNull()

        if (leftLevel == null && rightLevel == null && caseLevel == null) return null

        return TwsBatteryState(
            left = leftLevel?.let { EarBatteryState(it, leftCharging) },
            right = rightLevel?.let { EarBatteryState(it, rightCharging) },
            caseBattery = caseLevel,
        )
    }

    /**
     * 从同进程 GATT 客户端直读 + 本地缓存兜底（和 OppoPods 的 realRefreshPayload 一样）。
     *
     * 注意：cachedAnc/cachedAncDepth/cachedTransLevel 会在用户操作后被乐观更新，
     * 而 gatt.currentAnc 要等耳机回报后才更新。因此 ANC 相关字段优先使用缓存值，
     * 避免 sendRealStatus 推送旧状态导致控制中心 UI 回弹。
     */
    private fun readCurrentState(): CurrentState {
        val gatt = BluetoothProcessHook.currentSession()
        val battery = gatt?.currentBattery ?: cachedBattery
        // ANC 字段：优先用乐观更新的缓存（耳机回报后会同步更新）
        val anc = cachedAnc ?: gatt?.currentAnc
        val ancDepth = cachedAncDepth ?: gatt?.currentAncDepth
        val transLevel = cachedTransLevel ?: gatt?.currentTransLevel
        // 同步设备信息
        if (currentAddress == null && gatt != null) {
            currentAddress = gatt.connectedAddress
            currentName = gatt.connectedName
            currentAddress?.let { knownAddresses.add(it.uppercase()) }
        }
        return CurrentState(battery, anc, ancDepth, transLevel)
    }

    private data class CurrentState(
        val battery: TwsBatteryState?,
        val anc: AncMode?,
        val ancDepth: AncDepth?,
        val transLevel: TransparencyLevel?,
    )

    // ==================== Callback 数据推送 ====================

    private fun sendRealStatus(device: BluetoothDevice, reason: String) {
        sendRealStatus(device.address, reason)
    }

    private fun sendRealStatusDelayed(device: BluetoothDevice, reason: String, delayMs: Long) {
        handler.postDelayed({ sendRealStatus(device.address, reason) }, delayMs)
    }

    private fun sendRealStatus(address: String, reason: String) {
        if (callbacks.isEmpty()) return
        val payload = buildMiuiRefreshPayload()
        handler.post {
            callbacks.values.toList().forEach { callback ->
                runCatching {
                    ReflectionHelper.callMethod(callback, "refreshStatus", address, payload)
                    moduleLog("refreshStatus sent reason=$reason addr=$address")
                }.onFailure {
                    forgetCallback(callback)
                    moduleLog("refreshStatus failed, removed callback")
                }
            }
        }
    }

    private fun notifyCallbacks(reason: String) {
        val device = currentDevice
        if (device != null) {
            sendRealStatus(device, reason)
        } else {
            val address = currentAddress ?: return
            sendRealStatus(address, reason)
        }
    }

    // ==================== MiUI Payload 构建 ====================

    /**
     * MiUI 耳机状态刷新 payload：逗号分隔的 16 个字段。
     * [0]=左耳电量 [1]=右耳电量 [2]=盒电量 [7]=ANC等级 [8]="true" [11]="00" [13]="00" [14]="00"
     *
     * 电量格式：0-100 正常，255=未连接，充电时 value | 128
     * ANC 等级：0100=降噪中 0101=降噪轻 0102=降噪深 0103=智能降噪 0200=通透 0000=关闭
     */
    private fun buildMiuiRefreshPayload(): String {
        val state = readCurrentState()
        val battery = state.battery
        val values = MutableList(16) { "" }
        values[0] = miuiBatteryValue(battery?.left?.level, battery?.left?.isCharging)
        values[1] = miuiBatteryValue(battery?.right?.level, battery?.right?.isCharging)
        values[2] = miuiBatteryValue(battery?.caseBattery, null)
        values[7] = miuiAncLevel(state.anc, state.ancDepth, state.transLevel)
        values[8] = "true"
        values[11] = "00"
        values[13] = "00"
        values[14] = "00"
        return values.joinToString(",")
    }

    private fun miuiBatteryValue(level: Int?, charging: Boolean?): String {
        if (level == null || level !in 0..100) return "255"
        return (if (charging == true) level or 128 else level).toString()
    }

    /**
     * HyperRose AncMode/AncDepth → MIUI 4位ANC等级码
     */
    private fun miuiAncLevel(
        anc: AncMode?,
        depth: AncDepth?,
        transLevel: TransparencyLevel?
    ): String = when (anc) {
        AncMode.NOISE_CANCEL -> when (depth) {
            AncDepth.DEEP -> "0102"
            AncDepth.MEDIUM -> "0100"
            AncDepth.LIGHT -> "0101"
            null -> "0100"
        }

        AncMode.WIND_NOISE -> "0101"

        AncMode.TRANSPARENT -> when (transLevel) {
            TransparencyLevel.VOCAL -> "0201"
            else -> "0200"
        }

        AncMode.NORMAL, null -> "0000"
    }

    // ==================== MIUI 命令 → HyperRose 转换 ====================

    private fun roseAncFromMiuiMode(mode: Int): AncMode? = when (mode) {
        1 -> AncMode.NOISE_CANCEL

        // MIUI NC
        2 -> AncMode.TRANSPARENT

        // MIUI Transparency
        else -> AncMode.NORMAL // MIUI OFF
    }

    /**
     * MIUI ANC Level 码 → HyperRose 命令
     * 01xx = 降噪类, 02xx = 通透类
     * 发送命令前乐观更新缓存，确保后续 sendRealStatus 推送的是新状态
     */
    private fun handleAncLevel(level: String?) {
        if (level == null) return
        when {
            level.startsWith("0201") -> {
                cachedAnc = AncMode.TRANSPARENT
                cachedTransLevel = TransparencyLevel.VOCAL
                sendAncToGatt(AncMode.TRANSPARENT)
                sendTransLevelToGatt(TransparencyLevel.VOCAL)
            }

            level.startsWith("0200") -> {
                cachedAnc = AncMode.TRANSPARENT
                cachedTransLevel = TransparencyLevel.STANDARD
                sendAncToGatt(AncMode.TRANSPARENT)
                sendTransLevelToGatt(TransparencyLevel.STANDARD)
            }

            level.startsWith("0102") -> {
                cachedAnc = AncMode.NOISE_CANCEL
                cachedAncDepth = AncDepth.DEEP
                sendAncToGatt(AncMode.NOISE_CANCEL)
                sendAncDepthToGatt(AncDepth.DEEP)
            }

            level.startsWith("0103") -> {
                cachedAnc = AncMode.NOISE_CANCEL
                cachedAncDepth = null
                sendAncToGatt(AncMode.NOISE_CANCEL)
                // Smart 自适应降噪 — ROSE 无直接对应子模式，发送降噪命令即可
            }

            level.startsWith("0101") -> {
                cachedAnc = AncMode.NOISE_CANCEL
                cachedAncDepth = AncDepth.LIGHT
                sendAncToGatt(AncMode.NOISE_CANCEL)
                sendAncDepthToGatt(AncDepth.LIGHT)
            }

            level.startsWith("0100") -> {
                cachedAnc = AncMode.NOISE_CANCEL
                cachedAncDepth = AncDepth.MEDIUM
                sendAncToGatt(AncMode.NOISE_CANCEL)
                sendAncDepthToGatt(AncDepth.MEDIUM)
            }

            level.startsWith("01") -> {
                cachedAnc = AncMode.NOISE_CANCEL
                sendAncToGatt(AncMode.NOISE_CANCEL)
            }

            level.startsWith("02") -> {
                cachedAnc = AncMode.TRANSPARENT
                sendAncToGatt(AncMode.TRANSPARENT)
            }

            else -> {
                cachedAnc = AncMode.NORMAL
                sendAncToGatt(AncMode.NORMAL)
            }
        }
    }

    /**
     * 发送 ANC 命令到耳机，然后触发状态刷新（和 OppoPods 的 sendOppoAnc 一样）。
     * 1. GATT 直发命令
     * 2. 发 REFRESH_STATUS → GATT 客户端查询耳机 → 回包 → 广播 → hook 推送给 callback
     * 3. GATT 未就绪时缓存命令，连接恢复后自动重放
     */
    private fun sendAncToGatt(mode: AncMode?) {
        if (mode == null) return
        val gatt = BluetoothProcessHook.currentSession()
        if (gatt != null) {
            gatt.sendCommand(gatt.profile.protocol.ancCommand(mode))
            moduleLog("ANC command sent directly: $mode")
            // 命令发送成功后清除待重放缓存
            pendingAncMode = null
            pendingRetryCount = 0
        } else {
            // GATT 客户端未就绪：缓存命令，稍后重试
            pendingAncMode = mode
            moduleLog("GATT client null, caching ANC=$mode for retry (pendingRetryCount=$pendingRetryCount)")
            val ctx = context ?: return
            ctx.sendBroadcast(
                Intent(HyperRoseAction.ANC_SELECT).apply {
                    putExtra(HyperRoseAction.EXTRA_MODE, mode.name)
                    `package` = HyperRoseAction.PACKAGE_BLUETOOTH
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
            // 调度延迟重试
            if (pendingRetryCount < MAX_PENDING_RETRIES) {
                pendingRetryCount++
                handler.postDelayed(
                    { replayPendingAncCommands() },
                    PENDING_RETRY_DELAY_MS * pendingRetryCount
                )
            }
        }
        // 发完命令后触发状态刷新（关键！OppoPods 就是这么做的）
        requestStatusRefresh("sendAncToGatt:$mode")
    }

    private fun sendAncDepthToGatt(depth: AncDepth) {
        val gatt = BluetoothProcessHook.currentSession()
        if (gatt != null) {
            gatt.sendCommand(gatt.profile.protocol.ancDepthCommand(depth))
            moduleLog("ANC depth sent directly: $depth")
            pendingAncDepth = null
        } else {
            pendingAncDepth = depth
            moduleLog("GATT client null, caching ANC depth=$depth for retry")
        }
        requestStatusRefresh("sendAncDepth:$depth")
    }

    private fun sendTransLevelToGatt(level: TransparencyLevel) {
        val gatt = BluetoothProcessHook.currentSession()
        if (gatt != null) {
            gatt.sendCommand(gatt.profile.protocol.transLevelCommand(level))
            moduleLog("Trans level sent directly: $level")
            pendingTransLevel = null
        } else {
            pendingTransLevel = level
            moduleLog("GATT client null, caching trans level=$level for retry")
        }
        requestStatusRefresh("sendTransLevel:$level")
    }

    /**
     * 重放缓存的 ANC 命令（GATT 客户端就绪时调用）。
     * 和 OppoPods 的 RfcommController 不同，BLE GATT 客户端是异步创建的，
     * Binder hook 可能比 GATT 连接更早收到 ANC 命令，因此需要缓存重放机制。
     */
    private fun replayPendingAncCommands() {
        val gatt = BluetoothProcessHook.currentSession()
        if (gatt == null) {
            moduleLog(
                "replayPendingAncCommands: GATT still null, " +
                        "pendingAnc=$pendingAncMode depth=$pendingAncDepth trans=$pendingTransLevel",
            )
            if (pendingAncMode != null && pendingRetryCount < MAX_PENDING_RETRIES) {
                pendingRetryCount++
                handler.postDelayed(
                    { replayPendingAncCommands() },
                    PENDING_RETRY_DELAY_MS * pendingRetryCount
                )
            }
            return
        }
        pendingRetryCount = 0
        pendingAncMode?.let { mode ->
            gatt.sendCommand(gatt.profile.protocol.ancCommand(mode))
            moduleLog("replay: ANC command sent: $mode")
            pendingAncMode = null
        }
        pendingAncDepth?.let { depth ->
            gatt.sendCommand(gatt.profile.protocol.ancDepthCommand(depth))
            moduleLog("replay: ANC depth sent: $depth")
            pendingAncDepth = null
        }
        pendingTransLevel?.let { level ->
            gatt.sendCommand(gatt.profile.protocol.transLevelCommand(level))
            moduleLog("replay: trans level sent: $level")
            pendingTransLevel = null
        }
        if (pendingAncMode != null || pendingAncDepth != null || pendingTransLevel != null) {
            requestStatusRefresh("replayPendingAncCommands")
        }
    }

    /** 触发 GATT 客户端查询耳机状态（和 OppoPods 的 ACTION_REFRESH_STATUS 一样） */
    private fun requestStatusRefresh(reason: String) {
        val ctx = context ?: return
        ctx.sendBroadcast(
            Intent(HyperRoseAction.REFRESH_STATUS).apply {
                `package` = HyperRoseAction.PACKAGE_BLUETOOTH
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            },
        )
        moduleLog("REFRESH_STATUS sent reason=$reason")
    }

    // ==================== 设备识别 ====================

    private fun isRoseEarphone(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        val address = runCatching { device.address }.getOrNull()
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        val nameMatch = com.dohex.hyperrose.profile.DeviceProfileRegistry.findByName(name) != null
        val addrMatch = address != null && isRoseAddress(address)
        val result = nameMatch || addrMatch
        moduleLog("isRoseEarphone: name='$name' addr=$address nameMatch=$nameMatch addrMatch=$addrMatch known=$knownAddresses → $result")
        if (result && address != null) {
            knownAddresses.add(address.uppercase())
            currentDevice = device
            currentAddress = address
        }
        return result
    }

    private fun isRoseAddress(address: String): Boolean {
        val normalized = address.uppercase()
        // 1. 检查已知地址集合（由 DEVICE_CONNECTED 广播或 isRoseEarphone 名称匹配填充）
        if (normalized in knownAddresses) return true
        // 2. 检查当前缓存的设备地址（可能在收到 DEVICE_CONNECTED 广播前就被 Binder 调用）
        if (normalized == currentAddress?.uppercase()) return true
        // 3. 兜底：尝试从蓝牙适配器获取远程设备名称，匹配关键字
        if (runCatching {
                val bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val device = bt?.getRemoteDevice(address)
                val name = device?.name ?: device?.alias
                name?.let { com.dohex.hyperrose.profile.DeviceProfileRegistry.findByName(it) != null } == true
            }.getOrElse { false }
        ) {
            knownAddresses.add(normalized)
            return true
        }
        // 4. 检查用户白名单
        if (com.dohex.hyperrose.ipc.AuthorizedDeviceClient.isAuthorized(address)) {
            knownAddresses.add(normalized)
            return true
        }
        return false
    }

    // ==================== Callback 管理 ====================

    private fun rememberCallback(callback: Any) {
        (runCatching {
            ReflectionHelper.callMethod(
                callback,
                "asBinder"
            )
        }.getOrNull() as? IBinder)?.let {
            callbacks[it] = callback
        }
    }

    private fun forgetCallback(callback: Any) {
        (runCatching {
            ReflectionHelper.callMethod(
                callback,
                "asBinder"
            )
        }.getOrNull() as? IBinder)?.let {
            callbacks.remove(it)
        }
    }

    // ==================== 工具方法 ====================

    private fun fakeSupport(): String = "01010607,000000000000000010000000"

    private fun findMethodByParamTypes(
        clazz: Class<*>,
        name: String,
        vararg paramTypes: Class<*>
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                val method = current.getDeclaredMethod(name, *paramTypes)
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }
        return null
    }

    private fun moduleLog(msg: String) {
        mlog(Log.DEBUG, msg)
    }
}
