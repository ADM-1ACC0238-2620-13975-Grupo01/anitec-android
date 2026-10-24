package com.anitec.platform.subscriptions.interfaces.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.EmptyState
import com.anitec.platform.core.designsystem.component.ErrorMessage
import com.anitec.platform.core.designsystem.component.LoadingState
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.core.designsystem.component.mergedSemantics
import com.anitec.platform.core.designsystem.component.sectionHeading
import com.anitec.platform.devices.interfaces.ui.formatRecordedAt
import com.anitec.platform.financial.interfaces.ui.formatSoles
import com.anitec.platform.subscriptions.domain.Payment
import com.anitec.platform.subscriptions.domain.Plan
import com.anitec.platform.subscriptions.domain.Subscription
import com.anitec.platform.subscriptions.interfaces.viewmodel.CheckoutPhase
import com.anitec.platform.subscriptions.interfaces.viewmodel.SubscriptionResultViewModel
import com.anitec.platform.subscriptions.interfaces.viewmodel.SubscriptionsUiState
import com.anitec.platform.subscriptions.interfaces.viewmodel.SubscriptionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)

    // Read again each time the screen comes back, which includes the return from the payment page.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val context = LocalContext.current
    LaunchedEffect(state.checkoutUrl) {
        val url = state.checkoutUrl ?: return@LaunchedEffect
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            viewModel.onCheckoutOpened()
        } catch (_: ActivityNotFoundException) {
            viewModel.onBrowserMissing()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscriptions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (state.loading) {
                LoadingState()
            } else {
                SubscriptionsContent(state, onSubscribe = viewModel::subscribe)
            }
        }
    }
}

@Composable
private fun SubscriptionsContent(state: SubscriptionsUiState, onSubscribe: (Plan) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { CurrentPlanCard(state.active, state.activePlan) }
        item {
            Text(
                stringResource(R.string.subscriptions_plans),
                modifier = Modifier.padding(top = 6.dp).sectionHeading(),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.plans.isEmpty()) {
            item { EmptyState(stringResource(R.string.subscriptions_plans_empty)) }
        } else {
            // Plans and payments share one list, so their keys carry a prefix: a plan and a payment can have the same id.
            items(state.plans, key = { "plan-${it.id}" }) { plan ->
                PlanCard(
                    plan = plan,
                    isCurrent = state.active?.isActive == true && state.active.planId == plan.id,
                    hasActive = state.active?.isActive == true,
                    busy = state.startingPlanId == plan.id,
                    enabled = state.startingPlanId == null,
                    onSubscribe = { onSubscribe(plan) },
                )
            }
            item {
                Text(
                    stringResource(R.string.subscriptions_checkout_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.subscriptions_payments),
                modifier = Modifier.padding(top = 6.dp).sectionHeading(),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.payments.isEmpty()) {
            item { EmptyState(stringResource(R.string.subscriptions_payments_empty)) }
        } else {
            items(state.payments, key = { "payment-${it.id}" }) { payment -> PaymentRow(payment) }
        }
    }
}

@Composable
private fun CurrentPlanCard(subscription: Subscription?, plan: Plan?) {
    AniTecPanel(modifier = Modifier.fillMaxWidth().mergedSemantics()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.subscriptions_current),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (subscription == null || !subscription.isActive) {
                Text(stringResource(R.string.subscriptions_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        plan?.name ?: stringResource(R.string.subscriptions_unknown_plan),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    StatusTag(stringResource(R.string.subscriptions_status_active), Severity.Success)
                }
                plan?.let { Text(planSummary(it), style = MaterialTheme.typography.bodyMedium) }
                Text(
                    stringResource(R.string.subscriptions_since, subscription.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun planSummary(plan: Plan): String =
    stringResource(R.string.subscriptions_price_month, formatSoles(plan.price)) + " · " +
        stringResource(R.string.subscriptions_animals_limit, plan.maxAnimals)

@Composable
private fun PlanCard(
    plan: Plan,
    isCurrent: Boolean,
    hasActive: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onSubscribe: () -> Unit,
) {
    AniTecPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.mergedSemantics(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium)
                Text(planSummary(plan), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isCurrent) {
                StatusTag(stringResource(R.string.subscriptions_current_plan_tag), Severity.Success)
            } else {
                PrimaryButton(
                    text = stringResource(if (hasActive) R.string.subscriptions_switch else R.string.subscriptions_subscribe),
                    onClick = onSubscribe,
                    enabled = enabled,
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: Payment) {
    AniTecPanel(modifier = Modifier.fillMaxWidth().mergedSemantics()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatSoles(payment.amount), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                Text(
                    formatRecordedAt(payment.paidAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusTag(
                text = if (payment.isPaid) stringResource(R.string.subscriptions_payment_paid) else payment.status,
                severity = if (payment.isPaid) Severity.Success else Severity.Warn,
            )
        }
    }
}

/** Shown when the browser sends the user back from the Stripe checkout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionResultScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionResultViewModel = hiltViewModel(),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscriptions_result_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            when (val current = phase) {
                CheckoutPhase.Confirming -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingState()
                    Text(stringResource(R.string.subscriptions_result_confirming), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                is CheckoutPhase.Confirmed -> ResultMessage(
                    title = stringResource(R.string.subscriptions_result_success),
                    body = current.planName?.let { stringResource(R.string.subscriptions_result_success_body, it) }
                        ?: stringResource(R.string.subscriptions_result_success_body_generic),
                    icon = Icons.Filled.CheckCircle,
                    actionLabel = stringResource(R.string.subscriptions_result_done),
                    onAction = onDone,
                )
                CheckoutPhase.Cancelled -> ResultMessage(
                    title = stringResource(R.string.subscriptions_result_cancelled),
                    body = stringResource(R.string.subscriptions_result_cancelled_body),
                    icon = Icons.Filled.Info,
                    actionLabel = stringResource(R.string.subscriptions_result_done),
                    onAction = onDone,
                )
                is CheckoutPhase.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ErrorMessage(stringResource(current.messageRes))
                    PrimaryButton(stringResource(R.string.subscriptions_result_retry), onClick = viewModel::confirm)
                    SecondaryButton(stringResource(R.string.subscriptions_result_done), onClick = onDone)
                }
            }
        }
    }
}

@Composable
private fun ResultMessage(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.mergedSemantics(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PrimaryButton(actionLabel, onClick = onAction)
    }
}
