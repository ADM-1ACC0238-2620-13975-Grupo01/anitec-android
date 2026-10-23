package com.anitec.platform.devices.application

import com.anitec.platform.core.common.AppResult
import com.anitec.platform.devices.domain.Device
import com.anitec.platform.devices.domain.DeviceDraft
import com.anitec.platform.devices.domain.DevicesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDevicesUseCase @Inject constructor(private val repository: DevicesRepository) {
    operator fun invoke(): Flow<List<Device>> = repository.observeDevices()
}

class RefreshDevicesUseCase @Inject constructor(private val repository: DevicesRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refresh()
}

class SaveDeviceUseCase @Inject constructor(private val repository: DevicesRepository) {
    /** Creates the device when [id] is null, otherwise updates it. */
    suspend operator fun invoke(id: Int?, draft: DeviceDraft): AppResult<Device> =
        if (id == null) repository.create(draft) else repository.update(id, draft)
}

class DeleteDeviceUseCase @Inject constructor(private val repository: DevicesRepository) {
    suspend operator fun invoke(id: Int): AppResult<Unit> = repository.delete(id)
}
