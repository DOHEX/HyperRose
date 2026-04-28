package com.dohex.hyperrose.bluetooth.protocol

import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel
import com.dohex.hyperrose.domain.battery.EarBatteryState
import com.dohex.hyperrose.domain.battery.TwsBatteryState
import com.dohex.hyperrose.domain.battery.asBatteryLevelOrNull
import com.dohex.hyperrose.domain.battery.isBatteryLevelOrUnknown

/**
 * ROSE EARFREE i5 回包解析器。
 * 所有回包以 09 FF 开头，通过帧结构匹配分发到具体解析方法。
 */
object RoseResponseParser {

    /** 统一解析入口 */
    fun parse(data: ByteArray): RoseResponse {
        if (data.size < 2 || data[0] != 0x09.toByte() || data[1] != 0xFF.toByte()) {
            return RoseResponse.Unknown
        }

        // 电量回包: 09 FF 00 00 01 01 01 11 ... (至少 17 字节)
        if (data.size >= 17 && matchHeader(data, BATTERY_HEADER)) {
            return parseBattery(data)
        }

        // ANC 回包: 09 FF 00 00 01 06 02 0E ... (14 字节)
        if (data.size >= 14 && matchHeader(data, ANC_HEADER)) {
            return parseAnc(data)
        }

        // 降噪深度回包: 09 FF 00 00 01 06 07 0B ... (11 字节)
        if (data.size >= 11 && matchHeader(data, ANC_DEPTH_HEADER)) {
            return parseAncDepth(data)
        }

        // 通透强度回包: 09 FF 00 00 01 06 04 0B ... (11 字节)
        if (data.size >= 11 && matchHeader(data, TRANS_LEVEL_HEADER)) {
            return parseTransLevel(data)
        }

        // EQ 回包: 09 FF 00 00 01 02 01 0B ... (11 字节)
        if (data.size >= 11 && matchHeader(data, EQ_HEADER)) {
            return parseEq(data)
        }

        // 游戏模式回包: 09 FF 00 00 01 06 03 0B ... (11 字节)
        if (data.size >= 11 && matchHeader(data, GAME_MODE_HEADER)) {
            return parseGameMode(data)
        }

        return RoseResponse.Unknown
    }

    // ==================== 回包 Header 模式 ====================

    private val BATTERY_HEADER = intArrayOf(0x09, 0xFF, 0x00, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0x00, 0x01)
    private val ANC_HEADER = intArrayOf(0x09, 0xFF, 0x00, 0x00, 0x01, 0x06, 0x02, 0x0E)
    private val ANC_DEPTH_HEADER = intArrayOf(0x09, 0xFF, 0x00, 0x00, 0x01, 0x06, 0x07, 0x0B)
    private val TRANS_LEVEL_HEADER = intArrayOf(0x09, 0xFF, 0x00, 0x00, 0x01, 0x06, 0x04, 0x0B)
    private val EQ_HEADER = intArrayOf(0x09, 0xFF, 0x00, 0x00, 0x01, 0x02, 0x01, 0x0B)
    private val GAME_MODE_HEADER = intArrayOf(0x09, 0xFF, 0x00, 0x00, 0x01, 0x06, 0x03, 0x0B)

    // ==================== 具体解析方法 ====================

    /**
     * 解析电量回包。
     * 格式: [header 11B] [LL] [RR] [LC] [RC] [CC] [CS]
     * - LL/RR: 左/右耳电量 (0-100)
     * - LC/RC: 左/右耳充电标记 (01=充电中)
     * - CC: 充电盒电量 (0-100)，0xFF 表示未上报/不可用
     * - CS: 校验和 (前面所有字节之和 & 0xFF)
     *
     * 优先使用严格校验和校验；若校验失败但字段值合理，使用容错解析，
     * 以降低首包偶发损坏导致的状态丢失。
     */
    private fun parseBattery(data: ByteArray): RoseResponse {
        val leftLevelRaw = data[11].toInt() and 0xFF
        val rightLevelRaw = data[12].toInt() and 0xFF
        val leftChargingRaw = data[13].toInt() and 0xFF
        val rightChargingRaw = data[14].toInt() and 0xFF
        val caseLevelRaw = data[15].toInt() and 0xFF

        // 校验和验证
        val expectedChecksum = data.last().toInt() and 0xFF
        val calculatedChecksum = data.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF
        val checksumValid = expectedChecksum == calculatedChecksum
        if (!checksumValid && !isPlausibleBatteryFrame(
                leftLevel = leftLevelRaw,
                rightLevel = rightLevelRaw,
                leftChargingRaw = leftChargingRaw,
                rightChargingRaw = rightChargingRaw,
                caseLevel = caseLevelRaw,
            )
        ) {
            return RoseResponse.Unknown
        }

        val leftLevel = leftLevelRaw.asBatteryLevelOrNull()
        val rightLevel = rightLevelRaw.asBatteryLevelOrNull()
        val caseLevel = caseLevelRaw.asBatteryLevelOrNull()
        val leftCharging = leftChargingRaw == 0x01
        val rightCharging = rightChargingRaw == 0x01

        if (leftLevel == null && rightLevel == null && caseLevel == null) {
            return RoseResponse.Unknown
        }

        return RoseResponse.Battery(
            TwsBatteryState(
                left = leftLevel?.let { EarBatteryState(it, leftCharging) },
                right = rightLevel?.let { EarBatteryState(it, rightCharging) },
                caseBattery = caseLevel
            )
        )
    }

    /**
     * 解析 ANC 模式回包。
     * 判定字节 [8..11] 中哪一位为 01 来确定模式：
     * - [8]=01 → 降噪, [9]=01 → 普通, [10]=01 → 风噪/通透
     * 完整模式: 降噪 01 00 00 00 / 普通 00 01 00 00 / 风噪 00 00 01 00 / 通透 00 00 00 01
     */
    private fun parseAnc(data: ByteArray): RoseResponse {
        // 根据文档回包判定：索引从0开始，header是 09 FF 00 00 01 06 02 0E (8 bytes)
        // 后续 00 [降噪] [普通] [风噪] [通透] [校验]
        // 实际 data[8]=00, data[9]= 降噪flag, data[10]=普通flag, data[11]=风噪flag, data[12]=通透flag
        val noiseCancel = data[9].toInt() and 0xFF
        val normal = data[10].toInt() and 0xFF
        val windNoise = data[11].toInt() and 0xFF
        val transparent = data[12].toInt() and 0xFF

        val mode = when {
            noiseCancel == 1 -> AncMode.NOISE_CANCEL
            normal == 1 -> AncMode.NORMAL
            windNoise == 1 -> AncMode.WIND_NOISE
            transparent == 1 -> AncMode.TRANSPARENT
            else -> return RoseResponse.Unknown
        }
        return RoseResponse.Anc(mode)
    }

    /**
     * 解析降噪深度回包。
     * Header: 09 FF 00 00 01 06 07 0B 00 [value] [cs]
     * value: 00=轻度, 01=中度, 02=深度
     */
    private fun parseAncDepth(data: ByteArray): RoseResponse {
        val value = data[9].toInt() and 0xFF
        val depth = when (value) {
            0x00 -> AncDepth.LIGHT
            0x01 -> AncDepth.MEDIUM
            0x02 -> AncDepth.DEEP
            else -> return RoseResponse.Unknown
        }
        return RoseResponse.AncDepthChanged(depth)
    }

    /**
     * 解析通透强度回包。
     * Header: 09 FF 00 00 01 06 04 0B 00 [value] [cs]
     * value: 00=舒适, 01=人声, 02=标准
     */
    private fun parseTransLevel(data: ByteArray): RoseResponse {
        val value = data[9].toInt() and 0xFF
        val level = when (value) {
            0x00 -> TransparencyLevel.COMFORTABLE
            0x01 -> TransparencyLevel.VOCAL
            0x02 -> TransparencyLevel.STANDARD
            else -> return RoseResponse.Unknown
        }
        return RoseResponse.TransparencyChanged(level)
    }

    /**
     * 解析 EQ 回包。
     * Header: 09 FF 00 00 01 02 01 0B 00 [value] [cs]
     * value: 00=弱水经典, 01=日系柔情, 02=乐器大师, 03=清新空灵
     */
    private fun parseEq(data: ByteArray): RoseResponse {
        val value = data[9].toInt() and 0xFF
        val mode = when (value) {
            0x00 -> EqPreset.CLASSIC
            0x01 -> EqPreset.JAPANESE
            0x02 -> EqPreset.INSTRUMENT
            0x03 -> EqPreset.FRESH
            else -> return RoseResponse.Unknown
        }
        return RoseResponse.Eq(mode)
    }

    /**
     * 解析游戏模式回包。
     * Header: 09 FF 00 00 01 06 03 0B 00 [value] [cs]
     * value: 00=开, 01=关
     */
    private fun parseGameMode(data: ByteArray): RoseResponse {
        val value = data[9].toInt() and 0xFF
        return when (value) {
            0x00 -> RoseResponse.GameMode(true)
            0x01 -> RoseResponse.GameMode(false)
            else -> RoseResponse.Unknown
        }
    }

    private fun isPlausibleBatteryFrame(
        leftLevel: Int,
        rightLevel: Int,
        leftChargingRaw: Int,
        rightChargingRaw: Int,
        caseLevel: Int,
    ): Boolean {
        return leftLevel.isBatteryLevelOrUnknown() &&
            rightLevel.isBatteryLevelOrUnknown() &&
            caseLevel.isBatteryLevelOrUnknown() &&
            isValidChargingByte(leftChargingRaw) &&
            isValidChargingByte(rightChargingRaw)
    }

    private fun isValidChargingByte(value: Int): Boolean = value == 0x00 || value == 0x01

    // ==================== 工具方法 ====================

    /** 检查 data 前 n 个字节是否匹配 header */
    private fun matchHeader(data: ByteArray, header: IntArray): Boolean {
        if (data.size < header.size) return false
        for (i in header.indices) {
            if ((data[i].toInt() and 0xFF) != header[i]) return false
        }
        return true
    }
}

/** 回包解析结果 */
sealed class RoseResponse {
    data class Anc(val mode: AncMode) : RoseResponse()
    data class AncDepthChanged(val depth: AncDepth) : RoseResponse()
    data class TransparencyChanged(val level: TransparencyLevel) : RoseResponse()
    data class Eq(val mode: EqPreset) : RoseResponse()
    data class GameMode(val enabled: Boolean) : RoseResponse()
    data class Battery(val info: TwsBatteryState) : RoseResponse()
    data object Unknown : RoseResponse()
}
