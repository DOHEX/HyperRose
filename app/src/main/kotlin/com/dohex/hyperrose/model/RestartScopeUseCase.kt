package com.dohex.hyperrose.model

import android.util.Log
import com.dohex.hyperrose.ipc.HyperRoseIpc
import com.topjohnwu.superuser.Shell

private const val TAG = "RestartScopeUseCase"

val ScopePackagesToRestart = HyperRoseIpc.SCOPE_PACKAGES

data class ScopeRestartItemResult(
    val packageName: String,
    val success: Boolean,
    val details: String,
)

fun restartScopePackages(packages: List<String> = ScopePackagesToRestart): List<ScopeRestartItemResult> {
    return try {
        // 先关闭缓存的非 root shell，否则 KSU 授权后重试仍会返回旧的缓存实例
        synchronized(Shell::class.java) {
            Shell.getCachedShell()?.let { cached ->
                if (!cached.isRoot) {
                    Log.d(TAG, "Closing cached non-root shell to force fresh root attempt")
                    cached.close()
                }
            }
            val shell = Shell.getShell()
            if (!shell.isRoot) {
                Log.e(TAG, "Root unavailable or denied — prompt SU manager grant first")
                return packages.map {
                    ScopeRestartItemResult(
                        packageName = it,
                        success = false,
                        details = "root 未授权",
                    )
                }
            }

            packages.map { pkg ->
                val result = Shell.cmd("pkill -f $pkg").exec()
                if (result.isSuccess) {
                    Log.i(TAG, "Restart scope package success: $pkg")
                    ScopeRestartItemResult(
                        packageName = pkg,
                        success = true,
                        details = "pkill 执行成功",
                    )
                } else {
                    val stderr = result.err.joinToString("; ").ifBlank { "-" }
                    Log.w(
                        TAG,
                        "Restart scope package failed: $pkg, code=${result.code}, err=$stderr"
                    )
                    ScopeRestartItemResult(
                        packageName = pkg,
                        success = false,
                        details = "code=${result.code}, err=$stderr",
                    )
                }
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Restart scope failed", t)
        packages.map {
            ScopeRestartItemResult(
                packageName = it,
                success = false,
                details = t.message ?: "unknown",
            )
        }
    }
}
