package com.dohex.hyperrose.ipc

import android.content.Intent
import com.dohex.hyperrose.model.asBatteryLevelOrNull

object QuickControlIntentFactory {
    private const val LAUNCH_FLAGS =
        Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

    fun createLaunchIntent(
        deviceName: String?,
        profileId: String? = null,
        leftLevel: Int? = null,
        rightLevel: Int? = null,
        forceConnected: Boolean = true,
    ): Intent = Intent().apply {
        setClassName(HyperRoseIpc.PACKAGE_APP, HyperRoseIpc.QUICK_CONTROL_ACTIVITY)
        putExtra(HyperRoseIpc.EXTRA_DEVICE_NAME, deviceName)
        profileId?.let { putExtra(HyperRoseIpc.EXTRA_PROFILE_ID, it) }
        leftLevel?.asBatteryLevelOrNull()?.let { putExtra(HyperRoseIpc.EXTRA_LEFT_LEVEL, it) }
        rightLevel?.asBatteryLevelOrNull()?.let { putExtra(HyperRoseIpc.EXTRA_RIGHT_LEVEL, it) }
        putExtra(HyperRoseIpc.EXTRA_FORCE_CONNECTED, forceConnected)
        addFlags(LAUNCH_FLAGS)
    }
}
