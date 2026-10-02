package com.anitec.platform.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anitec.platform.R

/** Full-screen form with a back arrow, a scrolling body and a snackbar host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreenScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Shows [messageRes] once in the snackbar, then reports it as consumed. */
@Composable
fun MessageEffect(@StringRes messageRes: Int?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val text = messageRes?.let { stringResource(it) }
    LaunchedEffect(text) {
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            onShown()
        }
    }
}

/** Card for one record: kicker, title, optional trailing content, key/value rows and a footer of actions. */
@Composable
fun RecordCard(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    details: List<Pair<String, String>> = emptyList(),
    footer: (@Composable RowScope.() -> Unit)? = null,
) {
    AniTecPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    if (kicker != null) {
                        Text(kicker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                if (trailing != null) trailing()
            }
            if (details.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                details.forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            label,
                            modifier = Modifier.weight(0.4f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(value, modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (footer != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = footer)
            }
        }
    }
}
