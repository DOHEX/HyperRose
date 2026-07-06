package com.dohex.hyperrose.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.net.toUri

/**
 * Hook 进程侧的白名单客户端。
 *
 * 通过 ContentProvider 查询已授权设备列表并缓存在内存中，
 * 监听 [HyperRoseIpc.WHITELIST_CHANGED] 广播刷新缓存。
 */
object AuthorizedDeviceClient {

    private val authorizedAddresses = mutableSetOf<String>()
    private var receiverRegistered = false

    /** 加载白名单（首次调用时从 ContentProvider 查询） */
    fun ensureLoaded(context: Context) {
        if (authorizedAddresses.isNotEmpty() || receiverRegistered) return
        reload(context)
        registerReceiver(context)
    }

    fun isAuthorized(address: String): Boolean =
        authorizedAddresses.any { it.equals(address, ignoreCase = true) }

    private fun reload(context: Context) {
        authorizedAddresses.clear()
        runCatching {
            context.contentResolver.query(
                "content://${AuthorizedDeviceProvider.AUTHORITY}".toUri(),
                arrayOf(AuthorizedDeviceProvider.COLUMN_ADDRESS),
                null, null, null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(AuthorizedDeviceProvider.COLUMN_ADDRESS)
                while (cursor.moveToNext()) {
                    cursor.getString(idx)?.let { authorizedAddresses.add(it.uppercase()) }
                }
            }
        }
    }

    private fun registerReceiver(context: Context) {
        if (receiverRegistered) return
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    reload(ctx)
                }
            },
            IntentFilter(HyperRoseIpc.WHITELIST_CHANGED),
            Context.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
    }
}
