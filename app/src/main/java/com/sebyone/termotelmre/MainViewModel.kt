package com.sebyone.termotelmre

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sebyone.daas.DaasManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class MainViewModel : ViewModel(), DaasManager.dynamicListener {

    private val TAG = "MRE_LOG"

    // UI State
    var localDin by mutableStateOf(102L)
        private set
    var localUri by mutableStateOf("1ab2ddff")
        private set

    private val deviceSid = 100L
    private val cloudNodeDin = 103L
    private val expectedFanUpDin = 0L // UPDATE THIS IF YOU KNOW THE DIN, or leave 0 to accept any
    private val cloudUri = "158.220.97.43:3030"

    // UI State: A list of logs to display on screen
    val consoleLogs = mutableStateListOf<String>()
    val discoveredDins = mutableStateListOf<Long>()

    // Driver constants
    val driverBle: Byte = 4
    val driverInet4: Byte = 2

    fun appendLog(msg: String) {
        Log.d(TAG, msg)
        // Ensure UI updates happen on the main thread
        viewModelScope.launch(Dispatchers.Main) {
            consoleLogs.add(0, msg) // Add to top of list
        }
    }

    fun randomizeDin() {
        localDin = (101..199).random().toLong()
        appendLog("Local DIN randomized to: $localDin")
    }

    fun randomizeUri() {
        val chars = "0123456789abcdef"
        localUri = (1..8).map { chars.random() }.joinToString("")
        appendLog("Local URI randomized to: $localUri")
    }

    fun startSequence() {
        appendLog("--- STARTING SEQUENCE ---")

        val localIp = getLocalIpAddress()
        val uriInet = "$localIp:3031"

        DaasManager.ddoCallback = this

        // 1. Start Agent
        appendLog("1. Starting Agent (din=$localDin, ble_driver=$driverBle, uri=$localUri)")
        DaasManager.startAgent(sid = deviceSid, din = localDin, localUri = localUri, driver = driverBle)

        // 2. Enable INET4 Driver
        appendLog("2. Enabling INET4 Driver (uri=$uriInet)")
        DaasManager.nativeEnableDriver(uriInet, driverInet4)


        // Start the Daas Background Loop
        Thread { DaasManager.loop() }.start()

        appendLog("Agent Loop Started. Use buttons to start discovery.")
    }

    fun triggerDiscovery(driver: Byte) {
        val driverName = if (driver == driverBle) "BLE" else "INET4"
        appendLog("Triggering Discovery ($driverName)...")
        viewModelScope.launch(Dispatchers.IO) {
            DaasManager.discovery(driver, deviceSid)
        }
    }

    fun sendTestDDO(din: Long, value: Byte, typeset: Int) {
        appendLog("Sending DDO to $din: val=$value, type=$typeset")
        viewModelScope.launch(Dispatchers.IO) {
            val r = DaasManager.nativeSendDDO(din, value, typeset)
            appendLog("-> Send DDO result: $r")
        }
    }

    fun mapDevice(din: Long, uri: String, driver: Byte) {
        appendLog("Mapping device: DIN=$din, URI=$uri, Driver=$driver")
        viewModelScope.launch(Dispatchers.IO) {
            DaasManager.nativeMap(din, uri, driver)
        }
    }

    // --- DaaS Callbacks ---

    override fun onNodeDiscovered(din: Long) {
        appendLog("-> Node Discovered: DIN=$din")
    }

    override fun onDinAccepted(din: Long) {
        appendLog("-> DIN Accepted: $din")
        if (!discoveredDins.contains(din)) {
            viewModelScope.launch(Dispatchers.Main) {
                discoveredDins.add(din)
            }
        }
    }

    override fun onStatusReportReceived(origin: Long, typeset: Int, payload: ByteArray) {
        appendLog("-> STATUS REPORT RECEIVED from $origin (typeset=$typeset)")

        // 5 & 6. Decode, Log, Re-encode, Forward (Strictly on IO Thread to prevent JNI blocking)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = DeviceStatusDecoder.decode(payload)
                if (report != null) {
                    appendLog("   [Decoded] Temp: ${report.climateTemp}, Hum: ${report.climateHumidity}, Link: ${report.link}")

                    val newPayload = DeviceStatusDecoder.encode(report)
                    appendLog("   [Forwarding] Sending to Cloud Node $cloudNodeDin...")

                    val result = DaasManager.nativeSendDDOBytes(cloudNodeDin, newPayload, typeset)
                    appendLog("   [Result] Cloud forward result code: $result")
                } else {
                    appendLog("   [Error] Failed to decode payload!")
                }
            } catch (e: Exception) {
                appendLog("   [CRASH] Exception in forwarding: ${e.message}")
            }
        }
    }

    override fun onNetworkConnected(din: Long) { appendLog("-> Network Connected: $din") }
    override fun onDDOReceivedExtended(origin: Long, typeset: Int, value: Int) { appendLog("-> DDO Received: origin=$origin, type=$typeset, val=$value") }
    override fun onAutoPull(origin: Long, value: Int) { appendLog("-> AutoPull Received: origin=$origin, val=$value") }
    override fun onLogDDOReceived(origin: Long, timestamp: Int, logId: Int, logValue: Int) {
        appendLog("-> LOG DDO Received: origin=$origin, ts=$timestamp, id=$logId, val=$logValue")
    }

    private fun getLocalIpAddress(): String {
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress ?: "0.0.0.0"
    }
}