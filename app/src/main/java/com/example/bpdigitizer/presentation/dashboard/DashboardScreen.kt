package com.example.bpdigitizer.presentation.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.example.bpdigitizer.domain.model.BPReading
import com.example.bpdigitizer.presentation.components.CategoryBadge
import com.example.bpdigitizer.presentation.components.HealthRiskCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddViaCamera: () -> Unit,
    onAddManually: () -> Unit,
    onProfile: () -> Unit,
    onReminders: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.requestImport(it) } }

    val hcPermLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onHealthConnectPermissionsResult(granted) }

    LaunchedEffect(viewModel.events, lifecycleOwner) {
        viewModel.events.flowWithLifecycle(lifecycleOwner.lifecycle).collect { event ->
            when (event) {
                is DashboardEvent.ShowUndoSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Reading deleted",
                        actionLabel = "Undo",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.reading)
                    }
                }
                is DashboardEvent.ShareExport -> shareJsonFile(context, event.file)
                DashboardEvent.ExportFailed -> {
                    snackbarHostState.showSnackbar("Export failed")
                }
                is DashboardEvent.ImportSucceeded -> {
                    snackbarHostState.showSnackbar(
                        message = if (event.count == 0) "No new readings to import"
                                  else "Imported ${event.count} reading${if (event.count != 1) "s" else ""}",
                    )
                }
                DashboardEvent.ImportFailed -> {
                    snackbarHostState.showSnackbar("Import failed — make sure the file is a valid BP export")
                }
                is DashboardEvent.RequestHealthConnectPermissions -> {
                    hcPermLauncher.launch(event.permissions)
                }
                is DashboardEvent.SyncToHealthConnectSucceeded -> {
                    snackbarHostState.showSnackbar(
                        "Synced ${event.count} reading${if (event.count != 1) "s" else ""} to Health Connect",
                    )
                }
                DashboardEvent.SyncToHealthConnectFailed -> {
                    snackbarHostState.showSnackbar("Sync to Health Connect failed")
                }
                DashboardEvent.HealthConnectInstallRequired -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Health Connect is not available on this device",
                        actionLabel = "Install",
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val intent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
                            .apply { setPackage("com.android.vending") }
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                DashboardEvent.HealthConnectPermissionsDenied -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Please grant blood pressure permission in Health Connect",
                        actionLabel = "Open Settings",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        openHealthConnectSettings(context)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blood Pressure") },
                actions = {
                    IconButton(onClick = onReminders) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "Reminders")
                    }
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Health profile")
                    }
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import data")
                    }
                    IconButton(
                        onClick = viewModel::requestExport,
                        enabled = ui.readings.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export data")
                    }
                    IconButton(
                        onClick = viewModel::onSyncToHealthConnectClicked,
                        enabled = ui.readings.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.MonitorHeart, contentDescription = "Sync to Health Connect")
                    }
                },
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ui.hasApiKey) {
                    SmallFloatingActionButton(onClick = onAddManually) {
                        Icon(Icons.Default.Edit, contentDescription = "Add reading manually")
                    }
                    FloatingActionButton(onClick = onAddViaCamera) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scan reading with camera")
                    }
                } else {
                    FloatingActionButton(onClick = onAddManually) {
                        Icon(Icons.Default.Edit, contentDescription = "Add reading manually")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            RangeChipRow(
                selected = ui.selectedRange,
                onSelect = viewModel::selectRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            BPTrendChart(
                readings = ui.chartReadings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            if (ui.readings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No readings yet. Tap the pencil button to enter one, or the camera button to scan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Risk assessment card — always first when there are readings
                    ui.healthAssessment?.let { assessment ->
                        item(key = "health_risk_card") {
                            HealthRiskCard(
                                assessment = assessment,
                                onEditProfile = onProfile,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(items = ui.readings, key = { it.id }) { reading ->
                        SwipeableReadingRow(
                            reading = reading,
                            onDelete = { viewModel.requestDelete(reading) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChipRow(
    selected: ChartRange,
    onSelect: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableReadingRow(
    reading: BPReading,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        ReadingRow(reading = reading)
    }
}

@Composable
private fun ReadingRow(reading: BPReading) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTimestamp(reading.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(reading.systolic).append('/').append(reading.diastolic)
                        reading.pulse?.let { append(" · ").append(it).append(" bpm") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            CategoryBadge(category = reading.category)
        }
        reading.notes?.let { notes ->
            Text(
                text = notes,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val ROW_DATE_FORMAT = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

private fun formatTimestamp(ts: Long): String = ROW_DATE_FORMAT.format(Date(ts))

/**
 * Opens the Health Connect settings screen where the user can grant/revoke permissions for
 * this app. Tries the dedicated HC settings action first, then falls back to launching the
 * HC app directly.
 */
private fun openHealthConnectSettings(context: Context) {
    // "android.health.connect.action.HEALTH_HOME_SETTINGS" is the stable action for opening
    // the Health Connect settings / permissions management screen on Android 14+.
    val tried = listOf(
        "android.health.connect.action.HEALTH_HOME_SETTINGS",
        "android.health.connect.action.MANAGE_HEALTH_DATA",
    )
    for (action in tried) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }
    // Last resort: open the HC app itself
    context.packageManager
        .getLaunchIntentForPackage("com.google.android.apps.healthdata")
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?.let { context.startActivity(it) }
}

private fun shareJsonFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export BP readings").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
