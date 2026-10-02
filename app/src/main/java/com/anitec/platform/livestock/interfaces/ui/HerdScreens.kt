package com.anitec.platform.livestock.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.Alignment
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
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.RecordCard
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.livestock.interfaces.viewmodel.HerdFormViewModel
import com.anitec.platform.livestock.interfaces.viewmodel.HerdListViewModel

/** Farms of the signed-in rancher, reached from "More". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerdListScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HerdListViewModel = hiltViewModel(),
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
                title = { Text(stringResource(R.string.nav_herds)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.herds_new)) },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.items.isEmpty()) {
                EmptyState(stringResource(R.string.herds_empty))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        PanelHeader(
                            title = stringResource(R.string.nav_herds),
                            subtitle = stringResource(R.string.herds_subtitle),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(state.items, key = { it.herd.id }) { item ->
                        RecordCard(
                            title = item.herd.name,
                            kicker = LivestockOptions.herdType.labelFor(item.herd.mainType),
                            details = listOf(
                                stringResource(R.string.herd_location) to item.herd.location,
                                stringResource(R.string.herd_owner) to item.herd.owner,
                                stringResource(R.string.nav_corrals) to stringResource(R.string.herd_corrals_count, item.corralCount),
                                stringResource(R.string.nav_animals) to stringResource(R.string.herd_animals_count, item.animalCount),
                            ),
                            footer = {
                                SecondaryButton(
                                    text = stringResource(R.string.common_edit),
                                    onClick = { onEdit(item.herd.id) },
                                    icon = Icons.Filled.Edit,
                                )
                                DangerTextButton(
                                    text = stringResource(R.string.common_delete),
                                    onClick = { viewModel.requestDelete(item) },
                                    icon = Icons.Filled.Delete,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { herd ->
        ConfirmDialog(
            title = stringResource(R.string.common_confirm_delete_title),
            message = stringResource(R.string.herd_confirm_delete, herd.name),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
fun HerdFormScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HerdFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.done) { if (state.done) onBack() }
    val required = stringResource(R.string.error_field_required)
    val title = stringResource(if (state.isEdit) R.string.herd_form_edit else R.string.herd_form_new)

    FormScreenScaffold(title = title, onBack = onBack, snackbarHostState = snackbarHostState, modifier = modifier) {
        FormHero(
            icon = Icons.Filled.Place,
            title = title,
            chip = stringResource(R.string.nav_herds),
            subtitle = stringResource(R.string.herd_form_subtitle),
        )
        AniTecTextField(
            value = state.name, onValueChange = viewModel::onNameChange, label = stringResource(R.string.herd_name),
            modifier = Modifier.fillMaxWidth(), error = if (state.nameMissing) required else null,
        )
        AniTecTextField(
            value = state.location, onValueChange = viewModel::onLocationChange, label = stringResource(R.string.herd_location),
            modifier = Modifier.fillMaxWidth(), error = if (state.locationMissing) required else null,
        )
        DropdownField(
            label = stringResource(R.string.herd_main_type),
            options = LivestockOptions.herdType.map { it.value },
            selected = state.mainType,
            onSelected = viewModel::onMainTypeChange,
            optionLabel = { LivestockOptions.herdType.labelFor(it) },
        )
        AniTecTextField(
            value = state.owner, onValueChange = viewModel::onOwnerChange, label = stringResource(R.string.herd_owner),
            modifier = Modifier.fillMaxWidth(), error = if (state.ownerMissing) required else null,
        )
        PrimaryButton(text = stringResource(R.string.common_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = state.saving)
        SecondaryButton(text = stringResource(R.string.common_cancel), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
