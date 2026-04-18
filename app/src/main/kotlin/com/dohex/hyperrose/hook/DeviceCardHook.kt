package com.dohex.hyperrose.hook

import android.content.Context
import android.content.Intent
import android.util.Log
import com.dohex.hyperrose.entry.QuickControlActivity
import com.dohex.hyperrose.entry.HyperRoseXposedEntry.Companion.TAG
import com.dohex.hyperrose.util.Reflect
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 控制中心设备卡片点击拦截。
 * 在 MIUI SystemUI plugin ClassLoader 中 hook DeviceInfoWrapper.performClicked()。
 * 如果点击的设备是 ROSE EARFREE，打开自定义 PopupActivity。
 */
object DeviceCardHook {

    private const val DEVICE_NAME_KEYWORD = "ROSE EARFREE"
    private const val EXTRA_DEVICE_NAME = "com.dohex.hyperrose.extra.device_name"
    private const val TARGET_PACKAGE = "com.dohex.hyperrose"
    private const val QUICK_CONTROL_ACTIVITY = "com.dohex.hyperrose.entry.QuickControlActivity"

    private val wrapperClassNames = listOf(
        "miui.systemui.devicecenter.devices.DeviceInfoWrapper",
        "miui.systemui.controlcenter.panel.main.devicecenter.card.DeviceInfoWrapper"
    )

    private val hookedWrapperKeys = mutableSetOf<String>()
    private var panelHookClassLoaderId: Int? = null
    private var panelController: Any? = null

    fun init(module: XposedModule, pluginCl: ClassLoader): Boolean {
        val clId = System.identityHashCode(pluginCl)
        var hooked = false

        hookPanelController(module, pluginCl, clId)

        wrapperClassNames.forEach { className ->
            val hookKey = "$clId:$className"
            if (hookKey in hookedWrapperKeys) {
                hooked = true
                return@forEach
            }
            val wrapperClass = runCatching { pluginCl.loadClass(className) }.getOrNull() ?: return@forEach
            if (hookClickMethod(module, wrapperClass)) {
                hookedWrapperKeys += hookKey
                hooked = true
                module.log(Log.INFO, TAG, "DeviceCardHook: Hooked $className")
            }
        }

        if (!hooked) {
            module.log(Log.WARN, TAG, "DeviceCardHook: No compatible DeviceInfoWrapper found")
        }
        return hooked
    }

    private fun hookPanelController(module: XposedModule, pluginCl: ClassLoader, clId: Int) {
        if (panelHookClassLoaderId == clId) return

        val panelClass = runCatching {
            pluginCl.loadClass("miui.systemui.controlcenter.panel.main.MainPanelController")
        }.getOrNull() ?: return

        val onCreateMethod = panelClass.declaredMethods.firstOrNull { it.name == "onCreate" } ?: return
        module.hook(onCreateMethod).intercept(XposedInterface.Hooker { chain ->
            val result = chain.proceed()
            panelController = chain.thisObject
            result
        })
        panelHookClassLoaderId = clId
        module.log(Log.INFO, TAG, "DeviceCardHook: MainPanelController hook installed")
    }

    private fun hookClickMethod(module: XposedModule, wrapperClass: Class<*>): Boolean {
        val clickMethod = findClickMethod(wrapperClass) ?: return false

        module.hook(clickMethod).intercept(XposedInterface.Hooker { chain ->
            val result = runCatching {
                val wrapperObj = chain.thisObject
                val context = (chain.getArg(0) as? Context)
                    ?: readContext(wrapperObj, "mContext")
                    ?: readContext(wrapperObj, "context")
                    ?: readContext(wrapperObj, "mHostContext")
                if (context == null) return@runCatching chain.proceed()

                val deviceInfo = resolveDeviceInfo(wrapperObj)
                val deviceType = readString(deviceInfo, "getDeviceType", "deviceType", "mDeviceType")
                val deviceId = readString(deviceInfo, "getId", "id", "mId")
                val deviceName =
                    readString(deviceInfo, "getName", "name", "mName")
                        ?: readString(wrapperObj, "getName", "name", "mDeviceName")

                module.log(
                    Log.DEBUG,
                    TAG,
                    "DeviceCardHook: click type=$deviceType id=$deviceId name=$deviceName"
                )

                if (!isTargetCard(deviceType, deviceName)) {
                    module.log(Log.DEBUG, TAG, "DeviceCardHook: skip non-target card")
                    return@runCatching chain.proceed()
                }

                if (!startQuickControl(context, deviceName)) {
                    module.log(Log.WARN, TAG, "DeviceCardHook: start quick control failed")
                    return@runCatching chain.proceed()
                }

                module.log(Log.INFO, TAG, "DeviceCardHook: opened quick control for $deviceName")
                hideControlCenterPanel(module)
                null
            }.getOrElse {
                module.log(Log.WARN, TAG, "DeviceCardHook: click intercept failed", it)
                chain.proceed()
            }

            result
        })

        return true
    }

    private fun findClickMethod(wrapperClass: Class<*>): Method? {
        val methods = wrapperClass.declaredMethods
        return methods.firstOrNull {
            it.name == "performClicked" &&
                it.parameterCount == 1 &&
                Context::class.java.isAssignableFrom(it.parameterTypes[0])
        }
            ?: methods.firstOrNull { it.name == "performClicked" }
            ?: methods.firstOrNull { it.name == "onClick" }
    }

    private fun resolveDeviceInfo(wrapperObj: Any): Any? {
        return runCatching { Reflect.callMethod(wrapperObj, "getDeviceInfo") }.getOrNull()
            ?: runCatching { Reflect.getField(wrapperObj, "mDeviceInfo") }.getOrNull()
            ?: runCatching { Reflect.getField(wrapperObj, "deviceInfo") }.getOrNull()
    }

    private fun readContext(target: Any, fieldName: String): Context? {
        return runCatching { Reflect.getField(target, fieldName) as? Context }.getOrNull()
    }

    private fun readString(target: Any?, methodName: String, vararg fieldNames: String): String? {
        if (target == null) return null

        val fromMethod = runCatching { Reflect.callMethod(target, methodName) as? String }.getOrNull()
        if (!fromMethod.isNullOrBlank()) return fromMethod

        fieldNames.forEach { fieldName ->
            val value = runCatching { Reflect.getField(target, fieldName) as? String }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun isTargetCard(deviceType: String?, deviceName: String?): Boolean {
        val isThirdHeadset = deviceType?.equals("third_headset", ignoreCase = true) == true
        if (!deviceName.isNullOrBlank()) {
            return deviceName.contains(DEVICE_NAME_KEYWORD, ignoreCase = true)
        }
        return isThirdHeadset
    }

    private fun startQuickControl(context: Context, deviceName: String?): Boolean {
        return runCatching {
            val intent = Intent().apply {
                setClassName(TARGET_PACKAGE, QUICK_CONTROL_ACTIVITY)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(QuickControlActivity.EXTRA_FORCE_CONNECTED, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
        }.isSuccess
    }

    private fun hideControlCenterPanel(module: XposedModule) {
        val panel = panelController ?: return
        runCatching { Reflect.callMethod(panel, "exitOrHide") }
            .onFailure {
                module.log(Log.DEBUG, TAG, "DeviceCardHook: exitOrHide not available", it)
            }
    }
}
