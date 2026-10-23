package com.anitec.platform.devices.domain

import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/** The most recent value a device reported. [recordedAt] is the API date-time, without zone. */
data class DeviceReading(val type: String, val value: Double, val unit: String, val recordedAt: String)

/**
 * An IoT sensor attached to a herd or to a single animal (never both). [type] and [status] keep the API
 * text; [readingCount] and [latest] are summarized from the device metrics.
 */
data class Device(
    val id: Int,
    val name: String,
    val type: String,
    val serialNumber: String,
    val status: String,
    val herdId: Int?,
    val animalId: Int?,
    val latest: DeviceReading? = null,
    val readingCount: Int = 0,
)

data class DeviceDraft(
    val name: String,
    val type: String,
    val serialNumber: String,
    val status: String,
    val herdId: Int?,
    val animalId: Int?,
)

/** Canonical values, as the seed data stores them. The web wrote translated labels; [normalize] maps those back. */
object DeviceTypes {
    const val SCALE = "Scale"
    const val SMART_COLLAR = "SmartCollar"
    const val RFID_TAG = "RFIDTag"
    const val THERMAL_CAMERA = "ThermalCamera"
    const val WEATHER_STATION = "WeatherStation"
    const val ENVIRONMENTAL_SENSOR = "EnvironmentalSensor"
    val all = listOf(SCALE, SMART_COLLAR, RFID_TAG, THERMAL_CAMERA, WEATHER_STATION, ENVIRONMENTAL_SENSOR)

    private val aliases = mapOf(
        "balanza" to SCALE,
        "collar inteligente" to SMART_COLLAR, "smart collar" to SMART_COLLAR, "collar" to SMART_COLLAR,
        "camara termica" to THERMAL_CAMERA, "cámara térmica" to THERMAL_CAMERA, "thermal camera" to THERMAL_CAMERA,
        "arete de identificacion" to RFID_TAG, "arete de identificación" to RFID_TAG, "identification ear tag" to RFID_TAG,
        "estacion meteorologica" to WEATHER_STATION, "estación meteorológica" to WEATHER_STATION, "weather station" to WEATHER_STATION,
        "sensor ambiental" to ENVIRONMENTAL_SENSOR, "environmental sensor" to ENVIRONMENTAL_SENSOR,
    )

    fun normalize(value: String): String =
        all.firstOrNull { it.equals(value, ignoreCase = true) } ?: aliases[value.trim().lowercase()] ?: value
}

object DeviceStatuses {
    const val ONLINE = "Online"
    const val MAINTENANCE = "Maintenance"
    const val OFFLINE = "Offline"
    val all = listOf(ONLINE, MAINTENANCE, OFFLINE)

    private val aliases = mapOf(
        "activo" to ONLINE, "active" to ONLINE, "mantenimiento" to MAINTENANCE, "inactivo" to OFFLINE, "inactive" to OFFLINE,
    )

    fun normalize(value: String): String =
        all.firstOrNull { it.equals(value, ignoreCase = true) } ?: aliases[value.trim().lowercase()] ?: value
}

data class DeviceReadingRecord(val deviceId: Int, val reading: DeviceReading)

object DeviceScope {
    /** The API returns every device, so keep those attached to a herd or an animal the user can see. */
    fun visible(devices: List<Device>, herdIds: Set<Int>, animalIds: Set<Int>): List<Device> =
        devices.filter { (it.herdId != null && it.herdId in herdIds) || (it.animalId != null && it.animalId in animalIds) }

    /** Latest reading and count per device id, from every metric the API returned. */
    fun summarize(metrics: List<DeviceReadingRecord>): Map<Int, Pair<DeviceReading, Int>> =
        metrics.groupBy { it.deviceId }.mapValues { (_, list) ->
            list.maxBy { it.reading.recordedAt }.reading to list.size
        }
}

interface DevicesRepository {
    fun observeDevices(): Flow<List<Device>>
    suspend fun refresh(): AppResult<Unit>
    suspend fun create(draft: DeviceDraft): AppResult<Device>
    suspend fun update(id: Int, draft: DeviceDraft): AppResult<Device>
    suspend fun delete(id: Int): AppResult<Unit>
}
