package com.dohex.hyperrose.profile.rose_cambrian

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.dohex.hyperrose.profile.DeviceResponse

object RoseCambrianResponseParser {
    fun parse(data: ByteArray): List<DeviceResponse> {
        if (data.size < 4) return listOf(DeviceResponse.Unknown)

        val header = data[0].toInt() and 0xFF
        if (header != 0xDD) return listOf(DeviceResponse.Unknown)

        if (data[data.size - 1] != 0xAA.toByte()) return listOf(DeviceResponse.Unknown)

        val responseType = data[2].toInt() and 0xFF

        return when (responseType) {
            0x01 -> listOf(DeviceResponse.Unknown)
            0x15 -> parseStatusResponse(data)
            0x02 -> listOf(parseUnsolicitedNotification(data))
            0x04 -> listOf(parseType4Response(data))
            else -> listOf(DeviceResponse.Unknown)
        }
    }

    private fun parseStatusResponse(data: ByteArray): List<DeviceResponse> {
        val payload = data.copyOfRange(3, data.size - 1)
        return parseTlvBlock(payload, 0, payload.size)
    }

    private fun parseTlvBlock(data: ByteArray, start: Int, end: Int): List<DeviceResponse> {
        val results = mutableListOf<DeviceResponse>()
        var i = start
        while (i < end - 1) {
            val len = data[i].toInt() and 0xFF
            if (len < 2 || i + len > end) {
                i++
                continue
            }

            val ptype = data[i + 1].toInt() and 0xFF

            when (ptype) {
                0x09 -> {
                    val value = data[i + 2].toInt() and 0xFF
                    results.add(parseAncValue(value))
                }

                0x0E -> {
                    val value = data[i + 2].toInt() and 0xFF
                    results.add(DeviceResponse.GameMode(value == 0x01))
                }

                0x0C -> {
                    val values = mutableListOf<Int>()
                    var vi = i + 2
                    val vEnd = minOf(i + len + 1, end)
                    while (vi < vEnd) {
                        values.add(data[vi].toInt() and 0xFF)
                        vi++
                    }
                    val nonZero = values.filter { it > 0 }
                    if (nonZero.size == 1 && values.size >= 3) {
                        results.add(
                            DeviceResponse.Battery(
                                TwsBatteryState(
                                    left = null, right = null,
                                    caseBattery = nonZero[0],
                                )
                            )
                        )
                    } else {
                        results.add(
                            when (values.size) {
                                1 -> DeviceResponse.Battery(
                                    TwsBatteryState(
                                        left = null, right = null,
                                        caseBattery = values[0],
                                    )
                                )
                                2 -> DeviceResponse.Battery(
                                    TwsBatteryState(
                                        left = EarBatteryState(values[0], false),
                                        right = EarBatteryState(values[1], false),
                                        caseBattery = null,
                                    )
                                )
                                else -> DeviceResponse.Battery(
                                    TwsBatteryState(
                                        left = EarBatteryState(values[0], false),
                                        right = EarBatteryState(values[1], false),
                                        caseBattery = values[2].asBatteryLevelOrNull(),
                                    )
                                )
                            }
                        )
                    }
                }

                0x2A -> {
                    val value = data[i + 2].toInt() and 0xFF
                    results.add(parseEqValue(value))
                }
            }

            val valueStart = i + 2
            val valueEnd = minOf(i + len + 1, end)
            if (valueEnd > valueStart) {
                results.addAll(parseTlvBlock(data, valueStart, valueEnd))
            }

            i += len + 1
        }
        return results
    }

    private fun parseType4Response(data: ByteArray): DeviceResponse {
        if (data.size < 6) return DeviceResponse.Unknown
        val subType = data[3].toInt() and 0xFF
        return when (subType) {
            0x0C -> {
                val level = data[4].toInt() and 0xFF
                DeviceResponse.Battery(
                    TwsBatteryState(
                        left = EarBatteryState(level, false),
                        right = null,
                        caseBattery = null,
                    )
                )
            }
            else -> DeviceResponse.Unknown
        }
    }

    private fun parseUnsolicitedNotification(data: ByteArray): DeviceResponse {
        if (data.size < 6) return DeviceResponse.Unknown
        val ptype = data[3].toInt() and 0xFF
        val value = data[4].toInt() and 0xFF
        return when (ptype) {
            0x09 -> parseAncValue(value)
            0x0E -> DeviceResponse.GameMode(value == 0x01)
            0x2A -> parseEqValue(value)
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
