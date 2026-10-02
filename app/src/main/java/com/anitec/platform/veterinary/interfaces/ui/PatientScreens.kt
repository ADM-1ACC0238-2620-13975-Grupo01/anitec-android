package com.anitec.platform.veterinary.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.pluralCount
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.livestock.domain.AnimalView
import com.anitec.platform.livestock.interfaces.ui.AnimalThumbnail
import com.anitec.platform.livestock.interfaces.ui.LivestockOptions
import com.anitec.platform.livestock.interfaces.ui.labelFor
import com.anitec.platform.livestock.interfaces.ui.severity
import com.anitec.platform.livestock.interfaces.ui.statusLabel
import com.anitec.platform.sanitary.interfaces.ui.HealthRecordCard
import com.anitec.platform.veterinary.interfaces.viewmodel.ClinicalHistoryViewModel
import com.anitec.platform.veterinary.interfaces.viewmodel.PatientsViewModel

/** Patients tab of the veterinarian: pick a client (and optionally one of their farms) to see their animals. */
@Composable
fun PatientsScreen(
    onOpenHistory: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Column(modifier = modifier.fillMaxSize()) {
        if (state.clients.isEmpty()) {
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                EmptyState(stringResource(R.string.vet_pick_client))
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = stringResource(R.string.vet_select_client),
                    options = state.clients.map { it.rancherId },
                    selected = state.selectedClientId,
                    onSelected = viewModel::onClientSelected,
                    optionLabel = { id -> state.clients.firstOrNull { it.rancherId == id }?.rancherName.orEmpty() },
                )
                val herdOptions: List<Int?> = listOf<Int?>(null) + state.herds.map { it.id }
                DropdownField(
                    label = stringResource(R.string.animal_herd),
                    options = herdOptions,
                    selected = state.selectedHerdId,
                    onSelected = viewModel::onHerdSelected,
                    optionLabel = { id -> if (id == null) stringResource(R.string.home_all_farms) else state.herds.first { it.id == id }.name },
                    emptyLabel = stringResource(R.string.home_all_farms),
                )
                Text(
                    stringResource(
                        R.string.vet_patients_context,
                        pluralCount(R.plurals.count_farms, if (state.selectedHerdId == null) state.herds.size else 1),
                        pluralCount(R.plurals.count_animals, state.patients.size),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                if (state.patients.isEmpty()) {
                    EmptyState(stringResource(R.string.vet_patients_empty))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.patients, key = { it.animal.id }) { view ->
                            PatientCard(view, onOpenHistory = { onOpenHistory(view.animal.id) })
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun PatientCard(view: AnimalView, onOpenHistory: () -> Unit) {
    val animal = view.animal
    AniTecPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimalThumbnail(animal.imageUrl, animal.name)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(animal.name, style = MaterialTheme.typography.titleSmall)
                    Text(animal.tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOf(LivestockOptions.species.labelFor(animal.species), animal.breed, view.herdName).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusTag(statusLabel(animal.status), animal.healthStatus.severity())
            }
            SecondaryButton(text = stringResource(R.string.vet_view_history), onClick = onOpenHistory, icon = Icons.Filled.History)
        }
    }
}

/** Everything recorded about one animal, newest first, with a shortcut to add a record for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalHistoryScreen(
    onBack: () -> Unit,
    onNewRecord: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClinicalHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vet_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state.animal != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNewRecord(viewModel.animalId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.vet_history_new)) },
                )
            }
        },
    ) { padding ->
        val animalView = state.animal
        when {
            animalView == null && state.loaded -> EmptyState(stringResource(R.string.vet_history_not_found), Modifier.padding(padding))
            animalView == null -> Unit
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { PatientSummary(animalView) }
                if (state.events.isEmpty()) {
                    item { EmptyState(stringResource(R.string.vet_history_empty)) }
                } else {
                    items(state.events, key = { it.event.id }) { HealthRecordCard(it) }
                }
            }
        }
    }
}

@Composable
private fun PatientSummary(view: AnimalView) {
    val animal = view.animal
    AniTecPanel(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimalThumbnail(animal.imageUrl, animal.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(animal.name, style = MaterialTheme.typography.titleMedium)
                Text(animal.tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    listOf(LivestockOptions.species.labelFor(animal.species), animal.breed, view.herdName, "${animal.weight} kg")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusTag(statusLabel(animal.status), animal.healthStatus.severity())
        }
    }
}
