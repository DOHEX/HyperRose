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

/** Timing parameters for status queries and battery polling. */
data class GattTiming(
    val initialStatusQueryDelayMs: Long,
    val statusQueryStepDelayMs: Long,
    val batteryPollIntervalMs: Long,
)

/** Declares which features and enum values the device supports. */
data class DeviceCapabilities(
    val supportedAncModes: Set<AncMode>,
    val supportedAncDepths: Set<AncDepth>,
    val supportedTransLevels: Set<TransparencyLevel>,
    val supportedEqPresets: Set<EqPreset>,
    val hasGameMode: Boolean,
    val hasFindEarphone: Boolean,
)

/** Protocol layer: command encoding + response decoding for one device model. */
interface DeviceProtocol {
    // --- commands ---
    fun ancCommand(mode: AncMode): ByteArray
    fun ancDepthCommand(depth: AncDepth): ByteArray
    fun transLevelCommand(level: TransparencyLevel): ByteArray
    fun eqCommand(mode: EqPreset): ByteArray
    fun gameModeCommand(enabled: Boolean): ByteArray
    val findLeftOn: ByteArray
    val findRightOn: ByteArray
    val findAllOff: ByteArray

    // --- queries ---
    val queryBattery: ByteArray
    val queryAnc: ByteArray
    val queryAncDepth: ByteArray
    val queryTransLevel: ByteArray
    val queryEq: ByteArray
    val queryGameMode: ByteArray
    val statusQuerySequence: List<ByteArray>

    // --- response parsing ---
    fun parseResponse(data: ByteArray): DeviceResponse
}

/** Parsed response from a device. Device-agnostic — all profiles emit these same subtypes. */
sealed class DeviceResponse {
    data class Battery(val info: TwsBatteryState) : DeviceResponse()
    data class Anc(val mode: AncMode) : DeviceResponse()
    data class AncDepthChanged(val depth: AncDepth) : DeviceResponse()
    data class TransparencyChanged(val level: TransparencyLevel) : DeviceResponse()
    data class Eq(val mode: EqPreset) : DeviceResponse()
    data class GameMode(val enabled: Boolean) : DeviceResponse()
    data object Unknown : DeviceResponse()
}

/** Complete device-model profile: identification, GATT, protocol, capabilities. */
interface DeviceProfile {
    val id: String
    val displayName: String
    val nameKeywords: List<String>
    val gattSpec: GattSpec
    val timing: GattTiming
    val protocol: DeviceProtocol
    val capabilities: DeviceCapabilities

    /** Case-insensitive substring match against [deviceName]. */
    fun matchesDeviceName(deviceName: String): Boolean =
        nameKeywords.any { deviceName.contains(it, ignoreCase = true) }
}
