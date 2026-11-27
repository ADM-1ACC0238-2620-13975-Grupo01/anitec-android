package com.anitec.platform.consent.interfaces.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.R
import com.anitec.platform.consent.application.AcceptVeterinarianRequestUseCase
import com.anitec.platform.consent.application.ListVeterinarianLinksUseCase
import com.anitec.platform.consent.application.RejectVeterinarianRequestUseCase
import com.anitec.platform.consent.application.RevokeVeterinarianAccessUseCase
import com.anitec.platform.consent.domain.VeterinarianLink
import com.anitec.platform.consent.domain.closed
import com.anitec.platform.consent.domain.pending
import com.anitec.platform.consent.domain.withAccess
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VeterinarianRequestsUiState(
    val links: List<VeterinarianLink> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Link whose request or access is being answered; its buttons wait for the server. */
    val actingId: Int? = null,
    /** Access the rancher is about to revoke, waiting for confirmation. */
    val pendingRevoke: VeterinarianLink? = null,
    @StringRes val messageRes: Int? = null,
) {
    val pending: List<VeterinarianLink> get() = links.pending()
    val withAccess: List<VeterinarianLink> get() = links.withAccess()
    val closed: List<VeterinarianLink> get() = links.closed()
}

/** The rancher's list of veterinarian requests, with accept, reject and revoke (US-020, US-021, US-022). */
@HiltViewModel
class VeterinarianRequestsViewModel @Inject constructor(
    private val listLinks: ListVeterinarianLinksUseCase,
    private val acceptRequest: AcceptVeterinarianRequestUseCase,
    private val rejectRequest: RejectVeterinarianRequestUseCase,
    private val revokeAccess: RevokeVeterinarianAccessUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VeterinarianRequestsUiState())
    val state: StateFlow<VeterinarianRequestsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = listLinks()) {
                is AppResult.Success -> _state.update { it.copy(links = result.value, loading = false, refreshing = false) }
                is AppResult.Failure -> _state.update { it.copy(loading = false, refreshing = false, messageRes = result.error.messageRes()) }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(messageRes = null) }

    fun accept(link: VeterinarianLink) = act(link, R.string.consent_accepted_message) { acceptRequest(link.id) }

    fun reject(link: VeterinarianLink) = act(link, R.string.consent_rejected_message) { rejectRequest(link.id) }

    fun requestRevoke(link: VeterinarianLink) = _state.update { it.copy(pendingRevoke = link) }

    fun dismissRevoke() = _state.update { it.copy(pendingRevoke = null) }

    fun confirmRevoke() {
        val link = _state.value.pendingRevoke ?: return
        _state.update { it.copy(pendingRevoke = null) }
        act(link, R.string.consent_revoked_message) { revokeAccess(link.id) }
    }

    /** Runs one answer; whatever the outcome the list is read again, so it always shows what the server holds. */
    private fun act(link: VeterinarianLink, @StringRes success: Int, call: suspend () -> AppResult<Unit>) {
        if (_state.value.actingId != null) return
        _state.update { it.copy(actingId = link.id) }
        viewModelScope.launch {
            val result = call()
            val message = (result as? AppResult.Failure)?.error?.messageRes() ?: success
            _state.update { it.copy(actingId = null, messageRes = message) }
            if (result is AppResult.Failure) load() else reloadQuietly()
        }
    }

    private suspend fun reloadQuietly() {
        (listLinks() as? AppResult.Success)?.let { result -> _state.update { it.copy(links = result.value) } }
    }
}

/** Number of requests waiting for the rancher, for the badge of the menu. Refreshed each time the menu is shown. */
@HiltViewModel
class PendingRequestsViewModel @Inject constructor(
    private val listLinks: ListVeterinarianLinksUseCase,
) : ViewModel() {

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            // Without a connection the last known number stays; the badge is a hint, not a source of truth.
            (listLinks() as? AppResult.Success)?.let { result -> _count.value = result.value.pending().size }
        }
    }
}
