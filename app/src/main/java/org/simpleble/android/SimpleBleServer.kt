package org.simpleble.android

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission") // Ensure your app requests BLE permissions!
class SimpleBleServer(context: Context, private val nativeHandle: Long) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null

    private external fun onNativeReadRequest(handle: Long, charUuid: String): ByteArray
    private external fun onNativeWriteRequest(handle: Long, charUuid: String, data: ByteArray)

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, 
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = onNativeReadRequest(nativeHandle, characteristic.uuid.toString())
            
            gattServer?.sendResponse(
                device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data ?: ByteArray(0)
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, 
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            onNativeWriteRequest(nativeHandle, characteristic.uuid.toString(), value)
            
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    init {
        // Start the GATT server as soon as the C++ object creates this Kotlin object
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
    }

    fun addCharacteristic(sUuid: String, cUuid: String, canRead: Boolean, canWrite: Boolean) {
        var props = 0
        var perms = 0

        if (canRead) {
            props = props or BluetoothGattCharacteristic.PROPERTY_READ
            perms = perms or BluetoothGattCharacteristic.PERMISSION_READ
        }
        if (canWrite) {
            props = props or (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
            perms = perms or BluetoothGattCharacteristic.PERMISSION_WRITE
        }

        val characteristic = BluetoothGattCharacteristic(UUID.fromString(cUuid), props, perms)
        val service = BluetoothGattService(UUID.fromString(sUuid), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
    }

    fun startAdvertising(name: String, serviceUuidStr: String) {
        val advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID.fromString(serviceUuidStr)))
            .build()

        advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i("SimpleBleServer", "Broadcasting successfully!")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("SimpleBleServer", "Broadcast failed with error code: $errorCode")
            }
        })
    }
}