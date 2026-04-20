package org.simpleble.android

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
class SimpleBleServer(private val context: Context, private val nativeHandle: Long) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null

    private val characteristics = mutableMapOf<String, BluetoothGattCharacteristic>()
    private val subscribedDevices = mutableMapOf<String, BluetoothDevice>()
    private val services = mutableMapOf<String, BluetoothGattService>()

    // =============================================================
    // JNI NATIVE CALLBACKS (These link back to your C++ functions)
    // =============================================================
    private external fun nativeOnReady(handle: Long)
    private external fun nativeOnRead(handle: Long, charUuid: String, centralId: String): ByteArray?
    private external fun nativeOnWrite(handle: Long, charUuid: String, data: ByteArray, centralId: String)

    // =============================================================
    // ANDROID BLUETOOTH SERVER CALLBACKS
    // =============================================================
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribedDevices.remove(device.address)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Trigger C++ read callback
            val data = nativeOnRead(nativeHandle, characteristic.uuid.toString(), device.address) ?: ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            // Trigger C++ write callback
            nativeOnWrite(nativeHandle, characteristic.uuid.toString(), value, device.address)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            // Handle Phone Subscribing/Unsubscribing for Notifications
            if (BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentEquals(value)) {
                subscribedDevices[device.address] = device
            } else if (BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE.contentEquals(value)) {
                subscribedDevices.remove(device.address)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    init {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        // Break the C++ deadlock! Fire ready signal after C++ init() finishes
        Handler(Looper.getMainLooper()).post {
            nativeOnReady(nativeHandle)
        }
    }

    // =============================================================
    // METHODS CALLED BY YOUR C++ JNI
    // =============================================================
    fun addCharacteristic(serviceUuid: String, charUuid: String, canRead: Boolean, canWrite: Boolean, canNotify: Boolean) {
        var properties = 0
        var permissions = 0

        // Kotlin parameters are immutable (val), so we create safe local variables
        val safeServiceUuid = serviceUuid.lowercase()
        val safeCharUuid = charUuid.lowercase()

        if (canRead) {
            properties = properties or BluetoothGattCharacteristic.PROPERTY_READ
            permissions = permissions or BluetoothGattCharacteristic.PERMISSION_READ
        }
        if (canWrite) {
            properties = properties or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            permissions = permissions or BluetoothGattCharacteristic.PERMISSION_WRITE
        }
        if (canNotify) {
            properties = properties or BluetoothGattCharacteristic.PROPERTY_NOTIFY
        }

        val characteristic = BluetoothGattCharacteristic(UUID.fromString(safeCharUuid), properties, permissions)

        // Android requires a specific CCCD descriptor for Notify to work!
        if (canNotify) {
            val cccd = BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            characteristic.addDescriptor(cccd)
        }

        characteristics[safeCharUuid] = characteristic

        var service = services[safeServiceUuid]
        if (service == null) {
            service = BluetoothGattService(UUID.fromString(safeServiceUuid), BluetoothGattService.SERVICE_TYPE_PRIMARY)
            services[safeServiceUuid] = service
        }
        service.addCharacteristic(characteristic)
    }

    fun startAdvertising(name: String, serviceUuid: String) {
        val safeServiceUuid = serviceUuid.lowercase()
        Log.i("SimpleBleServer", "startAdvertising called by C++! Name: $name, UUID: $safeServiceUuid")

        // Fetch the advertiser dynamically
        val leAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            Log.e("SimpleBleServer", "FATAL: BluetoothLeAdvertiser is null! Is Bluetooth turned on?")
            return
        }

        services.values.forEach { gattServer?.addService(it) }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .build()

        // Set IncludeDeviceName to FALSE to avoid the 31-byte limit crash
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid.fromString(safeServiceUuid))
            .build()

        // Catch Android 12 Security Exceptions
        try {
            leAdvertiser.startAdvertising(settings, data, advertiseCallback)
            Log.i("SimpleBleServer", "Advertising command sent to Android radio.")
        } catch (e: SecurityException) {
            Log.e("SimpleBleServer", "SECURITY EXCEPTION: You are missing runtime BLUETOOTH_ADVERTISE permissions! ${e.message}")
        } catch (e: Exception) {
            Log.e("SimpleBleServer", "UNKNOWN ERROR starting advertisement: ${e.message}")
        }
    }

    fun stopAdvertising() {
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
    }

    fun notify(charUuid: String, data: ByteArray, targetId: String) {
        val safeCharUuid = charUuid.lowercase()
        val char = characteristics[safeCharUuid] ?: run {
            Log.e("SimpleBleServer", "Cannot notify: UUID $safeCharUuid not found in characteristics map.")
            return
        }

        char.value = data

        if (targetId.isNotEmpty()) {
            val device = subscribedDevices[targetId]
            if (device != null) gattServer?.notifyCharacteristicChanged(device, char, false)
        } else {
            // Broadcast to all subscribed phones
            subscribedDevices.values.forEach { device ->
                gattServer?.notifyCharacteristicChanged(device, char, false)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("SimpleBleServer", "Advertising started successfully")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("SimpleBleServer", "Advertising failed: $errorCode")
        }
    }
}