package com.dohex.hyperrose.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.dohex.hyperrose.hook.HyperRoseModuleEntry.Companion.TAG
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.TransportSpec
import io.github.libxposed.api.XposedModule
import java.io.IOException

/**
 * Bluetooth Classic RFCOMM 传输实现 — 用于不支持 BLE GATT 的设备（如 JieLi AC697 方案）。
 */
@SuppressLint("MissingPermission")
class RfcommDeviceSession(
    context: Context,
    module: XposedModule,
    profile: DeviceProfile,
) : DeviceSession(context, module, profile) {

    private var dataSocket: BluetoothSocket? = null
    private var readerThread: Thread? = null
    private var running = false

    override fun connect(device: BluetoothDevice) {
        connectedDevice = device
        module.log(Log.INFO, TAG, "RfcommDeviceSession: connecting to ${device.address}")

        val transport = profile.transport as TransportSpec.Rfcomm
        try {
            dataSocket = device.createRfcommSocketToServiceRecord(transport.dataChannelUuid)
            dataSocket!!.connect()
            module.log(Log.INFO, TAG, "RfcommDeviceSession: RFCOMM connected")
            registerRefreshReceiver()
            startReader()
            queryAllStatus()
        } catch (e: IOException) {
            module.log(Log.ERROR, TAG, "RfcommDeviceSession: connect failed", e)
            disconnect()
        }
    }

    override fun disconnect() {
        running = false
        readerThread?.interrupt()
        readerThread = null
        try {
            dataSocket?.close()
        } catch (_: IOException) {}
        dataSocket = null
        connectedDevice = null
        handler.removeCallbacksAndMessages(null)
        currentBattery = null
        currentAnc = null
        currentAncDepth = null
        currentTransLevel = null
        currentEq = null
        currentGameMode = null
        currentLowLatency = null
        module.log(Log.INFO, TAG, "RfcommDeviceSession: disconnected")
    }

    override fun sendCommand(packet: ByteArray) {
        try {
            dataSocket?.outputStream?.write(packet)
            module.log(Log.DEBUG, TAG, "→ ${packet.toHexString()}")
        } catch (e: IOException) {
            module.log(Log.ERROR, TAG, "RfcommDeviceSession: send failed", e)
        }
    }

    private fun startReader() {
        running = true
        readerThread = Thread {
            val buf = ByteArray(512)
            val input = dataSocket!!.inputStream
            var frameBuf = ByteArray(0)

            while (running) {
                try {
                    val n = input.read(buf)
                    if (n < 0) break
                    frameBuf += buf.copyOf(n)

                    while (frameBuf.size >= 4) {
                        val aaIdx = frameBuf.indexOf(0xAA.toByte())
                        if (aaIdx < 2) {
                            if (aaIdx == -1) break
                            frameBuf = frameBuf.copyOfRange(aaIdx + 1, frameBuf.size)
                            continue
                        }
                        val frameEnd = aaIdx + 1
                        val frame = frameBuf.copyOfRange(0, frameEnd)
                        if (verifyChecksum(frame)) {
                            handler.post { handleResponse(frame) }
                            frameBuf = frameBuf.copyOfRange(frameEnd, frameBuf.size)
                        } else {
                            frameBuf = frameBuf.copyOfRange(1, frameBuf.size)
                        }
                    }
                } catch (e: IOException) {
                    if (running) {
                        module.log(Log.ERROR, TAG, "RfcommDeviceSession: read error", e)
                    }
                    break
                }
            }
        }.apply {
            name = "RfcommReader"
            isDaemon = true
            start()
        }
    }

    private fun verifyChecksum(frame: ByteArray): Boolean {
        if (frame.size < 4) return false
        val ckPos = frame.size - 2
        val expectedCk = (frame.copyOfRange(0, ckPos).sum() and 0xFF).toByte()
        return frame[ckPos] == expectedCk
    }
}
