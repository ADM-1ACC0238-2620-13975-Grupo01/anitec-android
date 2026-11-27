package com.anitec.platform.reminders.interfaces.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.sectionHeading
import com.anitec.platform.reminders.application.ReminderTaps
import com.anitec.platform.reminders.domain.ReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The user's choice of getting activity reminders. */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val settings: ReminderSettings,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.isEnabled())

    fun setEnabled(value: Boolean) = settings.setEnabled(value)
}

/** The activity the user opened from a reminder notification, until the shell has shown it. */
@HiltViewModel
class ReminderTapViewModel @Inject constructor(
    private val taps: ReminderTaps,
) : ViewModel() {
    val pending: StateFlow<Int?> = taps.pending

    fun consume() = taps.consume()
}

/**
 * Whether the system lets the app show notifications, and how to ask for it.
 * [allowed] is read again each time the screen comes back, because the user may change it in the system settings.
 */
class NotificationAccess(val allowed: Boolean, val request: () -> Unit)

@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    // After the system dialog was refused, asking again does nothing: the settings screen is the only way left.
    var refused by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        refused = !allowed
    }
    return NotificationAccess(allowed) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission && !refused) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * The reminders setting: a switch, and, when the system blocks notifications, a clear message and a way to turn them
 * on. The activities stay saved either way (TS-009 and US-027, scenario 2).
 */
@Composable
fun ReminderSettingsCard(modifier: Modifier = Modifier, viewModel: ReminderViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()

    AniTecPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = viewModel::setEnabled),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.reminders_title), modifier = Modifier.sectionHeading(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.reminders_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = null)
            }
            if (enabled && !access.allowed) NotificationsBlockedNotice(access)
        }
    }
}

/** Says that notifications are off for the app, that the activities are still saved, and offers to turn them on. */
@Composable
fun NotificationsBlockedNotice(access: NotificationAccess, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.reminders_blocked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        SecondaryButton(
            text = stringResource(R.string.reminders_enable),
            onClick = access.request,
            icon = Icons.Filled.NotificationsActive,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The notice of the activity list: shown only when there are reminders the system would not deliver. */
@Composable
fun NotificationsOffBanner(modifier: Modifier = Modifier, viewModel: ReminderViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    if (!enabled || access.allowed) return
    AniTecPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.reminders_off_title), style = MaterialTheme.typography.titleSmall)
            }
            NotificationsBlockedNotice(access)
        }
    }
}
