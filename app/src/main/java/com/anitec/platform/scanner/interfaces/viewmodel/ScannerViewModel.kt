package com.anitec.platform.scanner.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.livestock.application.ObserveAllAnimalsUseCase
import com.anitec.platform.livestock.application.RefreshLivestockUseCase
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.scanner.domain.TagResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Outcome of looking up a code: the matching animal, or the code that matched nothing. */
sealed interface ScanResult {
    data class Found(val animal: Animal) : ScanResult
    data class NotFound(val code: String) : ScanResult
}

/** Text typed in the manual code field and the result on screen, if any. */
data class ScannerUiState(
    val manualCode: String = "",
    val result: ScanResult? = null,
)

/**
 * Looks up scanned or typed codes among the user's own cached animals, so it also works without a connection.
 * While a result is on screen, further detections are ignored: the camera sees the same code many times a second.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    observeAnimals: ObserveAllAnimalsUseCase,
    refreshLivestock: RefreshLivestockUseCase,
) : ViewModel() {

    private var animals: List<Animal> = emptyList()
    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { observeAnimals().collect { animals = it } }
        // Best effort: with no connection the cached animals are used.
        viewModelScope.launch { refreshLivestock() }
    }

    /** Called by the camera for every code it reads; ignored while a result is shown. */
    fun onCodeDetected(raw: String) {
        if (_state.value.result != null || raw.isBlank()) return
        val animal = TagResolver.resolve(raw, animals)
        _state.update { it.copy(result = if (animal != null) ScanResult.Found(animal) else ScanResult.NotFound(raw.trim())) }
    }

    fun onManualCodeChange(value: String) = _state.update { it.copy(manualCode = value) }

    /** Looks up the typed code, replacing any result on screen. */
    fun lookUpManualCode() {
        val code = _state.value.manualCode
        if (code.isBlank()) return
        _state.update { it.copy(result = null) }
        onCodeDetected(code)
    }

    /** Clears the result and the typed code so the camera can scan again. */
    fun scanAgain() = _state.update { it.copy(result = null, manualCode = "") }
}
