package com.anitec.platform.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.Severity
import com.anitec.platform.core.designsystem.statusColors

/**
 * Strip under the top bar that tells what the offline queue is doing: changes waiting for a connection, or
 * changes the server refused, which the user can send again or give up on.
 */
@Composable
fun SyncBanner(
    status: SyncStatus,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status.isIdle) return
    val failed = status.failed > 0
    val palette = MaterialTheme.statusColors.of(if (failed) Severity.Danger else Severity.Warn)
    Surface(modifier = modifier.fillMaxWidth(), color = palette.container, contentColor = palette.content) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(if (failed) Icons.Filled.SyncProblem else Icons.Filled.CloudOff, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (failed) {
                        pluralStringResource(R.plurals.sync_failed, status.failed, status.failed)
                    } else {
                        pluralStringResource(R.plurals.sync_pending, status.pending, status.pending)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (failed) {
                    Text(stringResource(R.string.sync_failed_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.sync_retry)) }
            if (failed) TextButton(onClick = onDiscard) { Text(stringResource(R.string.sync_discard)) }
        }
    }
}
