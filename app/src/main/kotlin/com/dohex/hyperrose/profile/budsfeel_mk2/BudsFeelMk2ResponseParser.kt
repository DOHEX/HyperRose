package com.dohex.hyperrose.profile.budsfeel_mk2

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.dohex.hyperrose.profile.DeviceResponse

/** ROSE BudsFeel MK2 — LTV response & unsolicited notification parser. */
object BudsFeelMk2ResponseParser {
    fun parse(data: ByteArray): List<DeviceResponse> {
        if (data.size < 4) return listOf(DeviceResponse.Unknown)

        val header = data[0].toInt() and 0xFF
        if (header != 0xDD) return listOf(DeviceResponse.Unknown)

        // Verify checksum and terminator
        if (data.size < 5) return listOf(DeviceResponse.Unknown)
        val expectedChecksum = (data.dropLast(2).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        if (data[data.size - 2] != expectedChecksum) return listOf(DeviceResponse.Unknown)
        if (data[data.size - 1] != 0xAA.toByte()) return listOf(DeviceResponse.Unknown)

        val responseType = data[2].toInt() and 0xFF

        return when (responseType) {
            0x01 -> listOf(DeviceResponse.Unknown) // ACK — no state change
            0x15 -> parseStatusResponse(data)
            0x02 -> listOf(parseUnsolicitedNotification(data))
            else -> listOf(DeviceResponse.Unknown)
        }
    }

    /** Walk TLVs recursively in a status (0x15) response.
     *  TLVs may be nested — value bytes of one TLV can contain child TLVs.
     *  Start from data[3] (skip DD SEQ 15 header), strip CK AA. */
    private fun parseStatusResponse(data: ByteArray): List<DeviceResponse> {
        val payload = data.copyOfRange(3, data.size - 2)
        return parseTlvBlock(payload, 0, payload.size)
    }

    /** Recursively parse [start, end) of [data] as a TLV block.
     *  Each entry: [LEN] [TYPE] [VALUE…] where LEN covers TYPE + VALUE bytes.
     *  On invalid len, skip one byte and continue (don't break) — nested blocks
     *  may have padding bytes that look like invalid LEN values. */
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
                    // Battery: 3 bytes — LEFT RIGHT CASE
                    if (i + 4 < end) {
                        val leftRaw = data[i + 2].toInt() and 0xFF
                        val rightRaw = data[i + 3].toInt() and 0xFF
                        val caseRaw = data[i + 4].toInt() and 0xFF
                        results.add(
                            DeviceResponse.Battery(
                                TwsBatteryState(
                                    left = EarBatteryState(leftRaw, false),
                                    right = EarBatteryState(rightRaw, false),
                                    caseBattery = caseRaw.asBatteryLevelOrNull(),
                                )
                            )
                        )
                    }
                }
            }

            // Recurse into value bytes — child TLVs may be nested inside
            val valueStart = i + 2
            val valueEnd = minOf(i + len + 1, end)
            if (valueEnd > valueStart) {
                results.addAll(parseTlvBlock(data, valueStart, valueEnd))
            }

            i += len + 1
        }
        return results
    }

    private fun parseUnsolicitedNotification(data: ByteArray): DeviceResponse {
        if (data.size < 7) return DeviceResponse.Unknown
        val ptype = data[3].toInt() and 0xFF
        val value = data[4].toInt() and 0xFF
        return when (ptype) {
            0x09 -> parseAncValue(value)
            0x0E -> DeviceResponse.GameMode(value == 0x01)
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
}
