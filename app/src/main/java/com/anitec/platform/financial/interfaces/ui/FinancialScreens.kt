package com.anitec.platform.financial.interfaces.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.ConfirmDialog
import com.anitec.platform.core.designsystem.component.DangerTextButton
import com.anitec.platform.core.designsystem.component.DateField
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.FormHero
import com.anitec.platform.core.designsystem.component.FormScreenScaffold
import com.anitec.platform.core.designsystem.component.LoadingState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.MetricCard
import com.anitec.platform.core.designsystem.component.PanelHeader
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.RecordCard
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.financial.domain.FinancialRecord
import com.anitec.platform.financial.domain.RecordCategories
import com.anitec.platform.financial.domain.RecordTypes
import com.anitec.platform.financial.interfaces.viewmodel.FinancialFormViewModel
import com.anitec.platform.financial.interfaces.viewmodel.FinancialListViewModel
import java.util.Locale

/** Peruvian soles as the web shows them, e.g. `S/ 1,250.50`. */
fun formatSoles(amount: Double, locale: Locale = Locale.getDefault()): String = "S/ " + String.format(locale, "%,.2f", amount)

@StringRes
fun recordTypeRes(type: String): Int? = when (type) {
    RecordTypes.INCOME -> R.string.finance_type_income
    RecordTypes.EXPENSE -> R.string.finance_type_expense
    else -> null
}

@StringRes
fun recordCategoryRes(category: String): Int? = when (category) {
    RecordCategories.MILK_SALES -> R.string.finance_category_milk
    RecordCategories.LIVESTOCK_SALES -> R.string.finance_category_livestock
    RecordCategories.FEED -> R.string.finance_category_feed
    RecordCategories.VETERINARY -> R.string.finance_category_veterinary
    RecordCategories.TRANSPORT -> R.string.finance_category_transport
    RecordCategories.OTHER -> R.string.finance_category_other
    else -> null
}

@Composable
fun recordTypeLabel(type: String): String = recordTypeRes(type)?.let { stringResource(it) } ?: type

@Composable
fun recordCategoryLabel(category: String): String = recordCategoryRes(category)?.let { stringResource(it) } ?: category

private fun typeSeverity(record: FinancialRecord) = if (record.isIncome) Severity.Success else Severity.Danger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialListScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FinancialListViewModel = hiltViewModel(),
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
                title = { Text(stringResource(R.string.nav_finance)) },
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
                text = { Text(stringResource(R.string.finance_new)) },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) {
                LoadingState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        PanelHeader(
                            title = stringResource(R.string.finance_title),
                            subtitle = stringResource(R.string.finance_subtitle),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            MetricCard(
                                label = stringResource(R.string.finance_income),
                                value = formatSoles(state.summary.income),
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                modifier = Modifier.weight(1f),
                            )
                            MetricCard(
                                label = stringResource(R.string.finance_expenses),
                                value = formatSoles(state.summary.expenses),
                                icon = Icons.AutoMirrored.Filled.TrendingDown,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item {
                        MetricCard(
                            label = stringResource(R.string.finance_balance),
                            value = formatSoles(state.summary.balance),
                            icon = Icons.Filled.AccountBalance,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.items.isEmpty()) {
                        item { EmptyState(stringResource(R.string.finance_empty)) }
                    }
                    items(state.items, key = { it.id }) { record ->
                        RecordCard(
                            title = recordCategoryLabel(record.category),
                            kicker = record.date,
                            trailing = { StatusTag(recordTypeLabel(record.type), typeSeverity(record)) },
                            details = buildList {
                                add(stringResource(R.string.finance_amount) to formatSoles(record.amount))
                                if (record.description.isNotBlank()) add(stringResource(R.string.finance_description) to record.description)
                            },
                            footer = {
                                SecondaryButton(
                                    text = stringResource(R.string.finance_detail),
                                    onClick = { viewModel.showDetail(record) },
                                    icon = Icons.Filled.Info,
                                )
                                SecondaryButton(
                                    text = stringResource(R.string.common_edit),
                                    onClick = { onEdit(record.id) },
                                    icon = Icons.Filled.Edit,
                                )
                                DangerTextButton(
                                    text = stringResource(R.string.common_delete),
                                    onClick = { viewModel.requestDelete(record) },
                                    icon = Icons.Filled.Delete,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    state.detail?.let { record ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDetail,
            title = { Text(stringResource(R.string.finance_detail_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(stringResource(R.string.finance_type), recordTypeLabel(record.type))
                    DetailRow(stringResource(R.string.finance_category), recordCategoryLabel(record.category))
                    DetailRow(stringResource(R.string.finance_amount), formatSoles(record.amount))
                    DetailRow(stringResource(R.string.finance_date), record.date.ifBlank { "-" })
                    DetailRow(stringResource(R.string.finance_description), record.description.ifBlank { "-" })
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDetail) { Text(stringResource(R.string.common_close)) } },
        )
    }

    state.pendingDelete?.let {
        ConfirmDialog(
            title = stringResource(R.string.common_confirm_delete_title),
            message = stringResource(R.string.finance_confirm_delete),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun FinancialFormScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FinancialFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.done) { if (state.done) onBack() }
    val required = stringResource(R.string.error_field_required)
    val title = stringResource(if (state.isEdit) R.string.finance_form_edit else R.string.finance_form_new)

    FormScreenScaffold(title = title, onBack = onBack, snackbarHostState = snackbarHostState, modifier = modifier) {
        FormHero(
            icon = Icons.Filled.AccountBalanceWallet,
            title = title,
            chip = stringResource(R.string.nav_finance),
            subtitle = stringResource(R.string.finance_form_subtitle),
        )
        DropdownField(
            label = stringResource(R.string.finance_type),
            options = RecordTypes.all.let { if (state.type in it) it else it + state.type },
            selected = state.type,
            onSelected = viewModel::onTypeChange,
            optionLabel = { recordTypeLabel(it) },
        )
        DropdownField(
            label = stringResource(R.string.finance_category),
            options = RecordCategories.all.let { if (state.category in it) it else it + state.category },
            selected = state.category,
            onSelected = viewModel::onCategoryChange,
            optionLabel = { recordCategoryLabel(it) },
        )
        AniTecTextField(
            value = state.amount,
            onValueChange = viewModel::onAmountChange,
            label = stringResource(R.string.finance_amount),
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Decimal,
            placeholder = "S/ 0.00",
            error = if (state.amountInvalid) stringResource(R.string.finance_amount_invalid) else null,
        )
        DateField(
            value = state.date, onChange = viewModel::onDateChange, label = stringResource(R.string.finance_date),
            error = if (state.dateMissing) required else null,
        )
        AniTecTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = stringResource(R.string.finance_description),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 3,
            imeAction = ImeAction.Default,
            placeholder = stringResource(R.string.finance_description_placeholder),
        )
        PrimaryButton(text = stringResource(R.string.common_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = state.saving)
        SecondaryButton(text = stringResource(R.string.common_cancel), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
