package com.dohex.hyperrose.hook

import android.content.SharedPreferences
import android.util.Log
import com.dohex.hyperrose.ipc.HyperRoseIpc
import io.github.libxposed.api.XposedModule

/**
 * Hook 进程侧白名单读取：远端偏好（framework 侧存储，LSPosed 实时推送变更）。
 *
 * 读取的是 framework 实时更新的内存 map，无需注册变更监听。
 * service 不可用时 [prefs] 为 null，白名单判定全部返回 false（fail-safe）。
 */
object RemoteDeviceWhitelist {
    private const val TAG = "RemoteDeviceWhitelist"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 幂等；每个 hook 进程初始化一次即可。 */
    fun init(module: XposedModule) {
        if (prefs != null) return
        prefs =
            runCatching {
                module.getRemotePreferences(HyperRoseIpc.REMOTE_PREFS_GROUP)
            }.onFailure { Log.w(TAG, "getRemotePreferences failed", it) }
                .getOrNull()
    }

    fun isAuthorized(address: String): Boolean =
        prefs?.getStringSet(HyperRoseIpc.REMOTE_PREFS_KEY_ADDRESSES, emptySet())
            ?.any { it.equals(address, ignoreCase = true) }
            ?: false

    fun isNormalToWindEnabled(): Boolean =
        prefs?.getBoolean(HyperRoseIpc.REMOTE_PREFS_KEY_NORMAL_TO_WIND, false)
            ?: false
}
