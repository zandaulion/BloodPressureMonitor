package com.zandaulion.bpdigitized.presentation.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.zandaulion.bpdigitized.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Exact-alarm permission isn't a runtime dialog — it's a Settings screen. Track its
    // granted state locally and refresh on resume so the card hides after the user returns.
    var exactAlarmGranted by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // When enabling, we ask for the notification permission first (a dialog); once it
    // resolves we chase the exact-alarm permission (a Settings screen) so the two prompts
    // don't collide. This flag remembers that the exact-alarm step is still owed.
    var pendingExactAlarm by remember { mutableStateOf(false) }

    val notifPermState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) {
            if (pendingExactAlarm) {
                pendingExactAlarm = false
                if (!canScheduleExactAlarms(context)) openExactAlarmSettings(context)
            }
        }
    } else null
    val notifGranted = notifPermState == null || notifPermState.status.isGranted

    // Fired when the master switch is turned on: request whichever permissions are missing.
    fun requestMissingPermissions() {
        val needExact = !exactAlarmGranted
        if (notifPermState != null && !notifPermState.status.isGranted) {
            pendingExactAlarm = needExact
            notifPermState.launchPermissionRequest()
        } else if (needExact) {
            openExactAlarmSettings(context)
        }
    }

    var showMorningPicker by remember { mutableStateOf(false) }
    var showEveningPicker by remember { mutableStateOf(false) }

    if (showMorningPicker) {
        TimePickerDialog(
            initial    = ui.morningMinutes,
            onConfirm  = { minutes -> viewModel.setMorningMinutes(minutes); showMorningPicker = false },
            onDismiss  = { showMorningPicker = false },
        )
    }
    if (showEveningPicker) {
        TimePickerDialog(
            initial    = ui.eveningMinutes,
            onConfirm  = { minutes -> viewModel.setEveningMinutes(minutes); showEveningPicker = false },
            onDismiss  = { showEveningPicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Master toggle ─────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.reminders_enable_label),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.reminders_enable_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = ui.isEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.toggleEnabled(enabled)
                            if (enabled) requestMissingPermissions()
                        },
                    )
                }
            }

            // ── Morning trigger ───────────────────────────────────────────────
            TimeCard(
                label       = stringResource(R.string.reminders_morning_label),
                description = stringResource(R.string.reminders_morning_desc),
                minutes     = ui.morningMinutes,
                enabled     = ui.isEnabled,
                onClick     = { if (ui.isEnabled) showMorningPicker = true },
            )

            // ── Evening trigger ───────────────────────────────────────────────
            TimeCard(
                label       = stringResource(R.string.reminders_evening_label),
                description = stringResource(R.string.reminders_evening_desc),
                minutes     = ui.eveningMinutes,
                enabled     = ui.isEnabled,
                onClick     = { if (ui.isEnabled) showEveningPicker = true },
            )

            // ── Permission cards (only those still missing, shown while enabled) ──
            if (ui.isEnabled && !notifGranted) {
                PermissionBanner(
                    title       = stringResource(R.string.reminders_notif_perm_title),
                    description = stringResource(R.string.reminders_notif_perm_desc),
                    buttonText  = stringResource(R.string.reminders_notif_perm_button),
                    onClick     = { notifPermState.launchPermissionRequest() },
                )
            }
            if (ui.isEnabled && !exactAlarmGranted) {
                PermissionBanner(
                    title       = stringResource(R.string.reminders_exact_alarm_title),
                    description = stringResource(R.string.reminders_exact_alarm_desc),
                    buttonText  = stringResource(R.string.reminders_exact_alarm_button),
                    onClick     = { openExactAlarmSettings(context) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            Text(
                text  = stringResource(R.string.reminders_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Permission helpers ───────────────────────────────────────────────────────

/** True when exact alarms can be scheduled (always true below API 31). */
private fun canScheduleExactAlarms(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    } else {
        true
    }

/** Opens the system "Alarms & reminders" settings so the user can allow exact alarms. */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun PermissionBanner(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClick) { Text(buttonText) }
        }
    }
}

@Composable
private fun TimeCard(
    label: String,
    description: String,
    minutes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick  = onClick,
        enabled  = enabled,
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text  = formatMinutes(minutes),
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour   = initial / 60,
        initialMinute = initial % 60,
        is24Hour      = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title    = { Text(stringResource(R.string.reminders_set_time)) },
        text     = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun formatMinutes(minutesSinceMidnight: Int): String =
    "%02d:%02d".format(minutesSinceMidnight / 60, minutesSinceMidnight % 60)
