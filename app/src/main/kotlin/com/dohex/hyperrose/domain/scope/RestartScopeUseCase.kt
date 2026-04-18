package com.dohex.hyperrose.domain.scope

import android.util.Log
import com.dohex.hyperrose.ipc.HyperRoseIpc
import com.topjohnwu.superuser.Shell

private const val TAG = "RestartScopeUseCase"

val ScopePackagesToRestart = listOf(
    HyperRoseIpc.PACKAGE_BLUETOOTH,
    HyperRoseIpc.PACKAGE_MI_BLUETOOTH,
    HyperRoseIpc.PACKAGE_SYSTEM_UI
)

data class ScopeRestartItemResult(
    val packageName: String,
    val success: Boolean,
    val details: String
)

fun restartScopePackages(packages: List<String> = ScopePackagesToRestart): List<ScopeRestartItemResult> {
    return try {
        val shell = Shell.getShell()
        if (!shell.isRoot) {
            Log.e(TAG, "Root unavailable or denied")
            return packages.map {
                ScopeRestartItemResult(
                    packageName = it,
                    success = false,
                    details = "root 未授权"
                )
            }
        }

        packages.map { pkg ->
            val result = Shell.cmd("pkill -f $pkg").exec()
            val stderr = result.err.joinToString("; ").ifBlank { "-" }

            if (result.isSuccess) {
                Log.i(TAG, "Restart scope package success: $pkg")
                ScopeRestartItemResult(
                    packageName = pkg,
                    success = true,
                    details = "pkill 执行成功"
                )
            } else {
                Log.w(TAG, "Restart scope package failed: $pkg, code=${result.code}, err=$stderr")
                ScopeRestartItemResult(
                    packageName = pkg,
                    success = false,
                    details = "code=${result.code}, err=$stderr"
                )
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Restart scope failed", t)
        packages.map {
            ScopeRestartItemResult(
                packageName = it,
                success = false,
                details = t.message ?: "unknown"
            )
        }
    }
}
