@file:SuppressLint("MissingPermission")

package com.dohex.hyperrose.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dohex.hyperrose.debug.BleLog
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.withLastKnownCaseBattery
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.DeviceResponse
import com.dohex.hyperrose.profile.TransportSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** App-process RFCOMM client for devices using Bluetooth Classic (e.g. BudsFeel MK2).
 *  All state exposed via StateFlow for Compose UI consumption. */
class StandaloneRfcommClient(
    private val context: Context,
    val profile: DeviceProfile,
) : StandaloneClient {
    companion object {
        private const val TAG = "HyperRose.StandaloneRfcommClient"
        private val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
    }

    // Transport spec (non-null after init; profile must have TransportSpec.Rfcomm)
    private val rfcommSpec: TransportSpec.Rfcomm
        get() = profile.transport as TransportSpec.Rfcomm

    // StateFlows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _battery = MutableStateFlow<TwsBatteryState?>(null)
    val battery: StateFlow<TwsBatteryState?> = _battery.asStateFlow()

    private val _ancMode = MutableStateFlow<AncMode?>(null)
    val ancMode: StateFlow<AncMode?> = _ancMode.asStateFlow()

    private val _ancDepth = MutableStateFlow<AncDepth?>(null)
    val ancDepth: StateFlow<AncDepth?> = _ancDepth.asStateFlow()

    private val _transLevel = MutableStateFlow<TransparencyLevel?>(null)
    val transLevel: StateFlow<TransparencyLevel?> = _transLevel.asStateFlow()

    private val _eqMode = MutableStateFlow<EqPreset?>(null)
    val eqMode: StateFlow<EqPreset?> = _eqMode.asStateFlow()

    private val _gameMode = MutableStateFlow<Boolean?>(null)
    val gameMode: StateFlow<Boolean?> = _gameMode.asStateFlow()

    private val _lowLatency = MutableStateFlow<Boolean?>(null)
    val lowLatency: StateFlow<Boolean?> = _lowLatency.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    // Internal state
    private var dataSocket: BluetoothSocket? = null
    private var readerThread: Thread? = null

    @Volatile
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    // ==================== Public methods ====================

    override fun connect(device: BluetoothDevice) {
        _deviceName.value = device.name
        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "Connecting to ${device.address} via RFCOMM")

        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(rfcommSpec.dataChannelUuid)
                socket.connect()
                dataSocket = socket
                handler.post {
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.i(TAG, "RFCOMM connected")
                    startReader()
                    queryAllStatus()
                }
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed", e)
                handler.post {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }.apply {
            name = "RfcommConnect"
            isDaemon = true
            start()
        }
    }

    override fun disconnect() {
        running = false
        readerThread?.interrupt()
        readerThread = null
        runCatching { dataSocket?.close() }
        dataSocket = null
        handler.removeCallbacksAndMessages(null)
        _connectionState.value = ConnectionState.DISCONNECTED
        _battery.value = null
        _ancMode.value = null
        _ancDepth.value = null
        _transLevel.value = null
        _eqMode.value = null
        _gameMode.value = null
        _lowLatency.value = null
        _deviceName.value = null
    }

    fun sendCommand(packet: ByteArray, description: String = "") {
        try {
            dataSocket?.outputStream?.write(packet)
            val hex = packet.toHexString()
            Log.d(TAG, "→ $hex")
            BleLog.log("App", "TX", hex, description, logTimeFormat.format(Date()))
        } catch (e: IOException) {
            Log.e(TAG, "RFCOMM send failed", e)
        }
    }

    override fun refreshStatus() {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        queryAllStatus()
    }

    // Convenience methods
    override fun setAnc(mode: AncMode) =
        sendCommand(profile.protocol.ancCommand(mode), "Set ANC: $mode")

    override fun setAncDepth(depth: AncDepth) =
        sendCommand(profile.protocol.ancDepthCommand(depth), "Set ANC depth: $depth")

    override fun setTransLevel(level: TransparencyLevel) =
        sendCommand(profile.protocol.transLevelCommand(level), "Set transparency: $level")

    override fun setEq(mode: EqPreset) =
        sendCommand(profile.protocol.eqCommand(mode), "Set EQ: $mode")

    override fun setGameMode(enabled: Boolean) =
        sendCommand(profile.protocol.gameModeCommand(enabled), "Set game mode: $enabled")

    override fun setLowLatency(enabled: Boolean) =
        sendCommand(profile.protocol.lowLatencyCommand(enabled), "Set low latency: $enabled")

    override fun findLeft() = sendCommand(profile.protocol.findLeftOn, "Find left")

    override fun findRight() = sendCommand(profile.protocol.findRightOn, "Find right")

    override fun stopFind() = sendCommand(profile.protocol.findAllOff, "Stop find")

    /** Send raw hex command (for debug page). */
    override fun sendRawCommand(hex: String) {
        val normalized = hex.replace(" ", "").replace("\n", "").replace("\r", "")
        if (normalized.isEmpty() || normalized.length % 2 != 0 ||
            !normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        ) {
            Log.w(TAG, "sendRawCommand: invalid hex: $hex")
            return
        }
        val bytes = ByteArray(normalized.length / 2) {
            normalized.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
        sendCommand(bytes, "Raw: $normalized")
    }

    // ==================== Reader thread ====================

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
                    if (running) Log.e(TAG, "RFCOMM read error", e)
                    break
                }
            }

            // Unexpected disconnect
            if (running) {
                handler.post { disconnect() }
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

    // ==================== Response handling ====================

    private fun handleResponse(data: ByteArray) {
        val hex = data.toHexString()
        val result = profile.protocol.parseResponse(data)
        BleLog.log("App", "RX", hex, result.toString(), logTimeFormat.format(Date()))
        when (result) {
            is DeviceResponse.Battery -> {
                Log.d(TAG, "← $hex → $result")
                _battery.value = result.info.withLastKnownCaseBattery(_battery.value)
            }

            is DeviceResponse.Anc -> {
                Log.d(TAG, "← $hex → $result")
                _ancMode.value = result.mode
            }

            is DeviceResponse.AncDepthChanged -> {
                Log.d(TAG, "← $hex → $result")
                _ancDepth.value = result.depth
            }

            is DeviceResponse.TransparencyChanged -> {
                Log.d(TAG, "← $hex → $result")
                _transLevel.value = result.level
            }

            is DeviceResponse.Eq -> {
                Log.d(TAG, "← $hex → $result")
                _eqMode.value = result.mode
            }

            is DeviceResponse.GameMode -> {
                Log.d(TAG, "← $hex → $result")
                _gameMode.value = result.enabled
            }

            is DeviceResponse.LowLatencyChanged -> {
                Log.d(TAG, "← $hex → $result")
                _lowLatency.value = result.enabled
            }

            is DeviceResponse.Unknown -> {
                Log.d(TAG, "← $hex → Unknown")
            }
        }
    }

    // ==================== Status polling ====================

    private fun queryAllStatus() {
        profile.protocol.statusQuerySequence.forEachIndexed { index, query ->
            handler.postDelayed({ sendCommand(query, "Query status") }, 120L * index)
        }
        handler.postDelayed(object : Runnable {
            override fun run() {
                queryAllStatus()
            }
        }, 30_000L)
    }
}

private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
