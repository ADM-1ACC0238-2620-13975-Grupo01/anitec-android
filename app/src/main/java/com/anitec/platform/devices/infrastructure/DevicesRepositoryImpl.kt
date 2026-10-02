package com.anitec.platform.devices.infrastructure

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.onSuccess
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.devices.domain.Device
import com.anitec.platform.devices.domain.DeviceDraft
import com.anitec.platform.devices.domain.DeviceReading
import com.anitec.platform.devices.domain.DeviceReadingRecord
import com.anitec.platform.devices.domain.DeviceScope
import com.anitec.platform.devices.domain.DevicesRepository
import com.anitec.platform.devices.infrastructure.local.DeviceEntity
import com.anitec.platform.devices.infrastructure.local.DevicesDao
import com.anitec.platform.devices.infrastructure.remote.DeviceDto
import com.anitec.platform.devices.infrastructure.remote.DevicesApi
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

fun DeviceDto.toDomain(latest: DeviceReading? = null, count: Int = 0) =
    Device(id, name, type, serialNumber, status, herdId, animalId, latest, count)

fun Device.toEntity() = DeviceEntity(
    id, name, type, serialNumber, status, herdId, animalId,
    latest?.type, latest?.value, latest?.unit, latest?.recordedAt, readingCount,
)

fun DeviceEntity.toDomain() = Device(
    id, name, type, serialNumber, status, herdId, animalId,
    latest = if (readingType != null && readingValue != null) {
        DeviceReading(readingType, readingValue, readingUnit.orEmpty(), readingAt.orEmpty())
    } else {
        null
    },
    readingCount = readingCount,
)

fun DeviceDraft.toDto() = DeviceDto(
    name = name, type = type, serialNumber = serialNumber, status = status, herdId = herdId, animalId = animalId,
)

@Singleton
class DevicesRepositoryImpl @Inject constructor(
    private val api: DevicesApi,
    private val dao: DevicesDao,
    private val livestockDao: LivestockDao,
) : DevicesRepository {

    override fun observeDevices(): Flow<List<Device>> = dao.observeDevices().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> = safeApiCall {
        // Herds and animals are cached first, so they decide which devices are the user's.
        val devices = DeviceScope.visible(
            api.getDevices().map { it.toDomain() }, livestockDao.herdIds().toSet(), livestockDao.animalIds().toSet(),
        )
        val readings = DeviceScope.summarize(
            api.getMetrics().map { DeviceReadingRecord(it.deviceId, DeviceReading(it.type, it.value, it.unit, it.recordedAt)) },
        )
        dao.replaceAll(
            devices.map { device ->
                val summary = readings[device.id]
                device.copy(latest = summary?.first, readingCount = summary?.second ?: 0).toEntity()
            },
        )
    }

    override suspend fun create(draft: DeviceDraft): AppResult<Device> =
        safeApiCall { api.create(draft.toDto()).toDomain() }.onSuccess { dao.upsert(it.toEntity()) }

    override suspend fun update(id: Int, draft: DeviceDraft): AppResult<Device> =
        safeApiCall {
            val kept = dao.find(id)?.toDomain()
            api.update(id, draft.toDto()).toDomain(kept?.latest, kept?.readingCount ?: 0)
        }.onSuccess { dao.upsert(it.toEntity()) }

    override suspend fun delete(id: Int): AppResult<Unit> =
        safeApiCall { api.delete(id) }.onSuccess { dao.delete(id) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DevicesBindingsModule {
    @Binds
    abstract fun bindDevicesRepository(impl: DevicesRepositoryImpl): DevicesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DevicesApiModule {
    @Provides
    @Singleton
    fun provideDevicesApi(retrofit: Retrofit): DevicesApi = retrofit.create(DevicesApi::class.java)
}
