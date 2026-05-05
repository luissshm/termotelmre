package com.sebyone.daas

import android.util.Log

object DaasManager {

    init {
        System.loadLibrary("daas_jni")
    }

    private const val TAG = "DaaS"
    // Listener for UI callbacks
    var ddoCallback: Any? = null
    private val acceptedDins = mutableSetOf<Long>()

    fun getListDinAccepted(): List<Long> {
        return acceptedDins.toList()
    }

    fun clearDinAccepted() {
        acceptedDins.clear()
    }



    external fun nativeCreate()
    external fun nativeInit(sid: Long, din: Long): Int
    external fun nativeEnableDriver(uri: String, driver: Byte): Int
    external fun nativePerform(): Int
    external fun nativeSendDDO(din: Long, value: Long, typeset: Int): Int
    external fun nativeListDrivers(): String
    external fun nativeAutoPull(remoteDin: Long)
    external fun nativeDiscovery(driver: Byte, sid: Long)

    external fun nativeSimpleDiscovery(sid: Long): Int

    external fun nativeSetDiscoveryStateFull()
    external fun nativeSetDiscoveryState(state: Int)

    external fun nativeSendDDOBytes(din: Long, payload: ByteArray, typeset: Int): Int
    external fun redirectLogs()
    external fun nativeMap(din: Long, uri: String, driver: Byte)

    external fun nativeLocate(din: Long, timeoutMs: Int = 1000): Int
    external fun nativeUnbindNetwork(): Int

    external fun nativeRemove(din: Long)

    external fun nativeGetSystemStatistics(syscode: Int): Long

    fun startAgent(sid: Long, din: Long, localUri: String, driver: Byte) {
        redirectLogs()

        Log.d(TAG, "Starting agent...")
        nativeCreate()

        val init = nativeInit(sid, din)
        Log.d(TAG, "Initialization result = $init")

        val drivers = nativeListDrivers()
        Log.d(TAG, "Available drivers = $drivers")

        val enable = nativeEnableDriver(localUri, driver)
        Log.d(TAG, "enableDriver result = $enable")
        setDiscoveryState(1)
        Log.d(TAG, "Node ready")


    }

    fun discovery(driver: Byte, sid: Long) {
        val r = nativeDiscovery(driver, sid)
        Log.d(TAG, "discovery result = $r")
    }

    fun setDiscoveryState(state: Int) {
        nativeSetDiscoveryState(state)
    }

    fun sendTestDDO(din: Long, value: Long, typeset: Int) {
        Log.d(TAG, "Sending DDO value=$value")
        val r = nativeSendDDO(din, value, typeset)
        Log.d(TAG, "push result = $r")
    }

    fun loop() {
        nativePerform()
    }

    fun sendBinaryDDO(din: Long, payload: ByteArray, typeset: Int) {
        Log.d(TAG, "Sending binary DDO typeset=$typeset size=${payload.size}")
        val r = nativeSendDDOBytes(din, payload, typeset)
        Log.d(TAG, "binary push result = $r")
    }

    fun map(din: Long, uri: String, driver: Byte) {
        val r = nativeMap(din, uri, driver)
    }

    fun autoPull(remoteDin: Long) {
        nativeAutoPull(remoteDin)
    }

    fun unbindNetwork() {
        nativeUnbindNetwork()
    }

    @JvmStatic
    fun onAutoPull(origin: Long, value: Int) {
        Log.d(TAG, "AUTO-PULL origin=$origin value=$value")
        (ddoCallback as? dynamicListener)
            ?.onAutoPull(origin, value)
    }
    @JvmStatic
    fun onNodeDiscovered(din: Long) {
        Log.d(TAG, "Node discovered from JNI → DIN=$din")

        val listener = ddoCallback as? dynamicListener
        if (listener == null) {
            Log.d(TAG, "No UI listener registered — discovery ignored")
            return
        }

        listener.onNodeDiscovered(din)
    }

    @JvmStatic
    fun onDinAccepted(din: Long) {
        acceptedDins.add(din)
        (ddoCallback as? dynamicListener)?.onDinAccepted(din)
    }


    @JvmStatic
    fun onNetworkConnected(din: Long) {
        (ddoCallback as? dynamicListener)?.onNetworkConnected(din)
    }

    @JvmStatic
    fun onDDOReceivedExtended(origin: Long, typeset: Int, value: Int) {
        Log.d(TAG, "DDO RECEIVED origin=$origin typeset=$typeset value=$value")
        (ddoCallback as? dynamicListener)
            ?.onDDOReceivedExtended(origin, typeset, value)
    }

    @JvmStatic
    fun onLogDDOReceived(origin: Long, timestamp: Int, logId: Int, logValue: Int) {
        Log.d(TAG, "LOG DDO origin=$origin timestamp=$timestamp id=$logId value=$logValue")
        (ddoCallback as? dynamicListener)?.onLogDDOReceived(origin, timestamp, logId, logValue)
    }

    @JvmStatic
    fun onStatusReportReceived(origin: Long, typeset: Int, payload: ByteArray) {
        (ddoCallback as? dynamicListener)?.onStatusReportReceived(origin, typeset, payload)
    }



    interface dynamicListener {

        fun onDinAccepted(din: Long)
        fun onNodeDiscovered(din: Long)
        fun onDDOReceivedExtended(origin: Long, typeset: Int, value: Int)
        fun onAutoPull(origin: Long, value: Int)
        fun onLogDDOReceived(origin: Long, timestamp: Int, logId: Int, logValue: Int)
        fun onNetworkConnected(din: Long)
        fun onStatusReportReceived(origin: Long, typeset: Int, payload: ByteArray)
    }

}