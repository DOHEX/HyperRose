package com.dohex.hyperrose.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data class DeviceDetail(
        val address: String,
    ) : Route

    @Serializable
    data class BleDebug(
        val address: String,
    ) : Route

    @Serializable
    data object ThemeSettings : Route

    @Serializable
    data class EarphoneColorSettings(
        val address: String,
        val deviceId: String,
    ) : Route
}

enum class TopLevelTab(
    val pageIndex: Int,
) {
    MODULE(0),
    EARPHONES(1),
    SETTINGS(2),
}

val Route.isTopLevel: Boolean
    get() = this is Route.Main
