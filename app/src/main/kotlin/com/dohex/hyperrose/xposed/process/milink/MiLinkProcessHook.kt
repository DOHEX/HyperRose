package com.dohex.hyperrose.xposed.process.milink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.dohex.hyperrose.core.reflection.ReflectionHelper
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.ipc.QuickControlIntentFactory
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction

/**
 * com.milink.service 进程的 Hook。
 * 让目标耳机在小米 MxBluetooth SDK 中伪装为原生小米 TWS 耳机，解锁音频流转功能。
 *
 * 核心策略：
 * - hook MxBluetoothManager / MxBluetoothService 的设备查询方法，返回伪装值
 * - hook ANC 控制命令，转发给 Bluetooth 进程执行
 * - 监听 Bluetooth 进程广播，缓存电量/ANC 状态供系统查询
 */
@SuppressLint("MissingPermission")
object MiLinkProcessHook {
    private const val LOG_TAG = "HyperRose-MiLink"

    /** 伪装的设备 ID（小米耳机型号编码），需与 BluetoothUpstreamHeadsetHook 保持一致 */
    private const val FAKE_DEVICE_ID = "01010607"

    // MxBluetooth SDK 类名候选（不同 HyperOS 版本可能不同）
    private val MX_MANAGER_CLASSES = listOf(
        "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
        "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
    )

    // 运行时耳机 UI 类
    private const val PROFILE_CONTEXT_CLASS = "com.miui.headset.runtime.ProfileContext"
    private const val ANC_BATTERY_CONTROLLER_CLASS = "com.miui.headset.runtime.AncBatteryController"
    private const val HEADSET_INFO_CLASS = "com.miui.headset.api.HeadsetInfo"

    // 状态缓存
    private var module: XposedModule? = null
    private var context: Context? = null
    private var receiverRegistered = false
    private var currentAddress: String? = null
    private var currentName: String? = null
    private var currentLeftBattery = -1
    private var currentRightBattery = -1
    private var currentCaseBattery = -1
    private var currentLeftCharging = false
    private var currentRightCharging = false
    private var currentAncMode: AncMode? = null

    /** LSPosed 日志（自动回退到 Log） */
    private fun mlog(level: Int, msg: String) {
        module?.log(level, LOG_TAG, msg) ?: Log.println(level, LOG_TAG, msg)
    }

    fun init(
        module: XposedModule,
        param: PackageLoadedParam,
    ) {
        this.module = module
        val cl = param.defaultClassLoader

        // 加载白名单（从 App 进程 ContentProvider 查询）
        runCatching {
            val clazz = Class.forName("android.app.ActivityThread")
            val appCtx = clazz.getMethod("currentApplication").invoke(null) as? android.content.Context
            if (appCtx != null) com.dohex.hyperrose.ipc.AuthorizedDeviceClient.ensureLoaded(appCtx)
        }

        // 先 hook 初始化方法捕获 Context（必须在注册广播接收器之前）
        hookContextEntry(module, cl)
        hookMxBluetoothMethods(module, cl)
        hookHeadsetRuntimeDisplay(module, cl)
        hookHeadsetInfo(module, cl)
        hookMoreSettingsButton(module, cl)

        module.log(Log.INFO, LOG_TAG, "MiLinkProcessHook initialized")
    }

    // ==================== Context 捕获 ====================

    /**
     * hook MxBluetoothManager/MxBluetoothService 的 getInstanceForIsMiTWS(Context) 方法，
     * 在系统首次调用时捕获 Context 并注册广播接收器。
     */
    private fun hookContextEntry(module: XposedModule, cl: ClassLoader) {
        MX_MANAGER_CLASSES.forEach { className ->
            runCatching {
                val clazz = cl.loadClass(className)
                val method = findMethodByParamTypes(clazz, "getInstanceForIsMiTWS", Context::class.java)
                    ?: return@runCatching

                module.hook(method)?.intercept { chain ->
                    val ctx = chain.getArg(0) as? Context
                    if (ctx != null && context == null) {
                        context = ctx.applicationContext ?: ctx
                        registerStateReceiver(module)
                        module.log(Log.INFO, LOG_TAG, "Context captured from $className.getInstanceForIsMiTWS")
                    }
                    chain.proceed()
                }
            }.onFailure { module.log(Log.WARN, LOG_TAG, "Context hook skipped for $className", it) }
        }
    }

    // ==================== MxBluetoothManager / MxBluetoothService ====================

    private fun hookMxBluetoothMethods(module: XposedModule, cl: ClassLoader) {
        MX_MANAGER_CLASSES.forEach { className ->
            val clazz = runCatching { cl.loadClass(className) }.getOrNull() ?: return@forEach

            // 设备身份伪装
            hookDeviceResult(module, clazz, "checkIsMiTWS") { 1 }
            hookDeviceResult(module, clazz, "getDeviceId") { FAKE_DEVICE_ID }
            hookDeviceResult(module, clazz, "getBatteryLevel") { buildMiLinkBatteryList() }
            hookDeviceResult(module, clazz, "getAncState") { miLinkAncState() }
            hookDeviceResult(module, clazz, "getDeviceRunInfo") { 0 }
            hookDeviceResult(module, clazz, "getSpatialMode") { 0 }
            hookDeviceResult(module, clazz, "getWearStatus") { "0,0" }
            hookDeviceResult(module, clazz, "isLeAudio") { false }

            // String address 参数的方法
            hookStringAddressResult(module, clazz, "isMiTWS") { true }
            hookStringAddressResult(module, clazz, "isSupportAudioSwitch") { 1 }
            hookStringAddressResult(module, clazz, "getRingFindState") { false }

            // ANC 命令拦截
            hookAncCommand(module, clazz, "openAnc", AncMode.NOISE_CANCEL)
            hookAncCommand(module, clazz, "closeAnc", AncMode.NORMAL)
            hookAncCommand(module, clazz, "openTransparent", AncMode.TRANSPARENT)

            module.log(Log.INFO, LOG_TAG, "Hooked MxBluetooth methods on $className")
        }
    }

    // ==================== com.miui.headset.runtime 显示层 ====================

    private fun hookHeadsetRuntimeDisplay(module: XposedModule, cl: ClassLoader) {
        // ProfileContext
        runCatching {
            val clazz = cl.loadClass(PROFILE_CONTEXT_CLASS)
            hookDeviceResult(module, clazz, "getDeviceId") { FAKE_DEVICE_ID }
            hookDeviceResult(module, clazz, "getBatteryLevel") { buildMiLinkBatteryList() }
        }.onFailure { module.log(Log.WARN, LOG_TAG, "ProfileContext hook skipped", it) }

        // AncBatteryController
        runCatching {
            val clazz = cl.loadClass(ANC_BATTERY_CONTROLLER_CLASS)
            hookDeviceResult(module, clazz, "getDeviceId") { FAKE_DEVICE_ID }
            hookDeviceResult(module, clazz, "getAncState") { miLinkAncState() }
            hookDeviceResult(module, clazz, "getBatteryLevelCache") { buildMiLinkBatteryList() }
            hookDeviceResult(module, clazz, "getHeadsetPropertyBlock") { batteryPercentForMiLink() }
            hookAncStateBlock(module, clazz)
        }.onFailure { module.log(Log.WARN, LOG_TAG, "AncBatteryController hook skipped", it) }
    }

    private fun hookHeadsetInfo(module: XposedModule, cl: ClassLoader) {
        runCatching {
            val clazz = cl.loadClass(HEADSET_INFO_CLASS)
            // 无参方法 — 通过参数数量匹配
            hookNoArgResult(module, clazz, "getDeviceId") { FAKE_DEVICE_ID }
            hookNoArgResult(module, clazz, "component3") { FAKE_DEVICE_ID }
            hookNoArgResult(module, clazz, "getPowers") { buildMiLinkBatteryList() }
            hookNoArgResult(module, clazz, "component4") { buildMiLinkBatteryList() }
            hookNoArgResult(module, clazz, "getMode") { miLinkAncState() }
            hookNoArgResult(module, clazz, "component5") { miLinkAncState() }
        }.onFailure { module.log(Log.WARN, LOG_TAG, "HeadsetInfo hook skipped", it) }
    }

    // ==================== 更多设置按钮重定向 ====================

    private fun hookMoreSettingsButton(module: XposedModule, cl: ClassLoader) {
        MX_MANAGER_CLASSES.forEach { className ->
            runCatching {
                val clazz = cl.loadClass(className)
                val method = findMethodByParamTypes(clazz, "switchToHeadsetActivity", BluetoothDevice::class.java)
                    ?: return@runCatching

                module.hook(method)?.intercept { chain ->
                    val device = chain.getArg(0) as? BluetoothDevice
                    if (device == null || !isRoseEarphone(device)) {
                        return@intercept chain.proceed()
                    }

                    val ctx = resolveContext(chain.thisObject) ?: return@intercept chain.proceed()
                    val intent = QuickControlIntentFactory.createLaunchIntent(deviceName = device.name)
                    runCatching { ctx.startActivity(intent) }

                    module.log(Log.INFO, LOG_TAG, "switchToHeadsetActivity redirected to HyperRose")
                    null // 阻止原始调用
                }
                module.log(Log.INFO, LOG_TAG, "Hooked $className.switchToHeadsetActivity")
            }.onFailure { module.log(Log.WARN, LOG_TAG, "switchToHeadsetActivity hook skipped for $className", it) }
        }
    }

    // ==================== AncStateBlock 拦截（系统 UI ANC 按钮） ====================

    private fun hookAncStateBlock(module: XposedModule, clazz: Class<*>) {
        runCatching {
            val method = findMethodByParamTypes(
                clazz, "setAncStateBlock", BluetoothDevice::class.java, Int::class.javaPrimitiveType!!,
            ) ?: return

            module.hook(method)?.intercept { chain ->
                val device = chain.getArg(0) as? BluetoothDevice
                if (device == null || !isRoseEarphone(device)) {
                    return@intercept chain.proceed()
                }

                val miLinkMode = chain.getArg(1) as? Int ?: return@intercept chain.proceed()
                val roseAnc = roseAncFromMiLink(miLinkMode)

                // 从 AncBatteryController 实例拿 Context（关键！OppoPods 也这样做）
                // getInstanceForIsMiTWS hook 可能永远不会触发，必须从实例兜底
                captureRuntimeContext(chain.thisObject)
                val instanceContext = runCatching {
                    ReflectionHelper.getField(chain.thisObject, "context") as? Context
                }.getOrNull()?.let { it.applicationContext ?: it }

                // 乐观更新 ANC 状态缓存（关键！控制中心通过 getAncState() 读取此值来更新 UI）
                // OppoPods: currentAnc = oppoAnc; this.result = miLinkAncState()
                currentAncMode = roseAnc

                // 立即广播 ANC_CHANGED（不等耳机回报！OppoPods 的 sendAncChanged 也是这样做）
                // 让 Bluetooth 进程的 HeadsetServiceBinderHook 立即通过 callback 推送给控制中心
                sendAncChanged(roseAnc, instanceContext)

                // 转发 ANC 命令到 Bluetooth 进程（传入 instanceContext 作为兜底）
                sendAncToBluetooth(roseAnc, instanceContext)

                // 通知系统 UI 刷新
                notifyHeadsetPropertyChanged(chain.thisObject, device, 8)
                notifyHeadsetPropertyChanged(chain.thisObject, device, 4)

                mlog(Log.WARN, ">>> setAncStateBlock: miLinkMode=$miLinkMode → roseAnc=$roseAnc ancState=${miLinkAncState()} ctx=${instanceContext != null}")
                return@intercept miLinkAncState()
            }
            mlog(Log.WARN, "Hooked AncBatteryController.setAncStateBlock")
        }.onFailure { module.log(Log.WARN, LOG_TAG, "setAncStateBlock hook skipped", it) }
    }

    // ==================== 广播接收器（状态同步） ====================

    private fun registerStateReceiver(module: XposedModule) {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(HyperRoseAction.DEVICE_CONNECTED)
            addAction(HyperRoseAction.DEVICE_DISCONNECTED)
            addAction(HyperRoseAction.BATTERY_CHANGED)
            addAction(HyperRoseAction.ANC_CHANGED)
            addAction(HyperRoseAction.ANC_DEPTH_CHANGED)
            addAction(HyperRoseAction.TRANS_LEVEL_CHANGED)
        }

        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    HyperRoseAction.DEVICE_CONNECTED -> {
                        currentAddress = intent.getParcelableExtra<BluetoothDevice>(HyperRoseAction.EXTRA_DEVICE)?.address
                        currentName = intent.getParcelableExtra<BluetoothDevice>(HyperRoseAction.EXTRA_DEVICE)?.name
                    }
                    HyperRoseAction.DEVICE_DISCONNECTED -> {
                        currentAddress = null
                        currentName = null
                        currentLeftBattery = -1
                        currentRightBattery = -1
                        currentCaseBattery = -1
                        currentLeftCharging = false
                        currentRightCharging = false
                        currentAncMode = null
                    }
                    HyperRoseAction.BATTERY_CHANGED -> {
                        currentLeftBattery = intent.getIntExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, -1)
                        currentRightBattery = intent.getIntExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, -1)
                        currentCaseBattery = intent.getIntExtra(HyperRoseAction.EXTRA_CASE_LEVEL, -1)
                        currentLeftCharging = intent.getBooleanExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, false)
                        currentRightCharging = intent.getBooleanExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, false)
                    }
                    HyperRoseAction.ANC_CHANGED -> {
                        currentAncMode = intent.getStringExtra(HyperRoseAction.EXTRA_MODE)?.let { name ->
                            runCatching { AncMode.valueOf(name) }.getOrNull()
                        }
                    }
                }
                module.log(
                    Log.DEBUG, LOG_TAG,
                    "State updated: addr=$currentAddress L=$currentLeftBattery R=$currentRightBattery " +
                        "C=$currentCaseBattery anc=$currentAncMode",
                )
            }
        }, filter, Context.RECEIVER_EXPORTED)

        receiverRegistered = true

        // 请求 Bluetooth 进程刷新状态（跨进程广播，必须带 FLAG_RECEIVER_FOREGROUND）
        context?.sendBroadcast(
            Intent(HyperRoseAction.REFRESH_STATUS).apply {
                setPackage(HyperRoseAction.PACKAGE_BLUETOOTH)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            },
        )

        module.log(Log.INFO, LOG_TAG, "State receiver registered")
    }

    // ==================== Hook 辅助方法 ====================

    /** hook BluetoothDevice 参数的方法，替换返回值 */
    private fun hookDeviceResult(
        module: XposedModule,
        clazz: Class<*>,
        methodName: String,
        result: () -> Any?,
    ) {
        runCatching {
            val method = findMethodByParamTypes(clazz, methodName, BluetoothDevice::class.java) ?: return
            module.hook(method)?.intercept { chain ->
                val device = chain.getArg(0) as? BluetoothDevice
                if (device != null && isRoseEarphone(device)) {
                    module.log(Log.DEBUG, LOG_TAG, "${clazz.simpleName}.$methodName → ${result()}")
                    return@intercept result()
                }
                chain.proceed()
            }
        }.onFailure { module.log(Log.WARN, LOG_TAG, "Hook ${clazz.simpleName}.$methodName skipped", it) }
    }

    /** hook String address 参数的方法，替换返回值 */
    private fun hookStringAddressResult(
        module: XposedModule,
        clazz: Class<*>,
        methodName: String,
        result: () -> Any?,
    ) {
        runCatching {
            val method = findMethodByParamTypes(clazz, methodName, String::class.java) ?: return
            module.hook(method)?.intercept { chain ->
                val address = chain.getArg(0) as? String
                if (address != null && isRoseAddress(address)) {
                    module.log(Log.DEBUG, LOG_TAG, "${clazz.simpleName}.$methodName(addr) → ${result()}")
                    return@intercept result()
                }
                chain.proceed()
            }
        }.onFailure { module.log(Log.WARN, LOG_TAG, "Hook ${clazz.simpleName}.$methodName(String) skipped", it) }
    }

    /** hook 无参方法，替换返回值 */
    private fun hookNoArgResult(
        module: XposedModule,
        clazz: Class<*>,
        methodName: String,
        result: () -> Any?,
    ) {
        runCatching {
            val method = findMethodByParamCount(clazz, methodName, 0) ?: return
            module.hook(method)?.intercept { chain ->
                // 通过 this 对象判断是否目标设备
                val instance = chain.thisObject
                if (isTargetHeadsetInfo(instance)) {
                    module.log(Log.DEBUG, LOG_TAG, "${clazz.simpleName}.$methodName() → ${result()}")
                    return@intercept result()
                }
                chain.proceed()
            }
        }.onFailure { module.log(Log.WARN, LOG_TAG, "Hook ${clazz.simpleName}.$methodName() skipped", it) }
    }

    /** hook ANC 命令方法，拦截并转发 */
    private fun hookAncCommand(
        moduleRef: XposedModule,
        clazz: Class<*>,
        methodName: String,
        roseAnc: AncMode?,
    ) {
        runCatching {
            val method = findMethodByParamTypes(clazz, methodName, BluetoothDevice::class.java)
            if (method == null) {
                mlog(Log.WARN, "!!! hookAncCommand: ${clazz.simpleName}.$methodName(BluetoothDevice) NOT FOUND — hook NOT installed")
                return
            }
            moduleRef.hook(method)?.intercept { chain ->
                val device = chain.getArg(0) as? BluetoothDevice
                if (device != null && isRoseEarphone(device)) {
                    // 从 MxBluetoothManager/MxBluetoothService 实例拿 Context（兜底）
                    captureRuntimeContext(chain.thisObject)
                    val instanceContext = runCatching {
                        ReflectionHelper.getField(chain.thisObject, "context") as? Context
                    }.getOrNull()?.let { it.applicationContext ?: it }

                    // 乐观更新 ANC 状态缓存（控制中心通过 getAncState() 读取此值）
                    if (roseAnc != null) currentAncMode = roseAnc
                    // 立即广播 ANC_CHANGED，让控制中心瞬间更新（不等耳机回报）
                    sendAncChanged(roseAnc, instanceContext)
                    sendAncToBluetooth(roseAnc, instanceContext)
                    mlog(Log.WARN, ">>> hookAncCommand: $methodName intercepted → roseAnc=$roseAnc ancState=${miLinkAncState()} ctx=${instanceContext != null}")
                    return@intercept miLinkAncState()
                }
                chain.proceed()
            }
            mlog(Log.WARN, "hookAncCommand: ${clazz.simpleName}.$methodName hook INSTALLED")
        }.onFailure { mlog(Log.ERROR, "!!! hookAncCommand: ${clazz.simpleName}.$methodName FAILED: ${it.message}") }
    }

    // ==================== 设备识别 ====================

    private fun isRoseEarphone(device: BluetoothDevice): Boolean {
        val address = runCatching { device.address }.getOrNull()
        if (address != null && isRoseAddress(address)) return true
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        return com.dohex.hyperrose.domain.DeviceConstants.matchesDeviceName(name)
    }

    private fun isRoseAddress(address: String): Boolean {
        // 1. 检查当前缓存的设备地址
        if (currentAddress != null && address.equals(currentAddress, ignoreCase = true)) return true
        // 2. 兜底：尝试从蓝牙适配器获取远程设备名称
        if (runCatching {
            val bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            val device = bt?.getRemoteDevice(address)
            val name = device?.name ?: device?.alias
            name?.let { com.dohex.hyperrose.domain.DeviceConstants.matchesDeviceName(it) } == true
        }.getOrElse { false }) {
            currentAddress = address
            return true
        }
        // 3. 检查用户白名单
        if (com.dohex.hyperrose.ipc.AuthorizedDeviceClient.isAuthorized(address)) {
            currentAddress = address
            return true
        }
        return false
    }

    private fun isTargetHeadsetInfo(info: Any?): Boolean {
        if (info == null) return false
        listOf("getAddress", "component1").forEach { methodName ->
            val address = runCatching { ReflectionHelper.callMethod(info, methodName) as? String }.getOrNull()
            if (address != null && isRoseAddress(address)) return true
        }
        return false
    }

    // ==================== MiLink 数据格式转换 ====================

    /**
     * MiLink ANC 状态编码：
     * 0 = OFF, 1 = 降噪 (NC), 2 = 通透 (Transparency)
     */
    private fun miLinkAncState(): Int = when (currentAncMode) {
        AncMode.NOISE_CANCEL, AncMode.WIND_NOISE -> 1
        AncMode.TRANSPARENT -> 2
        AncMode.NORMAL, null -> 0
    }

    /** MiLink ANC 模式 → HyperRose AncMode */
    private fun roseAncFromMiLink(miLinkMode: Int): AncMode? = when (miLinkMode) {
        1 -> AncMode.NOISE_CANCEL
        2 -> AncMode.TRANSPARENT
        else -> AncMode.NORMAL
    }

    /**
     * MiLink 电量格式：[box, left, right, boxCharging, leftCharging, rightCharging]
     * 电量 0-100，-1 表示未连接；充电 1=充电中 / 0=未充电
     */
    private fun buildMiLinkBatteryList(): List<Int> = listOf(
        if (currentCaseBattery in 0..100) currentCaseBattery else -1,
        if (currentLeftBattery in 0..100) currentLeftBattery else -1,
        if (currentRightBattery in 0..100) currentRightBattery else -1,
        0, // box charging（暂不支持）
        if (currentLeftCharging) 1 else 0,
        if (currentRightCharging) 1 else 0,
    )

    /** HeadsetPropertyBlock 用的最小电量百分比 */
    private fun batteryPercentForMiLink(): Int {
        val values = listOfNotNull(
            currentLeftBattery.takeIf { it in 0..100 },
            currentRightBattery.takeIf { it in 0..100 },
        )
        return values.minOrNull() ?: 0
    }

    // ==================== IPC 工具 ====================

    /**
     * 从实例对象的 context 字段提取 Context（和 OppoPods 的 captureRuntimeContext 一样）。
     * 关键：getInstanceForIsMiTWS hook 可能永远不会触发，必须从当前实例兜底获取。
     */
    private fun captureRuntimeContext(owner: Any?) {
        val ownerContext = runCatching {
            ReflectionHelper.getField(owner!!, "context") as? Context
        }.getOrNull() ?: return
        context = ownerContext.applicationContext ?: ownerContext
        mlog(Log.WARN, "Context captured from ${owner?.javaClass?.simpleName ?: "unknown"}.context")
    }

    /**
     * 立即广播 ANC 状态变更，不等耳机回报（和 OppoPods 的 sendAncChanged 一样）。
     * 关键：Bluetooth 进程的 HeadsetServiceBinderHook 收到此广播后，
     * 立即通过 IMiuiHeadsetCallback.refreshStatus 推送给控制中心，实现瞬间 UI 更新。
     */
    private fun sendAncChanged(
        roseAnc: AncMode?,
        fallbackContext: Context? = null,
    ) {
        val ctx = fallbackContext ?: context ?: return
        val modeName = roseAnc?.name ?: return
        // 广播给 App、MiLink 自身、Bluetooth 进程（binder hook 在 Bluetooth 进程里）
        listOf(
            HyperRoseAction.PACKAGE_BLUETOOTH,
            HyperRoseAction.PACKAGE_MILINK,
            HyperRoseAction.PACKAGE_APP,
        ).forEach { pkg ->
            ctx.sendBroadcast(
                Intent(HyperRoseAction.ANC_CHANGED).apply {
                    putExtra(HyperRoseAction.EXTRA_MODE, modeName)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
        }
    }

    /** 发送 ANC 命令到 Bluetooth 进程（跨进程广播，必须带 FLAG_RECEIVER_FOREGROUND，与 OppoPods 一致） */
    private fun sendAncToBluetooth(
        roseAnc: AncMode?,
        fallbackContext: Context? = null,
    ) {
        val ctx = fallbackContext ?: context
        if (ctx == null) {
            mlog(Log.ERROR, "!!! sendAncToBluetooth: context is NULL — ANC command DROPPED !!! roseAnc=$roseAnc")
            return
        }
        val targetPkg = HyperRoseAction.PACKAGE_BLUETOOTH
        val modeName = roseAnc?.name ?: "NULL"
        mlog(Log.WARN, ">>> sendAncToBluetooth: sending ANC_SELECT mode=$modeName to $targetPkg")
        try {
            ctx.sendBroadcast(
                Intent(HyperRoseAction.ANC_SELECT).apply {
                    putExtra(HyperRoseAction.EXTRA_MODE, modeName)
                    setPackage(targetPkg)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
            )
            mlog(Log.WARN, "<<< sendAncToBluetooth: broadcast sent successfully mode=$modeName")
        } catch (e: Exception) {
            mlog(Log.ERROR, "!!! sendAncToBluetooth: broadcast FAILED: ${e.message}")
        }
    }

    /** 通知 HeadsetPropertyChangeListener 刷新 */
    private fun notifyHeadsetPropertyChanged(controller: Any?, device: BluetoothDevice, updateType: Int) {
        val listener = runCatching { ReflectionHelper.getField(controller!!, "headsetPropertyChangeListener") }.getOrNull()
            ?: return
        runCatching { ReflectionHelper.callMethod(listener, "invoke", device, updateType) }
    }

    /** 从 Hook 实例获取 Context */
    private fun resolveContext(obj: Any): Context? = runCatching {
        ReflectionHelper.callMethod(obj, "getApplicationContext") as? Context
    }.getOrNull() ?: runCatching {
        ReflectionHelper.getField(obj, "mContext") as? Context
    }.getOrNull()

    // ==================== 反射方法查找 ====================

    private fun findMethodByParamTypes(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): java.lang.reflect.Method? {
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

    private fun findMethodByParamCount(clazz: Class<*>, name: String, paramCount: Int): java.lang.reflect.Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == paramCount }
            if (method != null) {
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }
        return null
    }
}
