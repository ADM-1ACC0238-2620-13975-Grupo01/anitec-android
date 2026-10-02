package com.anitec.platform.iam.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.R
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.iam.application.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val username: String = "",
    val password: String = "",
    val showFieldErrors: Boolean = false,
    val loading: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val usernameMissing get() = showFieldErrors && username.isBlank()
    val passwordMissing get() = showFieldErrors && password.isBlank()
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signIn: SignInUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, errorRes = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, errorRes = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(showFieldErrors = true) }
            return
        }
        _state.update { it.copy(loading = true, errorRes = null) }
        viewModelScope.launch {
            val result = signIn(current.username, current.password)
            // On success the session store flips the app to the signed-in graph; nothing else to do here.
            _state.update {
                it.copy(
                    loading = false,
                    errorRes = (result as? AppResult.Failure)?.error?.let(::errorFor),
                )
            }
        }
    }

    // The API answers wrong credentials with 400 (not 401).
    @StringRes
    private fun errorFor(error: AppError): Int =
        if (error is AppError.Validation) R.string.auth_invalid_credentials else error.messageRes()
}
