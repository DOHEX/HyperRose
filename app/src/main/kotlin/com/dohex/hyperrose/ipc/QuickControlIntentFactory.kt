package com.dohex.hyperrose.ipc

import android.content.Intent

object QuickControlIntentFactory {
    private val launchFlags =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_NO_ANIMATION or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP

    fun createLaunchIntent(
        deviceName: String?,
        leftLevel: Int? = null,
        rightLevel: Int? = null,
        forceConnected: Boolean = true,
    ): Intent {
        return Intent().apply {
            setClassName(HyperRoseIpc.PACKAGE_APP, HyperRoseIpc.QUICK_CONTROL_ACTIVITY)
            putExtra(HyperRoseIpc.EXTRA_DEVICE_NAME, deviceName)
            leftLevel?.let { putExtra(HyperRoseIpc.EXTRA_LEFT_LEVEL, it) }
            rightLevel?.let { putExtra(HyperRoseIpc.EXTRA_RIGHT_LEVEL, it) }
            putExtra(HyperRoseIpc.EXTRA_FORCE_CONNECTED, forceConnected)
            addFlags(launchFlags)
        }
    }
}
