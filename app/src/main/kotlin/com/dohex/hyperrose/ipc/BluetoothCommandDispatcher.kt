package com.dohex.hyperrose.ipc

import android.content.Context
import android.content.Intent
import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel

/**
 * App/Popup 侧发送控制广播到 com.android.bluetooth 进程。
 */
object BluetoothCommandDispatcher {
    fun setAnc(context: Context, mode: AncMode) {
        send(context, HyperRoseIpc.SET_ANC) {
            putExtra(HyperRoseIpc.EXTRA_MODE, mode.name)
        }
    }

    fun setAncDepth(context: Context, depth: AncDepth) {
        send(context, HyperRoseIpc.SET_ANC_DEPTH) {
            putExtra(HyperRoseIpc.EXTRA_DEPTH, depth.name)
        }
    }

    fun setTransLevel(context: Context, level: TransparencyLevel) {
        send(context, HyperRoseIpc.SET_TRANS_LEVEL) {
            putExtra(HyperRoseIpc.EXTRA_LEVEL, level.name)
        }
    }

    fun setEq(context: Context, mode: EqPreset) {
        send(context, HyperRoseIpc.SET_EQ) {
            putExtra(HyperRoseIpc.EXTRA_MODE, mode.name)
        }
    }

    fun setGameMode(context: Context, enabled: Boolean) {
        send(context, HyperRoseIpc.SET_GAME_MODE) {
            putExtra(HyperRoseIpc.EXTRA_ENABLED, enabled)
        }
    }

    fun findLeft(context: Context) {
        send(context, HyperRoseIpc.FIND_EARPHONE) {
            putExtra(HyperRoseIpc.EXTRA_SIDE, HyperRoseIpc.SIDE_LEFT)
        }
    }

    fun findRight(context: Context) {
        send(context, HyperRoseIpc.FIND_EARPHONE) {
            putExtra(HyperRoseIpc.EXTRA_SIDE, HyperRoseIpc.SIDE_RIGHT)
        }
    }

    fun stopFind(context: Context) {
        send(context, HyperRoseIpc.FIND_EARPHONE) {
            putExtra(HyperRoseIpc.EXTRA_SIDE, HyperRoseIpc.SIDE_STOP)
        }
    }

    fun refreshStatus(context: Context) {
        send(context, HyperRoseIpc.REFRESH_STATUS)
    }

    fun disconnectGatt(context: Context) {
        send(context, HyperRoseIpc.DISCONNECT_GATT)
    }

    private fun send(context: Context, action: String, extras: (Intent.() -> Unit)? = null) {
        val intent = Intent(action).apply {
            setPackage(HyperRoseIpc.PACKAGE_BLUETOOTH)
            extras?.invoke(this)
        }
        context.sendBroadcast(intent)
    }
}
