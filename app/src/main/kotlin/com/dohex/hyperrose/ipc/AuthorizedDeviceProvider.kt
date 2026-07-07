package com.dohex.hyperrose.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import com.dohex.hyperrose.data.AuthorizedDeviceStore
import com.dohex.hyperrose.ipc.AuthorizedDeviceProvider.Companion.AUTHORITY
import com.dohex.hyperrose.ipc.AuthorizedDeviceProvider.Companion.COLUMN_ADDRESS

/**
 * 跨进程白名单查询接口。
 *
 * Hook 进程（com.android.bluetooth 等）通过 ContentResolver 查询已授权设备列表，
 * 解决 SharedPreferences 无法跨进程访问的问题。
 *
 * authority: [AUTHORITY]
 * 返回列: [COLUMN_ADDRESS]
 */
class AuthorizedDeviceProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.dohex.hyperrose.authorized_devices"
        const val COLUMN_ADDRESS = "address"

        private var cachedAddresses: Set<String> = emptySet()

        /** App 进程调用：更新内存缓存并通知 Hook 进程 */
        fun refresh(context: Context) {
            cachedAddresses = AuthorizedDeviceStore.getAll(context)
            context.sendBroadcast(
                Intent(HyperRoseIpc.WHITELIST_CHANGED).apply {
                    setPackage(HyperRoseIpc.PACKAGE_BLUETOOTH)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
            )
        }
    }

    override fun onCreate(): Boolean {
        context?.let { cachedAddresses = AuthorizedDeviceStore.getAll(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (!isCallerAuthorized()) return MatrixCursor(arrayOf(COLUMN_ADDRESS))

        val cursor = MatrixCursor(arrayOf(COLUMN_ADDRESS))
        cachedAddresses.forEach { cursor.addRow(arrayOf(it)) }
        return cursor
    }

    private fun isCallerAuthorized(): Boolean {
        val callerUid = Binder.getCallingUid()
        if (callerUid == android.os.Process.myUid()) return true
        val packages = context?.packageManager?.getPackagesForUid(callerUid) ?: return false
        return packages.any { it in HyperRoseIpc.SCOPE_PACKAGES || it == HyperRoseIpc.PACKAGE_APP }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.$AUTHORITY.address"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
