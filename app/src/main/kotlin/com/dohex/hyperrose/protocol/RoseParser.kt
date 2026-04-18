package com.dohex.hyperrose.protocol

import com.dohex.hyperrose.bluetooth.protocol.RoseResponse
import com.dohex.hyperrose.bluetooth.protocol.RoseResponseParser

object RoseParser {
    fun parse(data: ByteArray): ParseResult {
        return when (val result = RoseResponseParser.parse(data)) {
            is RoseResponse.Battery -> ParseResult.Battery(result.info)
            is RoseResponse.Anc -> ParseResult.Anc(result.mode)
            is RoseResponse.AncDepthChanged -> ParseResult.AncDepthResult(result.depth)
            is RoseResponse.TransparencyChanged -> ParseResult.TransLevelResult(result.level)
            is RoseResponse.Eq -> ParseResult.Eq(result.mode)
            is RoseResponse.GameMode -> ParseResult.GameMode(result.enabled)
            is RoseResponse.Unknown -> ParseResult.Unknown
        }
    }
}

sealed class ParseResult {
    data class Anc(val mode: com.dohex.hyperrose.model.AncMode) : ParseResult()
    data class AncDepthResult(val depth: com.dohex.hyperrose.model.AncDepth) : ParseResult()
    data class TransLevelResult(val level: com.dohex.hyperrose.model.TransLevel) : ParseResult()
    data class Eq(val mode: com.dohex.hyperrose.model.EqMode) : ParseResult()
    data class GameMode(val enabled: Boolean) : ParseResult()
    data class Battery(val info: com.dohex.hyperrose.model.TwsBatteryInfo) : ParseResult()
    data object Unknown : ParseResult()
}
