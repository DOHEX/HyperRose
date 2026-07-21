package com.dohex.hyperrose.service

import android.bluetooth.BluetoothDevice
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.profile.DeviceProfile
import kotlinx.coroutines.flow.StateFlow

enum class StandaloneConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

/** Control API shared by [StandaloneGattClient] and [StandaloneRfcommClient].
 *  Allows [DeviceControlStore] to route commands without duplicating when-branches. */
interface StandaloneClient {
    val profile: DeviceProfile
    val connectionState: StateFlow<StandaloneConnectionState>
    val deviceName: StateFlow<String?>
    val battery: StateFlow<TwsBatteryState?>
    val ancMode: StateFlow<AncMode?>
    val ancDepth: StateFlow<AncDepth?>
    val transLevel: StateFlow<TransparencyLevel?>
    val eqMode: StateFlow<EqPreset?>
    val gameMode: StateFlow<Boolean?>
    val lowLatency: StateFlow<Boolean?>

    fun connect(device: BluetoothDevice)
    fun disconnect()
    fun setAnc(mode: AncMode)
    fun setAncDepth(depth: AncDepth)
    fun setTransLevel(level: TransparencyLevel)
    fun setEq(mode: EqPreset)
    fun setGameMode(enabled: Boolean)
    fun setLowLatency(enabled: Boolean)
    fun findLeft()
    fun findRight()
    fun stopFind()
    fun refreshStatus()
    fun sendRawCommand(hex: String)
}
