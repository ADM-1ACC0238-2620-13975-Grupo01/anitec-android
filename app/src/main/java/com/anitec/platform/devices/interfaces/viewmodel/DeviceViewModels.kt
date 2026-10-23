package com.anitec.platform.devices.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.app.DeviceFormRoute
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.devices.application.DeleteDeviceUseCase
import com.anitec.platform.devices.application.ObserveDevicesUseCase
import com.anitec.platform.devices.application.RefreshDevicesUseCase
import com.anitec.platform.devices.application.SaveDeviceUseCase
import com.anitec.platform.devices.domain.Device
import com.anitec.platform.devices.domain.DeviceDraft
import com.anitec.platform.devices.domain.DeviceStatuses
import com.anitec.platform.devices.domain.DeviceTypes
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.Herd
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a device is attached: a herd or an animal, with the name to show. */
data class DeviceItem(val device: Device, val herdName: String?, val animalName: String?)

data class DeviceListUiState(
    val items: List<DeviceItem> = emptyList(),
    val canManage: Boolean = false,
    val pendingDelete: Device? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    @StringRes val messageRes: Int? = null,
)

private data class DeviceListLocal(
    val pendingDelete: Device? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val messageRes: Int? = null,
)

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    observeDevices: ObserveDevicesUseCase,
    observeHerds: ObserveHerdsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    private val refreshLivestock: RefreshLivestockUseCase,
    private val refreshDevices: RefreshDevicesUseCase,
    private val deleteDevice: DeleteDeviceUseCase,
    sessionStore: SessionStore,
) : ViewModel() {

    // Only ranchers manage devices; a veterinarian reads them.
    private val canManage = (sessionStore.state.value as? SessionState.SignedIn)?.session?.role == UserRole.Rancher
    private val local = MutableStateFlow(DeviceListLocal())

    val state: StateFlow<DeviceListUiState> = combine(
        combine(observeDevices(), observeHerds(), observeAnimals()) { devices, herds, animals -> Triple(devices, herds, animals) },
        local,
    ) { (devices, herds, animals), local ->
        val herdNames = herds.associate { it.id to it.name }
        val animalNames = animals.associate { it.id to it.name }
        DeviceListUiState(
            items = devices.map { DeviceItem(it, it.herdId?.let(herdNames::get), it.animalId?.let(animalNames::get)) },
            canManage = canManage,
            pendingDelete = local.pendingDelete,
            loading = local.loading && devices.isEmpty(),
            refreshing = local.refreshing,
            messageRes = local.messageRes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceListUiState(canManage = canManage))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(refreshing = true) }
            // Devices are scoped by the herds and animals the user can see, so livestock is refreshed first.
            val result = when (val livestock = refreshLivestock()) {
                is AppResult.Failure -> livestock
                is AppResult.Success -> refreshDevices()
            }
            local.update { it.copy(refreshing = false, loading = false, messageRes = (result as? AppResult.Failure)?.error?.messageRes()) }
        }
    }

    fun requestDelete(device: Device) = local.update { it.copy(pendingDelete = device) }
    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }
    fun onMessageShown() = local.update { it.copy(messageRes = null) }

    fun confirmDelete() {
        val device = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = deleteDevice(device.id)
            if (result is AppResult.Failure) local.update { it.copy(messageRes = result.error.messageRes()) }
        }
    }
}

enum class DeviceTarget { Herd, Animal }

data class DeviceFormUiState(
    val isEdit: Boolean = false,
    val name: String = "",
    val type: String = DeviceTypes.SMART_COLLAR,
    val serialNumber: String = "",
    val status: String = DeviceStatuses.ONLINE,
    val target: DeviceTarget = DeviceTarget.Herd,
    val herdId: Int? = null,
    val animalId: Int? = null,
    val herds: List<Herd> = emptyList(),
    val animals: List<Animal> = emptyList(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    @StringRes val messageRes: Int? = null,
) {
    val nameMissing get() = showErrors && name.isBlank()
    val serialMissing get() = showErrors && serialNumber.isBlank()
    private val targetChosen get() = if (target == DeviceTarget.Herd) herdId != null else animalId != null
    val targetMissing get() = showErrors && !targetChosen
    val isValid get() = name.isNotBlank() && serialNumber.isNotBlank() && targetChosen
}

@HiltViewModel
class DeviceFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDevices: ObserveDevicesUseCase,
    observeHerds: ObserveHerdsUseCase,
    observeAnimals: ObserveAnimalsUseCase,
    private val saveDevice: SaveDeviceUseCase,
) : ViewModel() {

    private val deviceId: Int? = savedStateHandle.toRoute<DeviceFormRoute>().deviceId
    private val devices = observeDevices()

    private val _state = MutableStateFlow(DeviceFormUiState(isEdit = deviceId != null))
    val state: StateFlow<DeviceFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeHerds(), observeAnimals()) { herds, animals -> herds to animals }.collect { (herds, animals) ->
                // A new device starts on the first herd; an edited one keeps what it had.
                val defaultHerd = if (deviceId == null) herds.firstOrNull()?.id else null
                _state.update { it.copy(herds = herds, animals = animals, herdId = it.herdId ?: defaultHerd) }
            }
        }
        if (deviceId != null) {
            viewModelScope.launch {
                val device = devices.first().firstOrNull { it.id == deviceId }
                if (device == null) {
                    _state.update { it.copy(done = true) }
                } else {
                    _state.update {
                        it.copy(
                            name = device.name,
                            type = DeviceTypes.normalize(device.type),
                            serialNumber = device.serialNumber,
                            status = DeviceStatuses.normalize(device.status),
                            target = if (device.animalId != null) DeviceTarget.Animal else DeviceTarget.Herd,
                            herdId = device.herdId,
                            animalId = device.animalId,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onTypeChange(value: String) = _state.update { it.copy(type = value) }
    fun onSerialChange(value: String) = _state.update { it.copy(serialNumber = value) }
    fun onStatusChange(value: String) = _state.update { it.copy(status = value) }
    fun onTargetChange(value: DeviceTarget) = _state.update { it.copy(target = value) }
    fun onHerdChange(value: Int) = _state.update { it.copy(herdId = value) }
    fun onAnimalChange(value: Int) = _state.update { it.copy(animalId = value) }
    fun onMessageShown() = _state.update { it.copy(messageRes = null) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, messageRes = null) }
        viewModelScope.launch {
            // A device belongs to a herd or to an animal, never both.
            val draft = DeviceDraft(
                name = current.name.trim(),
                type = current.type,
                serialNumber = current.serialNumber.trim(),
                status = current.status,
                herdId = current.herdId.takeIf { current.target == DeviceTarget.Herd },
                animalId = current.animalId.takeIf { current.target == DeviceTarget.Animal },
            )
            _state.update {
                when (val result = saveDevice(deviceId, draft)) {
                    is AppResult.Success -> it.copy(saving = false, done = true)
                    is AppResult.Failure -> it.copy(saving = false, messageRes = result.error.messageRes())
                }
            }
        }
    }
}
