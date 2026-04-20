package com.sebyone.termotelmre

object DeviceStatusDecoder {

    fun decode(payload: ByteArray): DeviceStatusReport? {
        if (payload.size < 16) return null

        val timestamp =
            (payload[0].toLong() and 0xFF) or
            ((payload[1].toLong() and 0xFF) shl 8) or
            ((payload[2].toLong() and 0xFF) shl 16) or
            ((payload[3].toLong() and 0xFF) shl 24)

        val heat = payload[4].toInt() // signed int8_t
        val climateTemp = payload[5].toInt() and 0xFF
        val climateHumidity = payload[6].toInt() and 0xFF

        val rumor = payload[7].toInt() and 0xFF
        val daylight = payload[8].toInt() and 0xFF
        val presence = payload[9].toInt() and 0xFF

        val fan = payload[10].toInt() and 0xFF
        val source = payload[11].toInt() and 0xFF
        val charging = payload[12].toInt() and 0xFF
        val charge = payload[13].toInt() and 0xFF
        val memory = payload[14].toInt() and 0xFF
        val link = payload[15].toInt() and 0xFF

        return DeviceStatusReport(
            timestamp = timestamp,
            heat = heat,
            climateTemp = climateTemp,
            climateHumidity = climateHumidity,
            rumor = rumor,
            daylight = daylight,
            presence = presence,
            fan = fan,
            source = source,
            charging = charging,
            charge = charge,
            memory = memory,
            link = link
        )
    }

    fun encode(report: DeviceStatusReport): ByteArray {
        val payload = ByteArray(16)

        payload[0] = (report.timestamp and 0xFF).toByte()
        payload[1] = ((report.timestamp shr 8) and 0xFF).toByte()
        payload[2] = ((report.timestamp shr 16) and 0xFF).toByte()
        payload[3] = ((report.timestamp shr 24) and 0xFF).toByte()

        payload[4] = report.heat.toByte()
        payload[5] = report.climateTemp.toByte()
        payload[6] = report.climateHumidity.toByte()
        payload[7] = report.rumor.toByte()
        payload[8] = report.daylight.toByte()
        payload[9] = report.presence.toByte()
        payload[10] = report.fan.toByte()
        payload[11] = report.source.toByte()
        payload[12] = report.charging.toByte()
        payload[13] = report.charge.toByte()
        payload[14] = report.memory.toByte()
        payload[15] = report.link.toByte()

        return payload
    }
}
