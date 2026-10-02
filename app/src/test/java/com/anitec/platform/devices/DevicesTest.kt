package com.anitec.platform.devices

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.devices.domain.Device
import com.anitec.platform.devices.domain.DeviceDraft
import com.anitec.platform.devices.domain.DeviceReading
import com.anitec.platform.devices.domain.DeviceReadingRecord
import com.anitec.platform.devices.domain.DeviceScope
import com.anitec.platform.devices.domain.DeviceStatuses
import com.anitec.platform.devices.domain.DeviceTypes
import com.anitec.platform.devices.infrastructure.DevicesRepositoryImpl
import com.anitec.platform.devices.infrastructure.local.DeviceEntity
import com.anitec.platform.devices.infrastructure.local.DevicesDao
import com.anitec.platform.devices.infrastructure.remote.DeviceDto
import com.anitec.platform.devices.infrastructure.remote.DeviceMetricDto
import com.anitec.platform.devices.infrastructure.remote.DevicesApi
import com.anitec.platform.devices.interfaces.ui.formatReadingValue
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private fun device(id: Int, herdId: Int? = null, animalId: Int? = null) =
    Device(id, "D$id", "Scale", "SN$id", "Online", herdId, animalId)

class DeviceRulesTest {

    @Test
    fun `only devices attached to a visible herd or animal are kept`() {
        val all = listOf(
            device(1, herdId = 10), device(2, animalId = 20), device(3, herdId = 99), device(4, animalId = 98), device(5),
        )
        assertEquals(listOf(1, 2), DeviceScope.visible(all, setOf(10), setOf(20)).map { it.id })
    }

    @Test
    fun `the newest reading and the count are summarized per device`() {
        fun record(device: Int, value: Double, at: String) = DeviceReadingRecord(device, DeviceReading("Weight", value, "kg", at))
        val summary = DeviceScope.summarize(
            listOf(
                record(1, 450.0, "2026-06-10T08:00:00"), record(1, 455.0, "2026-06-11T10:34:52.518313"),
                record(1, 440.0, "2026-06-01T08:00:00"), record(2, 38.5, "2026-06-11T00:00:00"),
            ),
        )
        assertEquals(455.0, summary.getValue(1).first.value, 0.0)
        assertEquals(3, summary.getValue(1).second)
        assertEquals(1, summary.getValue(2).second)
        assertNull(summary[3])
    }

    @Test
    fun `labels written by the web map back to the canonical values`() {
        assertEquals(DeviceTypes.SMART_COLLAR, DeviceTypes.normalize("Collar inteligente"))
        assertEquals(DeviceTypes.THERMAL_CAMERA, DeviceTypes.normalize("Thermal camera"))
        assertEquals(DeviceTypes.SCALE, DeviceTypes.normalize("scale"))
        assertEquals("Something new", DeviceTypes.normalize("Something new"))
        assertEquals(DeviceStatuses.ONLINE, DeviceStatuses.normalize("Activo"))
        assertEquals(DeviceStatuses.OFFLINE, DeviceStatuses.normalize("Inactive"))
        assertEquals(DeviceStatuses.MAINTENANCE, DeviceStatuses.normalize("Maintenance"))
    }

    @Test
    fun `reading values drop needless zeros`() {
        assertEquals("455 kg", formatReadingValue(DeviceReading("Weight", 455.0, "kg", "")))
        assertEquals("38.5 °C", formatReadingValue(DeviceReading("Temperature", 38.5, "°C", "")))
        assertEquals("12", formatReadingValue(DeviceReading("Count", 12.0, "", "")))
    }
}

class DevicesRepositoryTest {
    private val api = mockk<DevicesApi>()
    private val dao = mockk<DevicesDao>(relaxed = true)
    private val livestockDao = mockk<LivestockDao>()
    private val repository = DevicesRepositoryImpl(api, dao, livestockDao)

    private fun dto(id: Int, herdId: Int? = null, animalId: Int? = null) = DeviceDto(id, "D$id", "Scale", "SN$id", "Online", herdId, animalId)

    @Test
    fun `refresh caches the visible devices with their latest reading`() = runTest {
        coEvery { api.getDevices() } returns listOf(dto(1, herdId = 10), dto(2, herdId = 99))
        coEvery { api.getMetrics() } returns listOf(
            DeviceMetricDto(1, 1, "Weight", 450.0, "kg", "2026-06-10T08:00:00"),
            DeviceMetricDto(2, 1, "Weight", 455.0, "kg", "2026-06-11T08:00:00"),
        )
        coEvery { livestockDao.herdIds() } returns listOf(10)
        coEvery { livestockDao.animalIds() } returns emptyList()
        val cached = slot<List<DeviceEntity>>()
        coEvery { dao.replaceAll(capture(cached)) } returns Unit

        assertEquals(AppResult.Success(Unit), repository.refresh())

        assertEquals(listOf(1), cached.captured.map { it.id })
        assertEquals(455.0, cached.captured.single().readingValue!!, 0.0)
        assertEquals(2, cached.captured.single().readingCount)
    }

    @Test
    fun `a failed refresh keeps the cache`() = runTest {
        coEvery { api.getDevices() } throws IOException("offline")
        coEvery { livestockDao.herdIds() } returns emptyList()
        coEvery { livestockDao.animalIds() } returns emptyList()

        assertEquals(AppResult.Failure(AppError.Network), repository.refresh())
        coVerify(exactly = 0) { dao.replaceAll(any()) }
    }

    @Test
    fun `updating keeps the cached reading of the device`() = runTest {
        coEvery { dao.find(1) } returns DeviceEntity(1, "D1", "Scale", "SN1", "Online", 10, null, "Weight", 455.0, "kg", "2026-06-11T08:00:00", 4)
        coEvery { api.update(1, any()) } returns dto(1, herdId = 10)

        val result = repository.update(1, DeviceDraft("Renamed", "Scale", "SN1", "Online", 10, null))

        assertTrue(result is AppResult.Success)
        coVerify { dao.upsert(match { it.id == 1 && it.readingValue == 455.0 && it.readingCount == 4 }) }
    }
}
