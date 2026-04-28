package com.dohex.hyperrose.xposed.process.mibluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.dohex.hyperrose.core.reflection.ReflectionHelper
import com.dohex.hyperrose.domain.battery.asBatteryLevelOrNull
import com.dohex.hyperrose.ipc.QuickControlIntentFactory
import com.dohex.hyperrose.util.FocusIslandBridge
import com.dohex.hyperrose.xposed.entry.HyperRoseModuleEntry.Companion.TAG
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction

/**
 * 在 com.xiaomi.bluetooth 进程接收 SHOW_ISLAND 广播并发送超级岛。
 * 对齐 HyperOriG 的“宿主发岛”策略。
 */
@SuppressLint("MissingPermission")
object MiBluetoothFocusIslandHook {

    private const val CHANNEL_ID = "hyperrose.focus"
    private const val ISLAND_NOTIFICATION_ID = 10086
    private const val ISLAND_TIMEOUT_SECONDS = 30
    private const val QUICK_CONTROL_REQUEST_CODE = 10086
    private var receiverRegistered = false
    private var lastKnownCaseLevel: Int? = null

    @SuppressLint("PrivateApi")
    fun init(module: XposedModule, param: PackageLoadedParam) {
        val cl = param.defaultClassLoader
        try {
            val notifClass = cl.loadClass("com.android.bluetooth.ble.app.MiuiBluetoothNotification")
            val ctor = notifClass.declaredConstructors.firstOrNull { it.parameterCount >= 1 }
            if (ctor == null) {
                module.log(
                    Log.WARN, TAG,
                    "MiBluetoothFocusIslandHook: MiuiBluetoothNotification constructor not found"
                )
                return
            }

            module.hook(ctor).intercept { chain ->
                val result = chain.proceed()
                try {
                    val context = (chain.getArg(0) as? Context) ?: (try {
                        ReflectionHelper.getField(chain.thisObject, "mContext") as? Context
                    } catch (_: Throwable) {
                        null
                    })
                    if (context != null) registerReceiver(module, context)
                } catch (t: Throwable) {
                    module.log(
                        Log.ERROR, TAG, "MiBluetoothFocusIslandHook: failed to register receiver", t
                    )
                }
                result
            }

            module.log(
                Log.INFO, TAG,
                "MiBluetoothFocusIslandHook: hooked MiuiBluetoothNotification constructor"
            )
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "MiBluetoothFocusIslandHook: failed to install hook", t)
        }
    }

    private fun registerReceiver(module: XposedModule, context: Context) {
        if (receiverRegistered) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    HyperRoseAction.SHOW_ISLAND -> {
                        val left = intent.getIntExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, -1)
                            .asBatteryLevelOrNull() ?: -1
                        val right = intent.getIntExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, -1)
                            .asBatteryLevelOrNull() ?: -1
                        val currentCaseLevel =
                            intent.getIntExtra(HyperRoseAction.EXTRA_CASE_LEVEL, -1)
                                .asBatteryLevelOrNull()
                        val caseLevel = currentCaseLevel ?: lastKnownCaseLevel ?: -1
                        if (currentCaseLevel != null) {
                            lastKnownCaseLevel = currentCaseLevel
                        }
                        val leftCharging =
                            intent.getBooleanExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, false)
                        val rightCharging =
                            intent.getBooleanExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, false)
                        if (left < 0 && right < 0 && caseLevel < 0) return

                        val device = intent.getParcelableExtra(
                            HyperRoseAction.EXTRA_DEVICE, BluetoothDevice::class.java
                        )

                        runCatching {
                            showIsland(
                                context = ctx,
                                device = device,
                                left = left,
                                right = right,
                                caseLevel = caseLevel,
                                leftCharging = leftCharging,
                                rightCharging = rightCharging,
                            )
                        }.onFailure {
                            module.log(
                                Log.WARN, TAG, "MiBluetoothFocusIslandHook: show island failed", it
                            )
                        }
                    }

                    HyperRoseAction.DEVICE_CONNECTED -> {
                        lastKnownCaseLevel = null
                    }

                    HyperRoseAction.DEVICE_DISCONNECTED -> {
                        lastKnownCaseLevel = null
                        cancelIsland(ctx)
                    }
                }
            }
        }

        val filter = IntentFilter(HyperRoseAction.SHOW_ISLAND).apply {
            HyperRoseAction.BRIDGE_STATE_ACTIONS.asSequence()
                .filter { it == HyperRoseAction.DEVICE_CONNECTED || it == HyperRoseAction.DEVICE_DISCONNECTED }
                .forEach(::addAction)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        module.log(Log.INFO, TAG, "MiBluetoothFocusIslandHook: receiver ready")
    }

    @SuppressLint("NotificationPermission")
    private fun showIsland(
        context: Context,
        device: BluetoothDevice?,
        left: Int,
        right: Int,
        caseLevel: Int,
        leftCharging: Boolean,
        rightCharging: Boolean,
    ) {
        val nm =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val content = buildBatteryText(
            left = left,
            right = right,
            caseLevel = caseLevel,
            leftCharging = leftCharging,
            rightCharging = rightCharging,
        )

        val extras = FocusIslandBridge.buildBatteryIslandExtras(
            leftLevel = left, rightLevel = right, caseLevel = caseLevel,
            leftCharging = leftCharging, rightCharging = rightCharging,
            islandTimeoutSeconds = ISLAND_TIMEOUT_SECONDS
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(device?.name ?: "HyperRose").setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content)).setOnlyAlertOnce(true)
            .setContentIntent(buildQuickControlPendingIntent(context, device, left, right))
        builder.addExtras(extras)

        nm.notify(ISLAND_NOTIFICATION_ID, builder.build())
    }

    private fun buildBatteryText(
        left: Int,
        right: Int,
        caseLevel: Int,
        leftCharging: Boolean,
        rightCharging: Boolean,
    ): String {
        val segments = mutableListOf<String>()
        if (left >= 0) {
            segments += "L ${formatEarBattery(left, leftCharging)}"
        }
        if (right >= 0) {
            segments += "R ${formatEarBattery(right, rightCharging)}"
        }
        if (caseLevel >= 0) {
            segments += "C ${caseLevel}%"
        }
        return if (segments.isEmpty()) "电量未知" else segments.joinToString(" | ")
    }

    private fun formatEarBattery(level: Int, charging: Boolean): String {
        return if (charging) "$level% ⚡" else "$level%"
    }

    private fun buildQuickControlPendingIntent(
        context: Context, device: BluetoothDevice?, left: Int, right: Int
    ): PendingIntent {
        val intent = QuickControlIntentFactory.createLaunchIntent(
            deviceName = device?.name, leftLevel = left, rightLevel = right, forceConnected = true
        )

        return PendingIntent.getActivity(
            context, QUICK_CONTROL_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelIsland(context: Context) {
        val nm =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.cancel(ISLAND_NOTIFICATION_ID) }
    }
}
