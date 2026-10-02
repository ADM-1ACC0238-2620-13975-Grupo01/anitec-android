package com.anitec.platform.iam.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.R
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.iam.application.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignUpUiState(
    val fullName: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val role: UserRole = UserRole.Rancher,
    val acceptedTerms: Boolean = false,
    val showFieldErrors: Boolean = false,
    val loading: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val fullNameMissing get() = showFieldErrors && fullName.isBlank()
    val usernameMissing get() = showFieldErrors && username.isBlank()
    val passwordMissing get() = showFieldErrors && password.isBlank()
    val passwordsMismatch get() = showFieldErrors && password != confirmPassword
    val termsMissing get() = showFieldErrors && !acceptedTerms
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    fun onFullNameChange(value: String) = _state.update { it.copy(fullName = value, errorRes = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, errorRes = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, errorRes = null) }
    fun onConfirmPasswordChange(value: String) = _state.update { it.copy(confirmPassword = value, errorRes = null) }
    fun onRoleChange(value: UserRole) = _state.update { it.copy(role = value) }
    fun onTermsChange(value: Boolean) = _state.update { it.copy(acceptedTerms = value) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val invalid = current.fullName.isBlank() || current.username.isBlank() || current.password.isBlank() ||
            current.password != current.confirmPassword || !current.acceptedTerms
        if (invalid) {
            _state.update { it.copy(showFieldErrors = true) }
            return
        }
        _state.update { it.copy(loading = true, errorRes = null) }
        viewModelScope.launch {
            val result = signUp(current.fullName, current.username, current.password, current.role)
            _state.update {
                it.copy(
                    loading = false,
                    errorRes = (result as? AppResult.Failure)?.error?.let(::errorFor),
                )
            }
        }
    }

    @StringRes
    private fun errorFor(error: AppError): Int =
        if (error is AppError.Conflict) R.string.auth_username_taken else error.messageRes()
}
