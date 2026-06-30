package com.dohex.hyperrose.hook

import android.util.Log
import com.dohex.hyperrose.hook.BluetoothProcessHook
import com.dohex.hyperrose.hook.HeadsetServiceBinderHook
import com.dohex.hyperrose.hook.MiBluetoothFocusIslandHook
import com.dohex.hyperrose.hook.MiLinkProcessHook
import com.dohex.hyperrose.ipc.HyperRoseIpc
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * HyperRose Xposed 模块入口。 使用 libxposed API 101，extends XposedModule。
 * - 不在构造函数中做初始化工作
 * - onPackageLoaded() 在目标进程默认 classloader 就绪后调用
 * - hook 使用 interceptor-chain 模型
 */
class HyperRoseModuleEntry : XposedModule() {
    companion object {
        const val TAG = "HyperRose"
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        val pkg = param.packageName
        log(Log.INFO, TAG, "onPackageLoaded: $pkg")

        try {
            when (pkg) {
                HyperRoseIpc.PACKAGE_BLUETOOTH -> {
                    log(Log.INFO, TAG, "Initializing BluetoothProcessHook")
                    BluetoothProcessHook.init(this, param)
                }

                HyperRoseIpc.PACKAGE_MI_BLUETOOTH -> {
                    log(Log.INFO, TAG, "Initializing MiBluetoothFocusIslandHook")
                    MiBluetoothFocusIslandHook.init(this, param)
                    log(Log.INFO, TAG, "Initializing HeadsetServiceBinderHook in MiBluetooth")
                    HeadsetServiceBinderHook.init(this, param.defaultClassLoader)
                }

                HyperRoseIpc.PACKAGE_MILINK -> {
                    log(Log.INFO, TAG, "Initializing MiLinkProcessHook")
                    MiLinkProcessHook.init(this, param)
                }
            }
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Failed to initialize hook for $pkg", e)
        }
    }
}
