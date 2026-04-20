package com.dohex.hyperrose.bluetooth.protocol

import android.os.Handler

object RoseGattQueryScheduler {
    fun scheduleStatusQueries(handler: Handler, sendCommand: (ByteArray) -> Unit) {
        RoseCommandSet.STATUS_QUERY_SEQUENCE.forEachIndexed { index, query ->
            handler.postDelayed(
                { sendCommand(query) },
                RoseGattTiming.STATUS_QUERY_STEP_DELAY_MS * index
            )
        }
    }

    fun scheduleBatteryPolling(handler: Handler, sendCommand: (ByteArray) -> Unit) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                sendCommand(RoseCommandSet.QUERY_BATTERY)
                handler.postDelayed(this, RoseGattTiming.BATTERY_POLL_INTERVAL_MS)
            }
        }, RoseGattTiming.BATTERY_POLL_INTERVAL_MS)
    }
}
