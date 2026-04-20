package com.dohex.hyperrose.ipc

import android.content.ComponentName

object QuickControlLaunchValidator {
    private val trustedPackages = setOf(
        HyperRoseIpc.PACKAGE_APP,
        HyperRoseIpc.PACKAGE_SYSTEM_UI,
        HyperRoseIpc.PACKAGE_MI_BLUETOOTH
    )

    fun isTrustedCaller(caller: ComponentName?): Boolean {
        if (caller == null) return true
        return caller.packageName in trustedPackages
    }
}
