package com.dohex.hyperrose.data

import android.util.Log
import com.dohex.hyperrose.ipc.HookStatusProvider
import com.dohex.hyperrose.ipc.HyperRoseIpc

/**
 * 白名单写入：远端偏好（libxposed service 同步到 hook 进程）。
 *
 * 写路径 fail-open：service 不可用时静默跳过（写入丢失并记日志），不阻塞 UI 流程。
 * 远端偏好是唯一真相源，不再保留本地副本。
 */
object RemoteDeviceStore {
    private const val TAG = "RemoteDeviceStore"

    private fun addressesOf(): Set<String> =
        HookStatusProvider.remotePreferences()
            ?.getStringSet(HyperRoseIpc.REMOTE_PREFS_KEY_ADDRESSES, emptySet())
            ?: emptySet()

    fun add(address: String) {
        val prefs = HookStatusProvider.remotePreferences() ?: return
        val normalized = address.uppercase()
        val committed = runCatching {
            prefs.edit()
                .putStringSet(HyperRoseIpc.REMOTE_PREFS_KEY_ADDRESSES, addressesOf() + normalized)
                .commit()
        }.getOrDefault(false)
        if (!committed) {
            Log.w(TAG, "add commit failed (hook service unavailable?)")
        }
    }

    fun remove(address: String) {
        val prefs = HookStatusProvider.remotePreferences() ?: return
        val normalized = address.uppercase()
        val committed = runCatching {
            prefs.edit()
                .putStringSet(HyperRoseIpc.REMOTE_PREFS_KEY_ADDRESSES, addressesOf() - normalized)
                .commit()
        }.getOrDefault(false)
        if (!committed) {
            Log.w(TAG, "remove commit failed (hook service unavailable?)")
        }
    }

    fun setNormalToWind(enabled: Boolean) {
        val prefs = HookStatusProvider.remotePreferences() ?: return
        val committed = runCatching {
            prefs.edit()
                .putBoolean(HyperRoseIpc.REMOTE_PREFS_KEY_NORMAL_TO_WIND, enabled)
                .commit()
        }.getOrDefault(false)
        if (!committed) {
            Log.w(TAG, "setNormalToWind commit failed (hook service unavailable?)")
        }
    }
}
