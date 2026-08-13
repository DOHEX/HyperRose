package com.dohex.hyperrose.hook

import android.util.Log
import com.dohex.hyperrose.ipc.HyperRoseIpc
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * HyperRose Xposed 模块入口。 使用 libxposed API 102，extends XposedModule。
 * - 不在构造函数中做初始化工作
 * - onPackageLoaded() 在目标进程默认 classloader 就绪后调用
 * - hook 使用 interceptor-chain 模型
 * - 支持热重载（autoHotReload）：更新模块 App 后由框架自动触发，
 *   onHotReloading 在旧代码中清理，onHotReloaded 在新代码中重新安装 hook
 */
class HyperRoseModuleEntry : XposedModule() {
    companion object {
        const val TAG = "HyperRose"
    }

    private var loadedPackage: String? = null

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        val pkg = param.packageName
        loadedPackage = pkg
        log(Log.INFO, TAG, "onPackageLoaded: $pkg")

        try {
            dispatchInit(pkg, param.defaultClassLoader)
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Failed to initialize hook for $pkg", e)
        }
    }

    /**
     * 热重载前回调（旧代码）：清理广播接收器、会话与定时任务，
     * 避免旧 generation 残留引用导致重复接收或 classloader 泄漏。
     */
    override fun onHotReloading(param: HotReloadingParam): Boolean {
        log(Log.INFO, TAG, "onHotReloading: ${loadedPackage ?: "unknown"}")
        try {
            when (loadedPackage) {
                HyperRoseIpc.PACKAGE_BLUETOOTH -> BluetoothProcessHook.shutdown()
                HyperRoseIpc.PACKAGE_MI_BLUETOOTH -> {
                    MiBluetoothFocusIslandHook.shutdown()
                    HeadsetServiceBinderHook.shutdown()
                }

                HyperRoseIpc.PACKAGE_MILINK -> MiLinkProcessHook.shutdown()
            }
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "onHotReloading cleanup failed", e)
        }
        return true
    }

    /**
     * 热重载后回调（新代码）：super 先解绑旧 generation 的 hook handles，
     * 再重新执行进程初始化。HotReloadedParam 只暴露 processName，
     * classloader 取新入口自身的 classloader。
     */
    override fun onHotReloaded(param: HotReloadedParam) {
        super.onHotReloaded(param)
        log(Log.INFO, TAG, "onHotReloaded: process=${param.processName}")
        val pkg = param.processName
        loadedPackage = pkg
        val cl = this.javaClass.classLoader ?: return
        try {
            dispatchInit(pkg, cl)
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Failed to re-initialize hooks after hot reload", e)
        }
    }

    private fun dispatchInit(pkg: String, cl: ClassLoader) {
        when (pkg) {
            HyperRoseIpc.PACKAGE_BLUETOOTH -> {
                BluetoothProcessHook.init(this, cl)
            }

            HyperRoseIpc.PACKAGE_MI_BLUETOOTH -> {
                MiBluetoothFocusIslandHook.init(this, cl)
                HeadsetServiceBinderHook.init(this, cl)
            }

            HyperRoseIpc.PACKAGE_MILINK -> {
                MiLinkProcessHook.init(this, cl)
            }
        }
    }
}
