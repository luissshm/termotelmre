package com.sebyone.termotelmre

data class DeviceStatusReport(
    val timestamp: Long, val heat: Int, val climateTemp: Int, val climateHumidity: Int,
    val rumor: Int, val daylight: Int, val presence: Int, val fan: Int,
    val source: Int, val charging: Int, val charge: Int, val memory: Int, val link: Int
)