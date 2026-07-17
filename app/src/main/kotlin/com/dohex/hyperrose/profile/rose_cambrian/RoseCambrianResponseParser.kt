package com.dohex.hyperrose.profile.rose_cambrian

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.profile.DeviceResponse

object RoseCambrianResponseParser {
    fun parse(data: ByteArray): List<DeviceResponse> {
        if (data.size < 5) return listOf(DeviceResponse.Unknown)
        if ((data[0].toInt() and 0xFF) != 0xDD) return listOf(DeviceResponse.Unknown)
        if (data[data.size - 1] != 0xAA.toByte()) return listOf(DeviceResponse.Unknown)

        val responseType = data[2].toInt() and 0xFF
        return when (responseType) {
            0x01 -> listOf(DeviceResponse.Unknown)
            0x15 -> parseStatusResponse(data)
            0x02 -> listOf(parseNotification(data))
            0x04 -> listOf(parseType4Response(data))
            else -> listOf(DeviceResponse.Unknown)
        }
    }

    private fun parseStatusResponse(data: ByteArray): List<DeviceResponse> {
        val tlv = data.copyOfRange(3, data.size - 1)
        val results = mutableListOf<DeviceResponse>()
        parseTlv(tlv, 0, tlv.size, results)
        if (results.isEmpty()) return listOf(DeviceResponse.Unknown)
        return results
    }

    private fun parseTlv(
        data: ByteArray, start: Int, end: Int, out: MutableList<DeviceResponse>,
    ) {
        var i = start
        while (i < end - 1) {
            val len = data[i].toInt() and 0xFF
            if (len < 2 || i + len > end) { i++; continue }
            val ptype = data[i + 1].toInt() and 0xFF
            when (ptype) {
                0x09 -> {
                    val value = data[i + 2].toInt() and 0xFF
                    out.add(parseAncValue(value))
                }
                0x0E -> out.add(DeviceResponse.GameMode(data[i + 2].toInt() == 0x01))
                0x0C -> {
                    val level = data[i + len].toInt() and 0xFF
                    out.add(DeviceResponse.Battery(
                        TwsBatteryState(
                            left = EarBatteryState(level, false), right = null, caseBattery = null,
                        )
                    ))
                }
                0x2A -> {
                    val value = data[i + 2].toInt() and 0xFF
                    out.add(parseEqValue(value))
                }
            }
            val innerStart = i + 2
            val innerEnd = minOf(i + len + 1, end)
            if (innerEnd > innerStart) parseTlv(data, innerStart, innerEnd, out)
            i += len + 1
        }
    }

    private fun parseNotification(data: ByteArray): DeviceResponse {
        if (data.size < 6) return DeviceResponse.Unknown
        val subType = data[3].toInt() and 0xFF
        val value = data[4].toInt() and 0xFF
        return when (subType) {
            0x09 -> parseAncValue(value)
            0x0E -> DeviceResponse.GameMode(value == 0x01)
            0x2A -> parseEqValue(value)
            else -> DeviceResponse.Unknown
        }
    }

    private fun parseType4Response(data: ByteArray): DeviceResponse {
        if (data.size < 6) return DeviceResponse.Unknown
        val subType = data[3].toInt() and 0xFF
        return when (subType) {
            0x0C -> {
                val level = data[data.size - 2].toInt() and 0xFF
                DeviceResponse.Battery(
                    TwsBatteryState(
                        left = EarBatteryState(level, false), right = null, caseBattery = null,
                    )
                )
            }
            else -> DeviceResponse.Unknown
        }
    }

    private fun parseAncValue(value: Int): DeviceResponse.Anc {
        val mode = when (value) {
            0x01 -> AncMode.NOISE_CANCEL
            0x02 -> AncMode.NORMAL
            0x03 -> AncMode.TRANSPARENT
            0x04 -> AncMode.WIND_NOISE
            else -> return DeviceResponse.Anc(AncMode.NORMAL)
        }
        return DeviceResponse.Anc(mode)
    }

    private fun parseEqValue(value: Int): DeviceResponse.Eq {
        val preset = when (value) {
            0x00 -> EqPreset.HIFI
            0x01 -> EqPreset.POP
            0x02 -> EqPreset.ROCK
            else -> return DeviceResponse.Eq(EqPreset.POP)
        }
        return DeviceResponse.Eq(preset)
    }
}
