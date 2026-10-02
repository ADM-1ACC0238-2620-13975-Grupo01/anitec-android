package com.anitec.platform.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitec.platform.core.outbox.OutboxDao
import com.anitec.platform.core.outbox.OutboxScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncStatus(
    /** Changes still waiting to be sent. */
    val pending: Int = 0,
    /** Changes the server refused; they need the user's decision. */
    val failed: Int = 0,
) {
    val isIdle get() = pending == 0 && failed == 0
    val total get() = pending + failed
}

/** What the shell shows about the offline queue, and the two things the user can do about it. */
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    outboxDao: OutboxDao,
    private val processor: OutboxProcessor,
    private val scheduler: OutboxScheduler,
) : ViewModel() {

    val status: StateFlow<SyncStatus> = outboxDao.observeAll()
        .map { operations -> SyncStatus(pending = operations.count { !it.failed }, failed = operations.count { it.failed }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    fun retry() {
        viewModelScope.launch {
            processor.retryFailed()
            scheduler.schedule()
        }
    }

    fun discardFailed() {
        viewModelScope.launch { processor.discardFailed() }
    }
}
