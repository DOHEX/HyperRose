package com.dohex.hyperrose.profile.rfcomm

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.dohex.hyperrose.profile.DeviceResponse

/** Shared ROSE RFCOMM response parser for compatible status and notification frames. */
object RoseRfcommResponseParser {
    fun parse(data: ByteArray): List<DeviceResponse> {
        if (data.size < 7) return listOf(DeviceResponse.Unknown)
        if (data[0].toInt() and 0xFF != 0xDD) return listOf(DeviceResponse.Unknown)
        if (data[data.size - 1] != 0xAA.toByte()) return listOf(DeviceResponse.Unknown)

        val expectedChecksum =
            (data.dropLast(2).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        if (data[data.size - 2] != expectedChecksum) return listOf(DeviceResponse.Unknown)

        return when (data[2].toInt() and 0xFF) {
            0x15 -> parseStatusResponse(data)
            0x04 -> parseBatteryPush(data)
            0x02 -> listOf(parseShortNotification(data))
            else -> listOf(DeviceResponse.Unknown)
        }
    }

    private fun parseStatusResponse(data: ByteArray): List<DeviceResponse> {
        val payload = data.copyOfRange(3, data.size - 2)
        val startsWithTouchSection = payload.size >= 21 && payload[0].toInt() and 0xFF == 0x01
        val results = parseTlvBlock(payload, if (startsWithTouchSection) 21 else 0)
        return if (results.isEmpty()) listOf(DeviceResponse.Unknown) else results
    }

    private fun parseTlvBlock(data: ByteArray, start: Int): List<DeviceResponse> {
        val results = mutableListOf<DeviceResponse>()
        var index = start
        while (index < data.size) {
            if (index + 1 >= data.size) break
            val length = data[index].toInt() and 0xFF
            val valueEnd = index + length + 1
            if (length < 2 || valueEnd > data.size) {
                index++
                continue
            }

            val key = data[index + 1].toInt() and 0xFF
            val value = data[index + 2].toInt() and 0xFF
            when (key) {
                0x07 -> if (value in 0..1) {
                    results.add(DeviceResponse.PromptToneLanguageChanged(value))
                }
                0x09 -> parseAncValue(value)?.let(results::add)
                0x0E -> results.add(DeviceResponse.GameMode(value == 0x01))
                0x2E -> if (value in 1..5) {
                    results.add(DeviceResponse.PromptToneLevelChanged(value))
                }
                0x0C -> if (length >= 4) {
                    results.add(parseBattery(data[index + 2], data[index + 3], data[index + 4]))
                }
                0x2A -> parseEqValue(value)?.let { results.add(DeviceResponse.Eq(it)) }
                0x2C -> parseAncDepthValue(value)?.let { results.add(DeviceResponse.AncDepthChanged(it)) }
                0x2D -> parseTransLevelValue(value)?.let {
                    results.add(DeviceResponse.TransparencyChanged(it))
                }
            }
            index = valueEnd
        }
        return results
    }

    private fun parseBatteryPush(data: ByteArray): List<DeviceResponse> {
        if (data.size != 9 || data[3].toInt() and 0xFF != 0x0C) {
            return listOf(DeviceResponse.Unknown)
        }
        return listOf(parseBattery(data[4], data[5], data[6]))
    }

    private fun parseBattery(left: Byte, right: Byte, case: Byte): DeviceResponse.Battery {
        val leftLevel = left.toInt() and 0xFF
        val rightLevel = right.toInt() and 0xFF
        val caseLevel = case.toInt() and 0xFF
        return DeviceResponse.Battery(
            TwsBatteryState(
                left = leftLevel.asBatteryLevelOrNull()?.let { EarBatteryState(it, false) },
                right = rightLevel.asBatteryLevelOrNull()?.let { EarBatteryState(it, false) },
                caseBattery = caseLevel.asBatteryLevelOrNull(),
            ),
        )
    }

    private fun parseShortNotification(data: ByteArray): DeviceResponse {
        val command = data[3].toInt() and 0xFF
        val value = data[4].toInt() and 0xFF
        return when (command) {
            0x09 -> parseAncValue(value) ?: DeviceResponse.Unknown
            0x0E -> DeviceResponse.GameMode(value == 0x01)
            else -> DeviceResponse.Unknown
        }
    }

    private fun parseAncValue(value: Int): DeviceResponse.Anc? {
        val mode = when (value) {
            0x01 -> AncMode.NOISE_CANCEL
            0x02 -> AncMode.NORMAL
            0x03 -> AncMode.TRANSPARENT
            0x04 -> AncMode.WIND_NOISE
            0x05 -> AncMode.ADAPTIVE_NOISE_CANCEL
            0x06 -> AncMode.EXTREME_NOISE_CANCEL
            else -> return null
        }
        return DeviceResponse.Anc(mode)
    }

    private fun parseAncDepthValue(value: Int) = when (value) {
        0x01 -> com.dohex.hyperrose.model.AncDepth.LIGHT
        0x03 -> com.dohex.hyperrose.model.AncDepth.MEDIUM
        0x05 -> com.dohex.hyperrose.model.AncDepth.DEEP
        else -> null
    }

    private fun parseTransLevelValue(value: Int) = when (value) {
        0x01 -> com.dohex.hyperrose.model.TransparencyLevel.STANDARD
        0x05 -> com.dohex.hyperrose.model.TransparencyLevel.DEEP
        else -> null
    }

    private fun parseEqValue(value: Int) = when (value) {
        0x00 -> com.dohex.hyperrose.model.EqPreset.HIFI
        0x01 -> com.dohex.hyperrose.model.EqPreset.POP
        0x02 -> com.dohex.hyperrose.model.EqPreset.ROCK
        0x03 -> com.dohex.hyperrose.model.EqPreset.ROSE_CLASSIC
        else -> null
    }
}
