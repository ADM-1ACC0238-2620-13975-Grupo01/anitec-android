package com.anitec.platform.livestock.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.ConfirmDialog
import com.anitec.platform.core.designsystem.component.DangerTextButton
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.FormHero
import com.anitec.platform.core.designsystem.component.FormScreenScaffold
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.PanelHeader
import com.anitec.platform.core.designsystem.component.pluralCount
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.RecordCard
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.livestock.interfaces.viewmodel.CorralFormViewModel
import com.anitec.platform.livestock.interfaces.viewmodel.CorralListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorralListScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CorralListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_corrals)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state.canEdit) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.corrals_new)) },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.items.isEmpty()) {
                EmptyState(stringResource(R.string.corrals_empty))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        PanelHeader(
                            title = stringResource(R.string.nav_corrals),
                            subtitle = stringResource(R.string.corrals_subtitle),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(state.items, key = { it.corral.id }) { item ->
                        RecordCard(
                            title = item.corral.name,
                            kicker = item.herdName,
                            details = listOf(stringResource(R.string.nav_animals) to pluralCount(R.plurals.count_animals, item.animalCount)),
                            footer = if (state.canEdit) {
                                {
                                    SecondaryButton(
                                        text = stringResource(R.string.common_edit),
                                        onClick = { onEdit(item.corral.id) },
                                        icon = Icons.Filled.Edit,
                                    )
                                    DangerTextButton(
                                        text = stringResource(R.string.common_delete),
                                        onClick = { viewModel.requestDelete(item) },
                                        icon = Icons.Filled.Delete,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.common_confirm_delete_title),
            message = if (item.animalCount > 0) {
                stringResource(R.string.corral_confirm_delete_with_animals, item.corral.name, item.animalCount)
            } else {
                stringResource(R.string.corral_confirm_delete, item.corral.name)
            },
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
fun CorralFormScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CorralFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.done) { if (state.done) onBack() }
    val required = stringResource(R.string.error_field_required)
    val title = stringResource(if (state.isEdit) R.string.corral_form_edit else R.string.corral_form_new)

    FormScreenScaffold(title = title, onBack = onBack, snackbarHostState = snackbarHostState, modifier = modifier) {
        FormHero(
            icon = Icons.Filled.GridView,
            title = title,
            chip = stringResource(R.string.nav_corrals),
            subtitle = stringResource(R.string.corral_form_subtitle),
        )
        if (state.herds.isEmpty()) {
            Text(stringResource(R.string.corral_no_herds), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AniTecTextField(
            value = state.name, onValueChange = viewModel::onNameChange, label = stringResource(R.string.corral_name),
            modifier = Modifier.fillMaxWidth(), error = if (state.nameMissing) required else null,
        )
        DropdownField(
            label = stringResource(R.string.corral_herd),
            options = state.herds.map { it.id },
            selected = state.herdId,
            onSelected = viewModel::onHerdChange,
            optionLabel = { id -> state.herds.firstOrNull { it.id == id }?.name.orEmpty() },
            error = if (state.herdMissing) required else null,
            placeholder = stringResource(R.string.animal_select),
        )
        PrimaryButton(text = stringResource(R.string.common_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = state.saving)
        SecondaryButton(text = stringResource(R.string.common_cancel), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
