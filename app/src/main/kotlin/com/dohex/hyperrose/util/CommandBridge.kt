package com.dohex.hyperrose.util

import android.content.Context
import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel
import com.dohex.hyperrose.ipc.BluetoothCommandDispatcher

object CommandBridge {
    fun setAnc(context: Context, mode: AncMode) = BluetoothCommandDispatcher.setAnc(context, mode)
    fun setAncDepth(context: Context, depth: AncDepth) = BluetoothCommandDispatcher.setAncDepth(context, depth)
    fun setTransLevel(context: Context, level: TransparencyLevel) = BluetoothCommandDispatcher.setTransLevel(context, level)
    fun setEq(context: Context, mode: EqPreset) = BluetoothCommandDispatcher.setEq(context, mode)
    fun setGameMode(context: Context, enabled: Boolean) = BluetoothCommandDispatcher.setGameMode(context, enabled)
    fun findLeft(context: Context) = BluetoothCommandDispatcher.findLeft(context)
    fun findRight(context: Context) = BluetoothCommandDispatcher.findRight(context)
    fun stopFind(context: Context) = BluetoothCommandDispatcher.stopFind(context)
    fun refreshStatus(context: Context) = BluetoothCommandDispatcher.refreshStatus(context)
    fun disconnectGatt(context: Context) = BluetoothCommandDispatcher.disconnectGatt(context)
}
