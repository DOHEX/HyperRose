package com.dohex.hyperrose.hook

import android.util.Log
import com.dohex.hyperrose.core.reflection.ReflectionHelper
import com.dohex.hyperrose.entry.HyperRoseXposedEntry.Companion.TAG
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * com.android.systemui 进程的 Hook。
 * 获取 miui.systemui.plugin 的 ClassLoader，用于后续 DeviceCardHook。
 */
object SystemUIPluginHook {

    var pluginClassLoader: ClassLoader? = null
        private set

    private var pluginLoadHookInstalled = false

    private val wrapperClassCandidates = listOf(
        "miui.systemui.devicecenter.devices.DeviceInfoWrapper",
        "miui.systemui.controlcenter.panel.main.devicecenter.card.DeviceInfoWrapper"
    )

    fun init(module: XposedModule, param: PackageLoadedParam) {
        val cl = param.getDefaultClassLoader()

        try {
            val hooked = hookPluginInstanceLoadPlugin(module, cl) or hookLegacyFactory(module, cl)
            if (hooked) {
                module.log(Log.INFO, TAG, "SystemUIPluginHook: Hooked successfully")
            } else {
                module.log(Log.WARN, TAG, "SystemUIPluginHook: No suitable method found")
            }
        } catch (e: Throwable) {
            module.log(Log.ERROR, TAG, "SystemUIPluginHook: Failed to hook", e)
        }
    }

    private fun hookPluginInstanceLoadPlugin(module: XposedModule, cl: ClassLoader): Boolean {
        if (pluginLoadHookInstalled) return true

        val pluginInstanceClass = runCatching {
            cl.loadClass("com.android.systemui.shared.plugins.PluginInstance")
        }.getOrNull() ?: return false

        val loadPluginMethod = pluginInstanceClass.declaredMethods.firstOrNull {
            it.name == "loadPlugin"
        } ?: return false

        module.hook(loadPluginMethod).intercept(XposedInterface.Hooker { chain ->
            val result = chain.proceed()
            runCatching {
                val pkgName = runCatching {
                    ReflectionHelper.callMethod(chain.thisObject, "getPackage") as? String
                }.getOrNull()
                module.log(Log.DEBUG, TAG, "SystemUIPluginHook: loadPlugin package=$pkgName")
                if (pkgName == "miui.systemui.plugin") {
                    extractPluginClassLoader(chain.thisObject)?.let {
                        tryInitDeviceCardHook(module, it)
                    }
                }
            }.onFailure {
                module.log(Log.DEBUG, TAG, "SystemUIPluginHook: loadPlugin inspect failed", it)
            }
            result
        })

        pluginLoadHookInstalled = true
        return true
    }

    private fun hookLegacyFactory(module: XposedModule, cl: ClassLoader): Boolean {
        val pluginFactoryClass = runCatching {
            cl.loadClass("com.android.systemui.shared.plugins.PluginInstance\$Factory")
        }.getOrNull() ?: return false

        val methods: Array<Method> = pluginFactoryClass.declaredMethods
        val createMethod = methods.firstOrNull { m ->
            m.name == "create" || m.name == "createPlugin" || m.name == "getClassLoader"
        } ?: return false

        module.hook(createMethod).intercept(XposedInterface.Hooker { chain ->
            val result = chain.proceed()

            if (result is ClassLoader) {
                tryInitDeviceCardHook(module, result)
            } else if (result != null) {
                val resultCl = result.javaClass.classLoader
                if (resultCl != null && resultCl != cl) {
                    tryInitDeviceCardHook(module, resultCl)
                }
            }
            result
        })
        return true
    }

    private fun extractPluginClassLoader(pluginInstance: Any): ClassLoader? {
        val direct = runCatching { pluginInstance.javaClass.classLoader }.getOrNull()
        if (isPluginClassLoader(direct)) return direct

        val pluginFactory = runCatching {
            ReflectionHelper.getField(pluginInstance, "mPluginFactory")
        }.getOrNull() ?: return direct

        val classLoaderFactory = runCatching {
            ReflectionHelper.getField(pluginFactory, "mClassLoaderFactory")
        }.getOrNull()

        val fromFactory: ClassLoader? = if (classLoaderFactory != null) {
            runCatching {
                ReflectionHelper.callMethod(classLoaderFactory, "get") as? ClassLoader
            }.getOrNull()
        } else {
            null
        }

        if (isPluginClassLoader(fromFactory)) return fromFactory
        return direct
    }

    private fun tryInitDeviceCardHook(module: XposedModule, classLoader: ClassLoader) {
        if (!isPluginClassLoader(classLoader)) {
            module.log(Log.DEBUG, TAG, "SystemUIPluginHook: classLoader not plugin")
            return
        }

        val hooked = DeviceCardHook.init(module, classLoader)
        if (!hooked) {
            module.log(Log.DEBUG, TAG, "SystemUIPluginHook: DeviceCardHook not installed")
            return
        }

        if (pluginClassLoader != classLoader) {
            pluginClassLoader = classLoader
            module.log(Log.INFO, TAG, "SystemUIPluginHook: Plugin ClassLoader captured")
        } else {
            module.log(Log.DEBUG, TAG, "SystemUIPluginHook: Plugin ClassLoader unchanged")
        }
    }

    private fun isPluginClassLoader(classLoader: ClassLoader?): Boolean {
        if (classLoader == null) return false
        return wrapperClassCandidates.any { className ->
            runCatching { classLoader.loadClass(className) }.isSuccess
        }
    }
}
