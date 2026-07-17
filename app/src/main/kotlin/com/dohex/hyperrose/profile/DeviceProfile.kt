package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import java.util.UUID

/** Per-device-model BLE GATT service & characteristic UUIDs. */
data class GattSpec(
    val serviceUuid: UUID,
    val writeCharUuid: UUID,
    val notifyCharUuid: UUID,
    val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
)

/** Timing parameters for status queries and periodic refresh. */
data class GattTiming(
    val initialStatusQueryDelayMs: Long,
    val statusQueryStepDelayMs: Long,
    val statusRefreshIntervalMs: Long,
)

data class DeviceCapabilities(
    val supportedAncModes: Set<AncMode>,
    val supportedAncDepths: Set<AncDepth>,
    val supportedTransLevels: Set<TransparencyLevel>,
    val supportedEqPresets: Set<EqPreset>,
    val hasGameMode: Boolean,
    val hasLowLatency: Boolean,
    val hasFindEarphone: Boolean,
)

/** Protocol layer: command encoding + response decoding for one device model. */
interface DeviceProtocol {
    // --- commands ---
    fun ancCommand(mode: AncMode): ByteArray
    fun ancDepthCommand(depth: AncDepth): ByteArray = unsupported()
    fun transLevelCommand(level: TransparencyLevel): ByteArray = unsupported()
    fun eqCommand(mode: EqPreset): ByteArray = unsupported()
    fun gameModeCommand(enabled: Boolean): ByteArray = unsupported()
    fun lowLatencyCommand(enabled: Boolean): ByteArray = unsupported()
    val findLeftOn: ByteArray get() = unsupported()
    val findRightOn: ByteArray get() = unsupported()
    val findAllOff: ByteArray get() = unsupported()

    // --- queries ---
    val queryBattery: ByteArray
    val queryAnc: ByteArray
    val queryAncDepth: ByteArray get() = unsupported()
    val queryTransLevel: ByteArray get() = unsupported()
    val queryEq: ByteArray get() = unsupported()
    val queryGameMode: ByteArray get() = unsupported()
    val queryLowLatency: ByteArray get() = unsupported()
    val statusQuerySequence: List<ByteArray>

    // --- response parsing ---
    fun parseResponse(data: ByteArray): List<DeviceResponse>
}

private fun unsupported(): Nothing =
    throw UnsupportedOperationException("Not supported by this device profile")

/** Parsed response from a device. Device-agnostic — all profiles emit these same subtypes. */
sealed class DeviceResponse {
    data class Battery(val info: TwsBatteryState) : DeviceResponse()
    data class Anc(val mode: AncMode) : DeviceResponse()
    data class AncDepthChanged(val depth: AncDepth) : DeviceResponse()
    data class TransparencyChanged(val level: TransparencyLevel) : DeviceResponse()
    data class Eq(val mode: EqPreset) : DeviceResponse()
    data class GameMode(val enabled: Boolean) : DeviceResponse()
    data object Unknown : DeviceResponse()
    data class LowLatencyChanged(val enabled: Boolean) : DeviceResponse()
}

/** 传输层规格：描述如何连接到设备。 */
sealed class TransportSpec {
    /** BLE GATT 传输 */
    data class Gatt(
        val serviceUuid: UUID,
        val writeCharUuid: UUID,
        val notifyCharUuid: UUID,
        val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
    ) : TransportSpec()

    /** Bluetooth Classic RFCOMM 传输 */
    data class Rfcomm(
        val dataChannelUuid: UUID,
        val sppChannelUuid: UUID? = null,
    ) : TransportSpec()
}

/** Complete device-model profile: identification, transport, protocol, capabilities. */
interface DeviceProfile {
    val id: String
    val displayName: String
    val nameKeywords: List<String>
    val transport: TransportSpec
    val protocol: DeviceProtocol
    val capabilities: DeviceCapabilities

    /** 仅 GATT 传输时有意义；RFCOMM 返回 null */
    val gattTiming: GattTiming? get() = null

    /** 帧是否包含 CK 校验字节（FF SEQ CMD PAYLOAD CK AA / DD SEQ TYPE PAYLOAD CK AA） */
    val hasFrameChecksum: Boolean get() = true

    /** 调试页 hex 输入框提示文案 */
    val debugHexHint: String get() = "HEX 指令"

    /** 调试页预设快捷指令 */
    val debugQuickCommands: List<Pair<String, ByteArray>> get() = emptyList()

    /** Case-insensitive substring match against [deviceName]. */
    fun matchesDeviceName(deviceName: String): Boolean =
        nameKeywords.any { deviceName.contains(it, ignoreCase = true) }
}
