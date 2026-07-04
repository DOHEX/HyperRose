package com.dohex.hyperrose.profile.budsfeel_mk2

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.profile.DeviceResponse

/** ROSE BudsFeel MK2 — LTV response & unsolicited notification parser. */
object BudsFeelMk2ResponseParser {
    fun parse(data: ByteArray): DeviceResponse {
        if (data.size < 4) return DeviceResponse.Unknown

        val header = data[0].toInt() and 0xFF
        if (header != 0xDD) return DeviceResponse.Unknown

        val cmd = data[2].toInt() and 0xFF

        return when (cmd) {
            0x01 -> DeviceResponse.Unknown  // ACK
            0x15 -> parseCapabilityResponse(data)
            0x02 -> parseUnsolicitedNotification(data)
            else -> DeviceResponse.Unknown
        }
    }

    private fun parseCapabilityResponse(data: ByteArray): DeviceResponse {
        val payload = data.copyOfRange(6, data.size - 2)
        var i = 0
        while (i < payload.size - 1) {
            val len = payload[i].toInt() and 0xFF
            if (len < 2 || i + len >= payload.size) break
            val ptype = payload[i + 1].toInt() and 0xFF
            val value = payload[i + 2].toInt() and 0xFF
            when (ptype) {
                0x09 -> return parseAncValue(value)
                0x2F -> return parseGameModeValue(value)
                0x0E -> return parseLowLatencyValue(value)
            }
            i += len + 1
        }
        return DeviceResponse.Unknown
    }

    private fun parseUnsolicitedNotification(data: ByteArray): DeviceResponse {
        if (data.size < 5) return DeviceResponse.Unknown
        val ptype = data[3].toInt() and 0xFF
        val value = data[4].toInt() and 0xFF
        return when (ptype) {
            0x09 -> parseAncValue(value)
            0x0E -> parseLowLatencyValue(value)
            0x2F -> parseGameModeValue(value)
            else -> DeviceResponse.Unknown
        }
    }

    private fun parseAncValue(value: Int): DeviceResponse.Anc {
        val mode = when (value) {
            0x01 -> AncMode.NOISE_CANCEL
            0x02 -> AncMode.NORMAL
            0x03 -> AncMode.TRANSPARENT
            0x04 -> AncMode.WIND_NOISE
            else -> AncMode.NORMAL
        }
        return DeviceResponse.Anc(mode)
    }

    private fun parseLowLatencyValue(value: Int): DeviceResponse.LowLatencyChanged =
        DeviceResponse.LowLatencyChanged(value == 0x01)

    private fun parseGameModeValue(value: Int): DeviceResponse.GameMode =
        DeviceResponse.GameMode(value == 0x04)
}
