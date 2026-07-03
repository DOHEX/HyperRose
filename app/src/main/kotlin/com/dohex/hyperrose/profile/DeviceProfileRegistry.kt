package com.dohex.hyperrose.profile

object DeviceProfileRegistry {
    /** All known device profiles, ordered by priority (first match wins). */
    val profiles: List<DeviceProfile> = listOf(
        RoseEarfreeI5Profile,
    )

    /** Default profile to display when no device is connected. */
    val defaultProfile: DeviceProfile get() = profiles.first()

    /** Find the first profile whose [DeviceProfile.nameKeywords] match [deviceName]. */
    fun findByName(deviceName: String): DeviceProfile? =
        profiles.firstOrNull { it.matchesDeviceName(deviceName) }
}
