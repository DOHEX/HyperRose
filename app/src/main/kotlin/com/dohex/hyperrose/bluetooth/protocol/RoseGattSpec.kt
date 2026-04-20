package com.dohex.hyperrose.bluetooth.protocol

import java.util.UUID

object RoseGattSpec {
    val SERVICE_UUID: UUID = UUID.fromString("011bf5da-0000-1000-8000-00805f9b34fb")
    val WRITE_UUID: UUID = UUID.fromString("00007777-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("00008888-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
