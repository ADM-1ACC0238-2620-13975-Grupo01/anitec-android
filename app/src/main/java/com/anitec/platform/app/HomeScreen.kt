package com.anitec.platform.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.MetricCard
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.SectionChip
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.interfaces.ui.labelRes
import com.anitec.platform.sanitary.interfaces.ui.HealthRecordCard

@Composable
fun HomeScreen(
    session: UserSession,
    onRegisterAnimal: () -> Unit,
    onRecordHealth: () -> Unit,
    onViewAllHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (session.role) {
        UserRole.Rancher -> RancherHome(session, onRegisterAnimal, onRecordHealth, onViewAllHealth, modifier)
        // The veterinarian dashboard is built together with the veterinary module.
        UserRole.Veterinarian -> Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GreetingPanel(session)
            EmptyState(stringResource(R.string.common_coming_soon))
        }
    }
}

@Composable
private fun GreetingPanel(session: UserSession, subtitle: String? = null) {
    AniTecPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionChip(stringResource(R.string.app_name))
            Text(stringResource(R.string.home_greeting, session.fullName), style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusTag(stringResource(session.role.labelRes()), Severity.Success)
        }
    }
}

@Composable
private fun RancherHome(
    session: UserSession,
    onRegisterAnimal: () -> Unit,
    onRecordHealth: () -> Unit,
    onViewAllHealth: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GreetingPanel(session, stringResource(R.string.home_subtitle))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        text = stringResource(R.string.home_action_register_animal),
                        onClick = onRegisterAnimal,
                        icon = Icons.Filled.Pets,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.home_action_record_health),
                        onClick = onRecordHealth,
                        icon = Icons.Filled.Favorite,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (state.herds.isNotEmpty()) {
                    val options: List<Int?> = listOf<Int?>(null) + state.herds.map { it.id }
                    DropdownField(
                        label = stringResource(R.string.home_farm_filter),
                        options = options,
                        selected = state.selectedHerdId,
                        onSelected = viewModel::onHerdSelected,
                        optionLabel = { id -> if (id == null) stringResource(R.string.home_all_farms) else state.herds.first { it.id == id }.name },
                        emptyLabel = stringResource(R.string.home_all_farms),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = stringResource(R.string.home_metric_animals),
                        value = state.animalCount.toString(),
                        icon = Icons.Filled.Pets,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.home_metric_attention),
                        value = state.attentionCount.toString(),
                        icon = Icons.Filled.Warning,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard(
                        label = stringResource(R.string.home_metric_records),
                        value = state.recordCount.toString(),
                        icon = Icons.Filled.Favorite,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.home_metric_followups),
                        value = state.followUpCount.toString(),
                        icon = Icons.Filled.EventRepeat,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onViewAllHealth) { Text(stringResource(R.string.home_view_all), color = MaterialTheme.colorScheme.primary) }
                }
                if (state.recent.isEmpty()) {
                    EmptyState(stringResource(R.string.home_recent_empty))
                } else {
                    state.recent.forEach { HealthRecordCard(it) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun ComingSoonScreen(modifier: Modifier = Modifier) {
    EmptyState(stringResource(R.string.common_coming_soon), modifier = modifier.fillMaxSize().padding(top = 48.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(@StringRes titleRes: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Spacer(Modifier.height(32.dp))
            EmptyState(stringResource(R.string.common_coming_soon))
        }
    }
}
