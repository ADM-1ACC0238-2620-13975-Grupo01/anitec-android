package com.anitec.platform.devices.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.devices.domain.Device
import com.anitec.platform.devices.domain.DeviceDraft
import com.anitec.platform.devices.domain.DeviceReading
import com.anitec.platform.devices.domain.DevicesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Cached devices the user can see; emits again after every refresh or change. */
class ObserveDevicesUseCase @Inject constructor(private val repository: DevicesRepository) {
    operator fun invoke(): Flow<List<Device>> = repository.observeDevices()
}

/** Downloads devices and their metrics and replaces the cache. */
class RefreshDevicesUseCase @Inject constructor(private val repository: DevicesRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refresh()
}

class SaveDeviceUseCase @Inject constructor(private val repository: DevicesRepository) {
    /** Creates the device when [id] is null, otherwise updates it. */
    suspend operator fun invoke(id: Int?, draft: DeviceDraft): AppResult<Device> =
        if (id == null) repository.create(draft) else repository.update(id, draft)
}

/** Deletes a device on the server and in the cache. */
class DeleteDeviceUseCase @Inject constructor(private val repository: DevicesRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.delete(id)
}

/** Full reading history of one device, read from the server. */
class GetDeviceHistoryUseCase @Inject constructor(private val repository: DevicesRepository) {
    suspend operator fun invoke(deviceId: Int): AppResult<List<DeviceReading>> = repository.history(deviceId)
}

/** Newest reading of one device, or null when it has none. */
class GetLatestDeviceReadingUseCase @Inject constructor(private val repository: DevicesRepository) {
    suspend operator fun invoke(deviceId: Int): AppResult<DeviceReading?> = repository.latestReading(deviceId)
}
