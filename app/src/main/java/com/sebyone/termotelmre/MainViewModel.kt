package com.sebyone.termotelmre

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sebyone.daas.DaasManager
import com.sebyone.termotelmre.DeviceStatusDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class MainViewModel : ViewModel(), DaasManager.dynamicListener {

    private val TAG = "MRE_LOG"

    // Constants from your original code
    private val localDin = 102L
    private val deviceSid = 100L
    private val cloudNodeDin = 103L
    private val expectedFanUpDin = 0L // UPDATE THIS IF YOU KNOW THE DIN, or leave 0 to accept any
    private val cloudUri = "158.220.97.43:3030"

    // UI State: A list of logs to display on screen
    val consoleLogs = mutableStateListOf<String>()

    fun appendLog(msg: String) {
        Log.d(TAG, msg)
        // Ensure UI updates happen on the main thread, though Compose snapshots usually handle this
        viewModelScope.launch(Dispatchers.Main) {
            consoleLogs.add(0, msg) // Add to top of list
        }
    }

    fun startSequence() {
        appendLog("--- STARTING SEQUENCE ---")

        val localIp = getLocalIpAddress()
        val uriInet = "$localIp:3031"
        val driverBle: Byte = 4 // Assuming BLE is 2, update if different (DaasTypes.Link.BLE.value)
        val driverInet4: Byte = 2 // Assuming INET4 is 1, update if different (DaasTypes.Link.INET4.value)

        DaasManager.ddoCallback = this

        // 1. Start Agent
        appendLog("1. Starting Agent (din=$localDin, ble_driver=$driverBle)")
        DaasManager.startAgent(sid = deviceSid, din = localDin, localUri = "1ab2ddff", driver = driverBle)

        // 2. Enable INET4 Driver
        appendLog("2. Enabling INET4 Driver (uri=$uriInet)")
        DaasManager.nativeEnableDriver(uriInet, driverInet4)

        // 3. Map Cloud Node
        appendLog("3. Mapping Cloud Node (din=$cloudNodeDin, uri=$cloudUri)")
        DaasManager.nativeMap(cloudNodeDin, cloudUri, driverInet4)

        // Start the Daas Background Loop
        Thread { DaasManager.loop() }.start()

        // 4. Start Discovery
        appendLog("4. Starting Discovery for SID=$deviceSid")
        Thread {
            // Firing discovery a few times just to be sure, like your original code
            for (i in 1..3) {
                DaasManager.discovery(driverBle, deviceSid)
                Thread.sleep(2000)
            }
        }.start()
    }

    // --- DaaS Callbacks ---

    override fun onNodeDiscovered(din: Long) {
        appendLog("-> Node Discovered: DIN=$din")
    }

    override fun onDinAccepted(din: Long) {
        appendLog("-> DIN Accepted: $din. Stopping discovery logic.")
        // Note: You don't explicitly need to stop discovery if you don't call it again,
        // but this is where you'd set your `discoveryRunning = false`
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
    override fun onAutoPull(origin: Long, value: Int) { /* Ignore for MRE */ }
    override fun onLogDDOReceived(origin: Long, timestamp: Int, logId: Int, logValue: Int) { /* Ignore for MRE */ }

    private fun getLocalIpAddress(): String {
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress ?: "0.0.0.0"
    }
}