package com.dohex.hyperrose.ipc

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hook 运行状态。
 */
sealed interface HookStatus {
    /** framework service 不可用：未安装/未启用 LSPosed，或模块未在 LSPosed 中启用。 */
    data object Inactive : HookStatus

    /** 模块已启用，但目标进程尚未注入 hook（作用域未生效或进程未重启）。 */
    data object ScopePending : HookStatus

    /** hook 进程正在运行。 */
    data object Active : HookStatus
}

/**
 * libxposed service 桥接：持有 [XposedService] 实例，暴露 hook 状态与远端偏好访问。
 *
 * 写路径 fail-open：service 不可用时 [remotePreferences] 返回 null，调用方静默跳过，
 * 不阻塞 standalone 直连流程。
 */
object HookStatusProvider {
    private const val TAG = "HookStatusProvider"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var service: XposedService? = null

    private val _status = MutableStateFlow<HookStatus>(HookStatus.Inactive)
    val status: StateFlow<HookStatus> = _status.asStateFlow()

    @Volatile
    private var initialized = false

    /** 注册 service 回调（幂等，App 启动时调用一次）。 */
    fun init() {
        if (initialized) return
        initialized = true
        XposedServiceHelper.registerListener(
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    this@HookStatusProvider.service = service
                    scope.launch { refresh() }
                }

                override fun onServiceDied(service: XposedService) {
                    if (this@HookStatusProvider.service === service) {
                        this@HookStatusProvider.service = null
                    }
                    scope.launch { refresh() }
                }
            },
        )
    }

    /** 远端偏好（service 不可用时为 null，写路径 fail-open）。 */
    fun remotePreferences(): SharedPreferences? =
        service?.let {
            runCatching { it.getRemotePreferences(HyperRoseIpc.REMOTE_PREFS_GROUP) }
                .onFailure { Log.w(TAG, "getRemotePreferences failed", it) }
                .getOrNull()
        }

    /** 刷新状态（binder 调用，放到后台线程执行）。 */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val svc = service
        _status.value =
            when {
                svc == null -> HookStatus.Inactive

                else ->
                    try {
                        if (svc.getRunningTargets().isNotEmpty()) {
                            HookStatus.Active
                        } else {
                            HookStatus.ScopePending
                        }
                    } catch (e: UnsupportedOperationException) {
                        // service API < 102：无法区分作用域状态，视为已激活
                        HookStatus.Active
                    } catch (e: XposedService.ServiceException) {
                        HookStatus.Inactive
                    }
            }
    }
}
