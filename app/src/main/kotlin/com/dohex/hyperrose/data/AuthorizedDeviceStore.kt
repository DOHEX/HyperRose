package com.dohex.hyperrose.data

import android.content.Context
import android.content.SharedPreferences
import com.dohex.hyperrose.ipc.AuthorizedDeviceProvider

/**
 * 已授权设备白名单持久化。
 *
 * 用户在设备选择器中手动选择的设备会加入此白名单，
 * 后续连接时即使设备名不匹配关键字也能被识别。
 */
object AuthorizedDeviceStore {

    private const val PREFS_NAME = "authorized_devices"
    private const val KEY_ADDRESSES = "addresses"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ADDRESSES, emptySet()) ?: emptySet()

    fun contains(context: Context, address: String): Boolean =
        getAll(context).any { it.equals(address, ignoreCase = true) }

    fun add(context: Context, address: String) {
        val normalized = address.uppercase()
        prefs(context).edit()
            .putStringSet(KEY_ADDRESSES, getAll(context) + normalized)
            .apply()
        AuthorizedDeviceProvider.refresh(context)
    }

    fun remove(context: Context, address: String) {
        val normalized = address.uppercase()
        prefs(context).edit()
            .putStringSet(KEY_ADDRESSES, getAll(context) - normalized)
            .apply()
        AuthorizedDeviceProvider.refresh(context)
    }
}
