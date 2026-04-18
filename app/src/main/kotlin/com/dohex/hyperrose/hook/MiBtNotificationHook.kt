package com.dohex.hyperrose.hook

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.dohex.hyperrose.core.reflection.ReflectionHelper
import com.dohex.hyperrose.entry.HyperRoseXposedEntry.Companion.TAG
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction
import com.dohex.hyperrose.util.FocusIslandBridge
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 在 com.xiaomi.bluetooth 进程接收 SHOW_ISLAND 广播并发送超级岛。
 * 对齐 HyperOriG 的“宿主发岛”策略。
 */
@SuppressLint("MissingPermission")
object MiBtNotificationHook {

    private const val CHANNEL_ID = "hyperrose.focus"
    private const val CHANNEL_NAME = "HyperRose Focus"
    private const val ISLAND_NOTIFICATION_ID = 10086
    private const val ISLAND_TIMEOUT_SECONDS = 30
    private const val QUICK_CONTROL_REQUEST_CODE = 10086
    private var receiverRegistered = false
    private var showedIslandOnCurrentConnection = false
    private var lastIslandTimestamp = 0L

    fun init(module: XposedModule, param: PackageLoadedParam) {
        val cl = param.defaultClassLoader
        try {
            val notifClass = cl.loadClass("com.android.bluetooth.ble.app.MiuiBluetoothNotification")
            val ctor = notifClass.declaredConstructors.firstOrNull { it.parameterCount >= 1 }
            if (ctor == null) {
                module.log(Log.WARN, TAG, "MiBtNotificationHook: constructor not found")
                return
            }

            module.hook(ctor).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                try {
                    val context = (chain.getArg(0) as? Context)
                        ?: (try { ReflectionHelper.getField(chain.thisObject, "mContext") as? Context } catch (_: Throwable) { null })
                    if (context != null) registerReceiver(module, context)
                } catch (t: Throwable) {
                    module.log(Log.ERROR, TAG, "MiBtNotificationHook: register receiver failed", t)
                }
                result
            })

            module.log(Log.INFO, TAG, "MiBtNotificationHook: hooked MiuiBluetoothNotification")
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "MiBtNotificationHook: hook failed", t)
        }
    }

    private fun registerReceiver(module: XposedModule, context: Context) {
        if (receiverRegistered) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    HyperRoseAction.SHOW_ISLAND -> {
                        val now = System.currentTimeMillis()
                        val allowByFirstShow = !showedIslandOnCurrentConnection
                        val allowByInterval = now - lastIslandTimestamp >= 20_000L
                        if (!allowByFirstShow && !allowByInterval) return

                        val left = intent.getIntExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, -1)
                        val right = intent.getIntExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, -1)
                        val caseLevel = intent.getIntExtra(HyperRoseAction.EXTRA_CASE_LEVEL, -1)
                        val leftCharging = intent.getBooleanExtra(HyperRoseAction.EXTRA_LEFT_CHARGING, false)
                        val rightCharging = intent.getBooleanExtra(HyperRoseAction.EXTRA_RIGHT_CHARGING, false)
                        if (left < 0 && right < 0 && caseLevel < 0) return

                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(HyperRoseAction.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(HyperRoseAction.EXTRA_DEVICE)
                        }

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
                        }
                            .onSuccess {
                                showedIslandOnCurrentConnection = true
                                lastIslandTimestamp = now
                            }
                            .onFailure { module.log(Log.WARN, TAG, "MiBtNotificationHook: show island failed", it) }
                    }

                    HyperRoseAction.DEVICE_CONNECTED -> {
                        showedIslandOnCurrentConnection = false
                    }

                    HyperRoseAction.DEVICE_DISCONNECTED -> {
                        showedIslandOnCurrentConnection = false
                        lastIslandTimestamp = 0L
                        cancelIsland(ctx)
                    }
                }
            }
        }

        val filter = IntentFilter(HyperRoseAction.SHOW_ISLAND).apply {
            addAction(HyperRoseAction.DEVICE_CONNECTED)
            addAction(HyperRoseAction.DEVICE_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        module.log(Log.INFO, TAG, "MiBtNotificationHook: receiver registered")
    }

    private fun showIsland(
        context: Context,
        device: BluetoothDevice?,
        left: Int,
        right: Int,
        caseLevel: Int,
        leftCharging: Boolean,
        rightCharging: Boolean,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)
        val content = buildBatteryText(
            left = left,
            right = right,
            caseLevel = caseLevel,
            leftCharging = leftCharging,
            rightCharging = rightCharging,
        )

        val extras = FocusIslandBridge.buildBatteryIslandExtras(
            leftLevel = left,
            rightLevel = right,
            caseLevel = caseLevel,
            leftCharging = leftCharging,
            rightCharging = rightCharging,
            islandTimeoutSeconds = ISLAND_TIMEOUT_SECONDS
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(device?.name ?: "HyperRose")
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setOnlyAlertOnce(true)
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
            segments += "左 ${formatEarBattery(left, leftCharging)}"
        }
        if (right >= 0) {
            segments += "右 ${formatEarBattery(right, rightCharging)}"
        }
        if (caseLevel >= 0) {
            segments += "盒 ${caseLevel}%"
        }
        return if (segments.isEmpty()) "电量未知" else segments.joinToString(" | ")
    }

    private fun formatEarBattery(level: Int, charging: Boolean): String {
        return if (charging) "$level% ⚡" else "$level%"
    }

    private fun buildQuickControlPendingIntent(
        context: Context,
        device: BluetoothDevice?,
        left: Int,
        right: Int
    ): PendingIntent {
        val intent = Intent().apply {
            setClassName(HyperRoseAction.PACKAGE_APP, HyperRoseAction.QUICK_CONTROL_ACTIVITY)
            putExtra(HyperRoseAction.EXTRA_DEVICE_NAME, device?.name)
            putExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, left)
            putExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, right)
            putExtra(HyperRoseAction.EXTRA_FORCE_CONNECTED, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        return PendingIntent.getActivity(
            context,
            QUICK_CONTROL_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelIsland(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.cancel(ISLAND_NOTIFICATION_ID) }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
