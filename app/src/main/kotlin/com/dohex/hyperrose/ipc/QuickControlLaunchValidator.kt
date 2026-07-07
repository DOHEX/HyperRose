package com.dohex.hyperrose.ipc

import android.content.ComponentName
import android.net.Uri

object QuickControlLaunchValidator {
    private val trustedPackages =
        setOf(
            HyperRoseIpc.PACKAGE_APP,
            HyperRoseIpc.PACKAGE_MI_BLUETOOTH,
        )

    /** 验证调用者是否可信。 [referrer] 用于 PendingIntent 启动场景，此时 callingActivity 可能为系统组件。 */
    fun isTrustedCaller(
        caller: ComponentName?,
        referrer: Uri? = null,
    ): Boolean {
        if (caller != null && caller.packageName in trustedPackages) return true
        // Notification PendingIntent launches may carry the creating package as referrer
        val referrerPackage = referrer?.host
        if (referrerPackage != null && referrerPackage in trustedPackages) return true
        return false
    }
}
