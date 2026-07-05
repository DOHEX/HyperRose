package com.dohex.hyperrose.service

import android.bluetooth.BluetoothDevice
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel

/** Control API shared by [StandaloneGattClient] and [StandaloneRfcommClient].
 *  Allows [DeviceControlStore] to route commands without duplicating when-branches. */
interface StandaloneClient {
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
