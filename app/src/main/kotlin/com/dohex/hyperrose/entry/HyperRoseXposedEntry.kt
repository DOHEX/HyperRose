package com.dohex.hyperrose.entry

import android.util.Log
import com.dohex.hyperrose.hook.BluetoothHook
import com.dohex.hyperrose.hook.MiBtNotificationHook
import com.dohex.hyperrose.hook.SystemUIPluginHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * HyperRose Xposed 模块入口。
 * 使用 libxposed API 101，extends XposedModule。
 *
 * - 不在构造函数中做初始化工作
 * - onPackageLoaded() 在目标进程默认 classloader 就绪后调用
 * - hook 使用 interceptor-chain 模型
 */
class HyperRoseXposedEntry : XposedModule() {

    companion object {
        const val TAG = "HyperRose"
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        val pkg = param.getPackageName()
        log(Log.INFO, TAG, "onPackageLoaded: $pkg")

        try {
            when (pkg) {
                "com.android.bluetooth" -> {
                    log(Log.INFO, TAG, "Initializing BluetoothHook")
                    BluetoothHook.init(this, param)
                }
                "com.xiaomi.bluetooth" -> {
                    log(Log.INFO, TAG, "Initializing MiBtNotificationHook")
                    MiBtNotificationHook.init(this, param)
                }
                "com.android.systemui" -> {
                    log(Log.INFO, TAG, "Initializing SystemUIPluginHook")
                    SystemUIPluginHook.init(this, param)
                }
            }
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Failed to initialize hook for $pkg", e)
        }
    }
}
