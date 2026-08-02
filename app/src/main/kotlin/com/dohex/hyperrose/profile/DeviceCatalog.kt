package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.DeviceVisuals
import com.dohex.hyperrose.profile.budsfeel_mk2.BudsFeelMk2Profile

/** Complete application metadata for one supported device model. */
data class DeviceDescriptor(
    val profile: DeviceProfile,
    val visuals: DeviceVisuals?,
) {
    val id: String get() = profile.id
    val displayName: String get() = profile.displayName

    fun matchesDeviceName(deviceName: String): Boolean = profile.matchesDeviceName(deviceName)
}

/** Single source of truth for supported device profiles and optional visual metadata. */
object DeviceCatalog {
    val devices: List<DeviceDescriptor> = listOf(
        DeviceDescriptor(EarFeelI5Profile, DeviceVisuals.EARFEEL_I5),
        DeviceDescriptor(EarFeelI7Profile, null),
        DeviceDescriptor(
            BudsFeelMk2Profile,
            DeviceVisuals.BUDSFEEL_MK2,
        ),
        DeviceDescriptor(CeramicsUProfile, null),
    )

    val profiles: List<DeviceProfile> = devices.map { it.profile }

    init {
        validateDevices(devices)
    }

    fun findByName(deviceName: String): DeviceDescriptor? =
        devices.firstOrNull { it.matchesDeviceName(deviceName) }

    fun findById(id: String): DeviceDescriptor? =
        devices.firstOrNull { it.id == id }

    internal fun validateDevices(candidates: List<DeviceDescriptor>) {
        val ids = candidates.map { it.id }
        require(ids.size == ids.distinct().size) {
            "Duplicate device profile id: ${ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}"
        }

        val aliases = mutableMapOf<String, String>()
        candidates.forEach { descriptor ->
            descriptor.profile.nameKeywords.forEach { keyword ->
                val normalized = normalizeDeviceName(keyword)
                val previous = aliases.putIfAbsent(normalized, descriptor.id)
                require(previous == null || previous == descriptor.id) {
                    "Duplicate device alias '$keyword' for profiles '$previous' and '${descriptor.id}'"
                }
            }
            descriptor.visuals?.let { visuals ->
                require(visuals.profileId == descriptor.id) {
                    "Visual metadata '${visuals.profileId}' does not match profile '${descriptor.id}'"
                }
            }
        }
    }
}
