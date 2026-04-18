@file:Suppress("unused")

package com.dohex.hyperrose.protocol

import com.dohex.hyperrose.bluetooth.protocol.RoseCommandSet

object RosePackets {
    val ANC_NOISE_CANCEL = RoseCommandSet.ANC_NOISE_CANCEL
    val ANC_WIND_NOISE = RoseCommandSet.ANC_WIND_NOISE
    val ANC_NORMAL = RoseCommandSet.ANC_NORMAL
    val ANC_TRANSPARENT = RoseCommandSet.ANC_TRANSPARENT

    val DEPTH_LIGHT = RoseCommandSet.DEPTH_LIGHT
    val DEPTH_MEDIUM = RoseCommandSet.DEPTH_MEDIUM
    val DEPTH_DEEP = RoseCommandSet.DEPTH_DEEP

    val TRANS_COMFORTABLE = RoseCommandSet.TRANS_COMFORTABLE
    val TRANS_VOCAL = RoseCommandSet.TRANS_VOCAL
    val TRANS_STANDARD = RoseCommandSet.TRANS_STANDARD

    val EQ_CLASSIC = RoseCommandSet.EQ_CLASSIC
    val EQ_JAPANESE = RoseCommandSet.EQ_JAPANESE
    val EQ_INSTRUMENT = RoseCommandSet.EQ_INSTRUMENT
    val EQ_FRESH = RoseCommandSet.EQ_FRESH

    val GAME_MODE_ON = RoseCommandSet.GAME_MODE_ON
    val GAME_MODE_OFF = RoseCommandSet.GAME_MODE_OFF

    val FIND_LEFT_ON = RoseCommandSet.FIND_LEFT_ON
    val FIND_RIGHT_ON = RoseCommandSet.FIND_RIGHT_ON
    val FIND_ALL_OFF = RoseCommandSet.FIND_ALL_OFF

    val QUERY_ANC = RoseCommandSet.QUERY_ANC
    val QUERY_ANC_DEPTH = RoseCommandSet.QUERY_ANC_DEPTH
    val QUERY_TRANS_LEVEL = RoseCommandSet.QUERY_TRANS_LEVEL
    val QUERY_EQ = RoseCommandSet.QUERY_EQ
    val QUERY_GAME_MODE = RoseCommandSet.QUERY_GAME_MODE
    val QUERY_BATTERY = RoseCommandSet.QUERY_BATTERY

    fun ancCommand(mode: com.dohex.hyperrose.model.AncMode): ByteArray = RoseCommandSet.ancCommand(mode)
    fun ancDepthCommand(depth: com.dohex.hyperrose.model.AncDepth): ByteArray = RoseCommandSet.ancDepthCommand(depth)
    fun transLevelCommand(level: com.dohex.hyperrose.model.TransLevel): ByteArray = RoseCommandSet.transLevelCommand(level)
    fun eqCommand(mode: com.dohex.hyperrose.model.EqMode): ByteArray = RoseCommandSet.eqCommand(mode)
    fun gameModeCommand(enabled: Boolean): ByteArray = RoseCommandSet.gameModeCommand(enabled)
}
