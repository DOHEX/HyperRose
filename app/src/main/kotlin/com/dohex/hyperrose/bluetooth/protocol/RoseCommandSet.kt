@file:Suppress("unused")

package com.dohex.hyperrose.bluetooth.protocol

import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel

/**
 * ROSE EARFREE i5 全部控制/查询命令。
 * 协议帧前缀：请求 08 EE / 回包 09 FF
 * 通信通道：BLE GATT
 */
object RoseCommandSet {

    // ==================== ANC 模式切换 ====================

    val ANC_NOISE_CANCEL: ByteArray = hexToBytes("08 EE 00 00 00 06 82 0E 00 01 00 00 00 8D")
    val ANC_WIND_NOISE: ByteArray   = hexToBytes("08 EE 00 00 00 06 82 0E 00 00 00 01 00 8D")
    val ANC_NORMAL: ByteArray       = hexToBytes("08 EE 00 00 00 06 82 0E 00 00 01 00 00 8D")
    val ANC_TRANSPARENT: ByteArray  = hexToBytes("08 EE 00 00 00 06 82 0E 00 00 00 00 01 8D")

    fun ancCommand(mode: AncMode): ByteArray = when (mode) {
        AncMode.NOISE_CANCEL -> ANC_NOISE_CANCEL
        AncMode.WIND_NOISE -> ANC_WIND_NOISE
        AncMode.NORMAL -> ANC_NORMAL
        AncMode.TRANSPARENT -> ANC_TRANSPARENT
    }

    // ==================== 降噪深度（仅 ANC=降噪） ====================

    val DEPTH_LIGHT: ByteArray  = hexToBytes("08 EE 00 00 00 06 87 0B 00 00 8E")
    val DEPTH_MEDIUM: ByteArray = hexToBytes("08 EE 00 00 00 06 87 0B 00 01 8F")
    val DEPTH_DEEP: ByteArray   = hexToBytes("08 EE 00 00 00 06 87 0B 00 02 90")

    fun ancDepthCommand(depth: AncDepth): ByteArray = when (depth) {
        AncDepth.LIGHT -> DEPTH_LIGHT
        AncDepth.MEDIUM -> DEPTH_MEDIUM
        AncDepth.DEEP -> DEPTH_DEEP
    }

    // ==================== 通透强度（仅 ANC=通透） ====================

    val TRANS_COMFORTABLE: ByteArray = hexToBytes("08 EE 00 00 00 06 84 0B 00 00 8B")
    val TRANS_VOCAL: ByteArray       = hexToBytes("08 EE 00 00 00 06 84 0B 00 01 8C")
    val TRANS_STANDARD: ByteArray    = hexToBytes("08 EE 00 00 00 06 84 0B 00 02 8D")

    fun transLevelCommand(level: TransparencyLevel): ByteArray = when (level) {
        TransparencyLevel.COMFORTABLE -> TRANS_COMFORTABLE
        TransparencyLevel.VOCAL -> TRANS_VOCAL
        TransparencyLevel.STANDARD -> TRANS_STANDARD
    }

    // ==================== EQ 调音 ====================

    val EQ_CLASSIC: ByteArray    = hexToBytes("08 EE 00 00 00 02 81 0B 00 00 84")
    val EQ_JAPANESE: ByteArray   = hexToBytes("08 EE 00 00 00 02 81 0B 00 01 85")
    val EQ_INSTRUMENT: ByteArray = hexToBytes("08 EE 00 00 00 02 81 0B 00 02 86")
    val EQ_FRESH: ByteArray      = hexToBytes("08 EE 00 00 00 02 81 0B 00 03 87")

    fun eqCommand(mode: EqPreset): ByteArray = when (mode) {
        EqPreset.CLASSIC -> EQ_CLASSIC
        EqPreset.JAPANESE -> EQ_JAPANESE
        EqPreset.INSTRUMENT -> EQ_INSTRUMENT
        EqPreset.FRESH -> EQ_FRESH
    }

    // ==================== 游戏模式 ====================

    val GAME_MODE_ON: ByteArray  = hexToBytes("08 EE 00 00 00 06 83 0B 00 00 8A")
    val GAME_MODE_OFF: ByteArray = hexToBytes("08 EE 00 00 00 06 83 0B 00 01 8B")

    fun gameModeCommand(enabled: Boolean): ByteArray =
        if (enabled) GAME_MODE_ON else GAME_MODE_OFF

    // ==================== 查找耳机 ====================

    val FIND_LEFT_ON: ByteArray  = hexToBytes("08 EE 00 00 00 0E 82 0C 00 00 01 93")
    val FIND_RIGHT_ON: ByteArray = hexToBytes("08 EE 00 00 00 0E 82 0C 00 01 01 94")
    val FIND_ALL_OFF: ByteArray  = hexToBytes("08 EE 00 00 00 0E 82 0C 00 03 00 95")

    // ==================== 查询指令 ====================

    val QUERY_ANC: ByteArray         = hexToBytes("08 EE 00 00 00 06 02 0A 00 08")
    val QUERY_ANC_DEPTH: ByteArray   = hexToBytes("08 EE 00 00 00 06 07 0A 00 0D")
    val QUERY_TRANS_LEVEL: ByteArray = hexToBytes("08 EE 00 00 00 06 04 0A 00 0A")
    val QUERY_EQ: ByteArray          = hexToBytes("08 EE 00 00 00 02 01 0A 00 03")
    val QUERY_GAME_MODE: ByteArray   = hexToBytes("08 EE 00 00 00 06 03 0A 00 09")
    val QUERY_BATTERY: ByteArray     = hexToBytes("08 EE 00 00 00 01 01 0A 00 02")

    val STATUS_QUERY_SEQUENCE: Array<ByteArray> = arrayOf(
        QUERY_BATTERY,
        QUERY_ANC,
        QUERY_ANC_DEPTH,
        QUERY_TRANS_LEVEL,
        QUERY_EQ,
        QUERY_GAME_MODE
    )

    // ==================== 工具方法 ====================

    /**
     * 将空格分隔的十六进制字符串转为 ByteArray。
     * 例如 "08 EE 00" → byteArrayOf(0x08, 0xEE.toByte(), 0x00)
     */
    private fun hexToBytes(hex: String): ByteArray =
        hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
