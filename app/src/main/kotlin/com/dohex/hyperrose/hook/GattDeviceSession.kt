package com.dohex.hyperrose.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.dohex.hyperrose.hook.HyperRoseModuleEntry.Companion.TAG
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.TransportSpec
import io.github.libxposed.api.XposedModule

/**
 * BLE GATT 传输实现 — 在 com.android.bluetooth 进程内运行。
 */
@SuppressLint("MissingPermission")
class GattDeviceSession(
    context: Context,
    module: XposedModule,
    profile: DeviceProfile,
) : DeviceSession(context, module, profile) {

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private val gattSpec: TransportSpec.Gatt
        get() = profile.transport as TransportSpec.Gatt

    override fun connect(device: BluetoothDevice) {
        connectedDevice = device
        module.log(Log.INFO, TAG, "GattDeviceSession: connecting to ${device.address}")
        registerRefreshReceiver()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    override fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        connectedDevice = null
        currentBattery = null
        currentAnc = null
        currentAncDepth = null
        currentTransLevel = null
        currentEq = null
        currentGameMode = null
        currentLowLatency = null
    }

    override fun sendCommand(packet: ByteArray, description: String) {
        val char = writeChar ?: run {
            module.log(Log.WARN, TAG, "!!! sendCommand dropped: writeChar is null ($description)")
            return
        }
        val g = gatt ?: run {
            module.log(Log.WARN, TAG, "!!! sendCommand dropped: gatt is null ($description)")
            return
        }
        val hex = packet.toHexString()
        module.log(Log.DEBUG, TAG, "→ $hex")
        logTx(hex, description)
        char.value = packet
        if (!g.writeCharacteristic(char)) {
            module.log(Log.WARN, TAG, "!!! writeCharacteristic returned false ($description)")
        }
    }

    // ==================== GATT Callback ====================

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    module.log(Log.INFO, TAG, "GattDeviceSession: connected, discovering services")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    module.log(Log.INFO, TAG, "GattDeviceSession: disconnected")
                    handler.removeCallbacksAndMessages(null)
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    module.log(
                        Log.ERROR,
                        TAG,
                        "GattDeviceSession: service discovery failed: $status"
                    )
                    return
                }

                val service = gatt.getService(gattSpec.serviceUuid)
                if (service == null) {
                    module.log(
                        Log.ERROR,
                        TAG,
                        "GattDeviceSession: service ${gattSpec.serviceUuid} not found"
                    )
                    return
                }

                writeChar = service.getCharacteristic(gattSpec.writeCharUuid)
                if (writeChar == null) {
                    module.log(
                        Log.ERROR,
                        TAG,
                        "GattDeviceSession: write char ${gattSpec.writeCharUuid} not found"
                    )
                    return
                }
                @Suppress("DEPRECATION")
                writeChar?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                val notifyChar = service.getCharacteristic(gattSpec.notifyCharUuid)
                if (notifyChar == null) {
                    module.log(
                        Log.ERROR,
                        TAG,
                        "GattDeviceSession: notify char ${gattSpec.notifyCharUuid} not found"
                    )
                    return
                }

                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(gattSpec.cccdUuid)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }

                module.log(Log.INFO, TAG, "GattDeviceSession: GATT ready, querying initial status")
                sendCommand(profile.protocol.queryBattery)
                handler.postDelayed(
                    { queryAllStatus() },
                    profile.gattTiming?.initialStatusQueryDelayMs ?: 120L
                )
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                val data = characteristic.value ?: return
                handleResponse(data)
            }
        }
}
