package com.anitec.platform.livestock.interfaces.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.PendingSyncTag
import com.anitec.platform.core.outbox.isPendingSync
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.ConfirmDialog
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.LoadingState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.core.network.resolveMediaUrl
import com.anitec.platform.livestock.domain.AnimalStatus
import com.anitec.platform.livestock.domain.AnimalView
import com.anitec.platform.livestock.domain.Corral
import com.anitec.platform.livestock.interfaces.viewmodel.AnimalListUiState
import com.anitec.platform.livestock.interfaces.viewmodel.AnimalListViewModel
import com.anitec.platform.livestock.interfaces.viewmodel.PendingAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalListScreen(
    onNewAnimal: () -> Unit,
    onEditAnimal: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimalListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.selectionMode) {
                SelectionBar(
                    state = state,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAllVisible,
                    onChangeStatus = viewModel::requestStatusChange,
                    onDelete = viewModel::requestDeleteSelected,
                )
            } else {
                SearchBar(state, viewModel::onQueryChange, viewModel::onCorralFilterChange)
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.loading -> LoadingState()
                    state.items.isEmpty() -> EmptyState(
                        stringResource(if (state.totalCount == 0) R.string.animals_empty else R.string.animals_empty_search),
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.items, key = { it.animal.id }) { view ->
                            AnimalRow(
                                view = view,
                                selected = view.animal.id in state.selectedIds,
                                selectionMode = state.selectionMode,
                                canSelect = state.canEdit && !view.animal.id.isPendingSync,
                                onClick = {
                                    if (state.selectionMode) viewModel.toggleSelection(view.animal.id) else viewModel.openDetail(view.animal.id)
                                },
                                onLongClick = { if (state.canEdit && !view.animal.id.isPendingSync) viewModel.toggleSelection(view.animal.id) },
                            )
                        }
                    }
                }
            }
        }

        if (state.canEdit && !state.selectionMode) {
            ExtendedFloatingActionButton(
                onClick = onNewAnimal,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.animals_new)) },
            )
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.detail?.let { detail ->
        AnimalDetailSheet(
            view = detail,
            canEdit = state.canEdit && !detail.animal.id.isPendingSync,
            onDismiss = viewModel::closeDetail,
            onEdit = {
                viewModel.closeDetail()
                onEditAnimal(detail.animal.id)
            },
            onDelete = { viewModel.requestDelete(detail) },
        )
    }

    PendingActionDialogs(state, viewModel)
}

@Composable
private fun SearchBar(
    state: AnimalListUiState,
    onQueryChange: (String) -> Unit,
    onCorralFilterChange: (Int?) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AniTecTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.common_search),
            placeholder = stringResource(R.string.animals_search_hint),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
        val options: List<Corral?> = listOf(null) + state.corrals
        DropdownField(
            label = stringResource(R.string.animals_filter_corral),
            options = options,
            selected = state.corrals.firstOrNull { it.id == state.corralFilter },
            onSelected = { onCorralFilterChange(it?.id) },
            optionLabel = { it?.name ?: stringResource(R.string.animals_all_corrals) },
            emptyLabel = stringResource(R.string.animals_all_corrals),
        )
        Text(
            stringResource(R.string.animals_count, state.items.size, state.totalCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Replaces the search area while animals are selected (long-press to start, tap to toggle). */
@Composable
private fun SelectionBar(
    state: AnimalListUiState,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onChangeStatus: () -> Unit,
    onDelete: () -> Unit,
) {
    AniTecPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.animals_clear_selection))
            }
            Text(
                stringResource(R.string.animals_selected_count, state.selectedIds.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.DoneAll, contentDescription = stringResource(R.string.animals_select_all))
            }
            IconButton(onClick = onChangeStatus) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.animals_change_status))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.animals_delete_selected),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimalRow(
    view: AnimalView,
    selected: Boolean,
    selectionMode: Boolean,
    canSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val animal = view.animal
    AniTecPanel(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.animals_selected_count, 1)),
    ) {
        Row(
            modifier = Modifier
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode && canSelect) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
            }
            AnimalThumbnail(animal.imageUrl, animal.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (animal.id.isPendingSync) PendingSyncTag(modifier = Modifier.padding(bottom = 2.dp))
                Text(animal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(animal.tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    listOf(LivestockOptions.species.labelFor(animal.species), view.herdName, view.corralName ?: stringResource(R.string.animal_no_corral))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusTag(statusLabel(animal.status), animal.healthStatus.severity())
        }
    }
}

@Composable
fun AnimalThumbnail(imageUrl: String?, name: String, modifier: Modifier = Modifier) {
    val url = resolveMediaUrl(imageUrl)
    Box(
        modifier = modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder: @Composable () -> Unit = {
            Icon(Icons.Filled.Pets, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        }
        if (url != null) {
            // Shows the paw icon while loading and when the image is missing or broken on the server.
            SubcomposeAsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { placeholder() } },
                error = { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { placeholder() } },
            )
        } else {
            placeholder()
        }
    }
}

@Composable
private fun PendingActionDialogs(state: AnimalListUiState, viewModel: AnimalListViewModel) {
    when (val pending = state.pending) {
        null -> Unit
        is PendingAction.DeleteOne -> ConfirmDialog(
            title = stringResource(R.string.common_confirm_delete_title),
            message = stringResource(R.string.animals_confirm_delete_one, pending.name),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending,
        )
        PendingAction.DeleteSelected -> ConfirmDialog(
            title = stringResource(R.string.common_confirm_delete_title),
            message = stringResource(R.string.animals_confirm_delete_many, state.selectedIds.size),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending,
        )
        PendingAction.PickStatus -> StatusPickerDialog(onPick = viewModel::onStatusPicked, onDismiss = viewModel::dismissPending)
        is PendingAction.ChangeStatus -> ConfirmDialog(
            title = stringResource(R.string.animals_change_status),
            message = stringResource(R.string.animals_confirm_status, state.selectedIds.size, statusLabel(pending.status.apiValue)),
            confirmLabel = stringResource(R.string.common_save),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmPending,
            onDismiss = viewModel::dismissPending,
        )
    }
}

@Composable
private fun StatusPickerDialog(onPick: (AnimalStatus) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.animals_status_dialog_title)) },
        text = {
            Column {
                AnimalStatus.selectable.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = false, role = Role.RadioButton, onClick = { onPick(status) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = false, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        StatusTag(statusLabel(status.apiValue), status.severity())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
