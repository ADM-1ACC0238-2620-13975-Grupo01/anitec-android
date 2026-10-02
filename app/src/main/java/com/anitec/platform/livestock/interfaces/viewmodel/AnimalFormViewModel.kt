package com.anitec.platform.livestock.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anitec.platform.R
import com.anitec.platform.app.AnimalFormRoute
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.livestock.application.ObserveAnimalsUseCase
import com.anitec.platform.livestock.application.ObserveCorralsUseCase
import com.anitec.platform.livestock.application.ObserveHerdsUseCase
import com.anitec.platform.livestock.application.RegisterAnimalBatchUseCase
import com.anitec.platform.livestock.application.SaveAnimalUseCase
import com.anitec.platform.livestock.application.UploadAnimalImageUseCase
import com.anitec.platform.livestock.domain.AnimalBatchDraft
import com.anitec.platform.livestock.domain.AnimalDraft
import com.anitec.platform.livestock.domain.AnimalPlacementPolicy
import com.anitec.platform.livestock.domain.AnimalStatus
import com.anitec.platform.livestock.domain.Corral
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.livestock.interfaces.ui.livestockMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RegistrationMode { Individual, Bulk }

data class AnimalFormUiState(
    val isEdit: Boolean = false,
    val mode: RegistrationMode = RegistrationMode.Individual,
    val tag: String = "",
    val name: String = "",
    val species: String = "Bovino",
    val breed: String = "",
    val gender: String = "Hembra",
    val birthDate: String = "",
    val weight: String = "0",
    val status: String = AnimalStatus.Healthy.apiValue,
    val herdId: Int? = null,
    val corralId: Int? = null,
    val source: String? = null,
    val ageRange: String? = null,
    val quantity: String = "1",
    val imageUrl: String? = null,
    val localPhotoUri: String? = null,
    val uploading: Boolean = false,
    val herds: List<Herd> = emptyList(),
    val allCorrals: List<Corral> = emptyList(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    @StringRes val messageRes: Int? = null,
    val serverMessages: List<String> = emptyList(),
) {
    val corralsOfHerd: List<Corral> get() = allCorrals.filter { it.herdId == herdId }
    val bulk: Boolean get() = !isEdit && mode == RegistrationMode.Bulk

    val weightValue: Double? get() = weight.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val quantityValue: Int? get() = quantity.trim().toIntOrNull()

    val tagMissing get() = showErrors && !bulk && tag.isBlank()
    val nameMissing get() = showErrors && !bulk && name.isBlank()
    val breedMissing get() = showErrors && breed.isBlank()
    val herdMissing get() = showErrors && herdId == null
    val corralMissing get() = showErrors && corralId == null
    val weightInvalid get() = showErrors && weightValue == null
    val quantityInvalid get() = showErrors && bulk && quantityValue?.let(AnimalPlacementPolicy::isValidBatchSize) != true

    private val hasErrors: Boolean
        get() = (!bulk && (tag.isBlank() || name.isBlank())) || breed.isBlank() || herdId == null || corralId == null ||
            weightValue == null || (bulk && quantityValue?.let(AnimalPlacementPolicy::isValidBatchSize) != true)

    val isValid: Boolean get() = !hasErrors
}

@HiltViewModel
class AnimalFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeHerds: ObserveHerdsUseCase,
    observeCorrals: ObserveCorralsUseCase,
    private val observeAnimals: ObserveAnimalsUseCase,
    private val saveAnimal: SaveAnimalUseCase,
    private val registerBatch: RegisterAnimalBatchUseCase,
    private val uploadImage: UploadAnimalImageUseCase,
) : ViewModel() {

    private val animalId: Int? = savedStateHandle.toRoute<AnimalFormRoute>().animalId

    private val _state = MutableStateFlow(AnimalFormUiState(isEdit = animalId != null))
    val state: StateFlow<AnimalFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeHerds(), observeCorrals()) { herds, corrals -> herds to corrals }.collect { (herds, corrals) ->
                _state.update { current ->
                    val herdId = current.herdId ?: herds.firstOrNull()?.id
                    val corralId = current.corralId ?: corrals.firstOrNull { it.herdId == herdId }?.id
                    current.copy(herds = herds, allCorrals = corrals, herdId = herdId, corralId = corralId)
                }
            }
        }
        if (animalId != null) loadAnimal(animalId)
    }

    private fun loadAnimal(id: Int) {
        viewModelScope.launch {
            val animal = observeAnimals().first().firstOrNull { it.id == id }
            if (animal == null) {
                _state.update { it.copy(done = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    tag = animal.tag, name = animal.name, species = animal.species, breed = animal.breed,
                    gender = animal.gender, birthDate = animal.birthDate.orEmpty(),
                    weight = animal.weight.toString(), status = animal.status, herdId = animal.herdId,
                    corralId = animal.corralId, source = animal.source, ageRange = animal.ageRange,
                    imageUrl = animal.imageUrl,
                )
            }
        }
    }

    fun onModeChange(mode: RegistrationMode) = _state.update { it.copy(mode = mode) }
    fun onTagChange(value: String) = _state.update { it.copy(tag = value) }
    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onSpeciesChange(value: String) = _state.update { it.copy(species = value) }
    fun onBreedChange(value: String) = _state.update { it.copy(breed = value) }
    fun onGenderChange(value: String) = _state.update { it.copy(gender = value) }
    fun onBirthDateChange(value: String) = _state.update { it.copy(birthDate = value) }
    fun onWeightChange(value: String) = _state.update { it.copy(weight = value) }
    fun onStatusChange(value: String) = _state.update { it.copy(status = value) }
    fun onCorralChange(value: Int?) = _state.update { it.copy(corralId = value) }
    fun onSourceChange(value: String?) = _state.update { it.copy(source = value) }
    fun onAgeRangeChange(value: String?) = _state.update { it.copy(ageRange = value) }
    fun onQuantityChange(value: String) = _state.update { it.copy(quantity = value) }
    fun onMessageShown() = _state.update { it.copy(messageRes = null) }

    /** Changing the farm resets the corral to the first one of that farm, as the web does. */
    fun onHerdChange(herdId: Int) = _state.update { current ->
        current.copy(herdId = herdId, corralId = current.allCorrals.firstOrNull { it.herdId == herdId }?.id)
    }

    fun onPhotoRemoved() = _state.update { it.copy(imageUrl = null, localPhotoUri = null) }

    /** [uri] comes from the camera or the gallery; it is uploaded right away and the server path is kept. */
    fun onPhotoPicked(uri: String) {
        _state.update { it.copy(localPhotoUri = uri, uploading = true) }
        viewModelScope.launch {
            when (val result = uploadImage(uri)) {
                is AppResult.Success -> _state.update { it.copy(uploading = false, imageUrl = result.value) }
                is AppResult.Failure -> _state.update {
                    it.copy(uploading = false, localPhotoUri = null, messageRes = R.string.animal_photo_error)
                }
            }
        }
    }

    fun onCameraDenied() = _state.update { it.copy(messageRes = R.string.animal_photo_camera_denied) }

    fun onCameraUnavailable() = _state.update { it.copy(messageRes = R.string.animal_photo_camera_unavailable) }

    fun save() {
        val current = _state.value
        if (current.saving || current.uploading) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, messageRes = null, serverMessages = emptyList()) }
        viewModelScope.launch {
            val result: AppResult<*> = if (current.bulk) {
                registerBatch(current.toBatchDraft(), current.allCorrals)
            } else {
                saveAnimal(animalId, current.toDraft(), current.allCorrals)
            }
            _state.update {
                when (result) {
                    is AppResult.Success<*> -> it.copy(saving = false, done = true)
                    is AppResult.Failure -> it.copy(
                        saving = false,
                        messageRes = result.error.livestockMessageRes(),
                        serverMessages = (result.error as? AppError.Validation)?.messages
                            ?.filterNot { message -> message.startsWith("livestock.") }.orEmpty(),
                    )
                }
            }
        }
    }

    private fun AnimalFormUiState.toDraft() = AnimalDraft(
        tag = tag.trim(), name = name.trim(), species = species, breed = breed.trim(), gender = gender,
        birthDate = birthDate.ifBlank { null }, weight = weightValue ?: 0.0, status = status,
        herdId = herdId!!, corralId = corralId, source = source, ageRange = ageRange, imageUrl = imageUrl,
    )

    private fun AnimalFormUiState.toBatchDraft() = AnimalBatchDraft(
        species = species, breed = breed.trim(), gender = gender, birthDate = birthDate.ifBlank { null },
        weight = weightValue ?: 0.0, status = status, herdId = herdId!!, corralId = corralId!!,
        quantity = quantityValue ?: 1, source = source, ageRange = ageRange, imageUrl = imageUrl,
    )
}
