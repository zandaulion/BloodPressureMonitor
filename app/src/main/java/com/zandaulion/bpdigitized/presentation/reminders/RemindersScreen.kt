package com.zandaulion.bpdigitized.presentation.reminders

import android.Manifest
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Runtime permission for POST_NOTIFICATIONS (API 33+)
    val notifPermState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    LaunchedEffect(viewModel.events, lifecycleOwner) {
        viewModel.events.flowWithLifecycle(lifecycleOwner.lifecycle).collect { event ->
            when (event) {
                RemindersEvent.OpenExactAlarmSettings -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                RemindersEvent.RequestNotificationPermission ->
                    notifPermState?.launchPermissionRequest()
            }
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
                title = { Text("Reminders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Notification permission banner ────────────────────────────────
            if (notifPermState != null && !notifPermState.status.isGranted) {
                PermissionBanner(
                    title       = "Notification permission required",
                    description = "Allow notifications so reminders can reach you.",
                    buttonText  = "Grant Permission",
                    onClick     = { notifPermState.launchPermissionRequest() },
                )
            }

            // ── Exact-alarm permission banner ─────────────────────────────────
            if (ui.needsExactAlarmPermission) {
                PermissionBanner(
                    title       = "Exact alarm permission required",
                    description = "For precise reminders, allow this app to schedule exact alarms.",
                    buttonText  = "Open Settings",
                    onClick     = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }

            // ── Master toggle ─────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Reminders", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Morning and evening BP check notifications",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = ui.isEnabled,
                        onCheckedChange = { viewModel.toggleEnabled(it) },
                    )
                }
            }

            // ── Morning trigger ───────────────────────────────────────────────
            TimeCard(
                label       = "Morning Check",
                description = "Taken ~30 min after waking, before coffee or medication",
                minutes     = ui.morningMinutes,
                enabled     = ui.isEnabled,
                onClick     = { if (ui.isEnabled) showMorningPicker = true },
            )

            // ── Evening trigger ───────────────────────────────────────────────
            TimeCard(
                label       = "Evening Check",
                description = "Taken while relaxing in the evening",
                minutes     = ui.eveningMinutes,
                enabled     = ui.isEnabled,
                onClick     = { if (ui.isEnabled) showEveningPicker = true },
            )

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            Text(
                text  = "Tap a time to change it. Reminders fire each day at the chosen time and reschedule automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        title    = { Text("Set time") },
        text     = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatMinutes(minutesSinceMidnight: Int): String =
    "%02d:%02d".format(minutesSinceMidnight / 60, minutesSinceMidnight % 60)
