package com.zandaulion.bpdigitized.presentation.dashboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.presentation.components.CategoryBadge
import com.zandaulion.bpdigitized.presentation.components.HealthRiskCard
import com.zandaulion.bpdigitized.presentation.extensions.labelRes
import kotlinx.coroutines.delay
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
    onHelp: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var displayPaywall by remember { mutableStateOf(false) }
    // Set when an export completes; promoted to exportedMessage on the next ON_RESUME
    // (i.e. when the user dismisses the Android share sheet and returns to the app).
    var pendingExportMessage by remember { mutableStateOf<String?>(null) }
    var exportedMessage by remember { mutableStateOf<String?>(null) }

    // Promote the pending message once the user returns from the share sheet.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingExportMessage != null) {
                exportedMessage = pendingExportMessage
                pendingExportMessage = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-clear the export confirmation after 1 second.
    LaunchedEffect(exportedMessage) {
        if (exportedMessage != null) {
            delay(1_000)
            exportedMessage = null
        }
    }

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
                        message = context.getString(R.string.dashboard_snack_deleted),
                        actionLabel = context.getString(R.string.dashboard_snack_undo),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.reading)
                    }
                }
                is DashboardEvent.ShareExport -> {
                    pendingExportMessage = context.getString(
                        R.string.dashboard_snack_export_success,
                        context.getString(event.exportType.labelRes()),
                    )
                    shareFile(context, event.file, event.mimeType)
                }
                DashboardEvent.ExportFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.dashboard_snack_export_failed))
                }
                is DashboardEvent.ImportSucceeded -> {
                    val msg = if (event.count == 0) {
                        context.getString(R.string.dashboard_snack_import_none)
                    } else {
                        context.resources.getQuantityString(
                            R.plurals.dashboard_snack_imported, event.count, event.count,
                        )
                    }
                    snackbarHostState.showSnackbar(message = msg)
                }
                DashboardEvent.ImportFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.dashboard_snack_import_failed))
                }
                is DashboardEvent.RequestHealthConnectPermissions -> {
                    hcPermLauncher.launch(event.permissions)
                }
                is DashboardEvent.SyncToHealthConnectSucceeded -> {
                    snackbarHostState.showSnackbar(
                        context.resources.getQuantityString(
                            R.plurals.dashboard_snack_synced, event.count, event.count,
                        ),
                    )
                }
                DashboardEvent.SyncToHealthConnectFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.dashboard_snack_hc_sync_failed))
                }
                DashboardEvent.HealthConnectInstallRequired -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.dashboard_snack_hc_unavailable),
                        actionLabel = context.getString(R.string.dashboard_snack_hc_install),
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
                        message = context.getString(R.string.dashboard_snack_hc_permission),
                        actionLabel = context.getString(R.string.action_open_settings),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        openHealthConnectSettings(context)
                    }
                }
            }
        }
    }

    // Landscape: chart + range chips, full-screen.
    // Wrap in Surface so the chart gets the correct MaterialTheme background colour in
    // both light and dark mode. Without it the raw window background is used, which
    // doesn't follow the Material theme and ignores dynamic dark/light switching.
    if (isLandscape) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
            ) {
                RangeChipRow(
                    selected = ui.selectedRange,
                    onSelect = viewModel::selectRange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                BPTrendChart(
                    readings = ui.chartReadings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding(),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onReminders) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = stringResource(R.string.dashboard_cd_reminders))
                    }
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Default.Person, contentDescription = stringResource(R.string.dashboard_cd_profile))
                    }
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.dashboard_cd_import))
                    }
                    Box {
                        IconButton(
                            onClick = { exportMenuExpanded = true },
                            enabled = ui.readings.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = stringResource(R.string.dashboard_cd_export))
                        }
                        DropdownMenu(
                            expanded = exportMenuExpanded,
                            onDismissRequest = { exportMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dashboard_export_json)) },
                                onClick = {
                                    exportMenuExpanded = false
                                    viewModel.requestExport()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dashboard_export_csv)) },
                                onClick = {
                                    exportMenuExpanded = false
                                    viewModel.requestCsvExport()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dashboard_export_pdf)) },
                                onClick = {
                                    exportMenuExpanded = false
                                    viewModel.requestPdfExport()
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = viewModel::onSyncToHealthConnectClicked,
                        enabled = ui.readings.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.MonitorHeart, contentDescription = stringResource(R.string.dashboard_cd_sync_hc))
                    }
                    IconButton(onClick = onHelp) {
                        Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.dashboard_cd_help))
                    }
                }
                HorizontalDivider()
            }
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallFloatingActionButton(onClick = onAddManually) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.dashboard_cd_add_manually))
                }
                Box {
                    FloatingActionButton(onClick = {
                        if (ui.isProUser) {
                            onAddViaCamera()
                        } else {
                            displayPaywall = true
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.dashboard_cd_scan))
                    }
                    if (!ui.isProUser) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.dashboard_cd_premium),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.5.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                                .padding(3.dp),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        if (displayPaywall) {
            PaywallDialog(
                paywallDialogOptions = PaywallDialogOptions.Builder()
                    .setRequiredEntitlementIdentifier("pro")
                    .setShouldDisplayDismissButton(true)
                    .setDismissRequest { displayPaywall = false }
                    .setListener(object : PaywallListener {
                        override fun onPurchaseStarted(rcPackage: com.revenuecat.purchases.Package) {}
                        override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                            displayPaywall = false
                            onAddViaCamera()
                        }
                        override fun onPurchaseError(error: PurchasesError) {}
                        override fun onPurchaseCancelled() {}
                        override fun onRestoreStarted() {}
                        override fun onRestoreCompleted(customerInfo: CustomerInfo) {}
                        override fun onRestoreError(error: PurchasesError) {}
                    })
                    .build()
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
                        text = stringResource(R.string.dashboard_empty),
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
        } // Column

        // ── Export confirmation pill ───────────────────────────────────────
        AnimatedVisibility(
            visible = exportedMessage != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit  = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = exportedMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
        } // Box
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
                label = { Text(stringResource(range.labelRes())) },
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
    // Keep a stable reference to the latest onDelete so we can capture it in a
    // remember-once lambda without forcing a new lambda object each recomposition.
    // SwipeToDismissBox uses onDismiss as a LaunchedEffect key, so an unstable
    // lambda would re-trigger the effect and double-fire the delete.
    val currentOnDelete = rememberUpdatedState(onDelete)

    // *** Use plain `remember`, NOT rememberSaveable. ***
    // rememberSaveable would persist the dismissed offset across composition entries
    // (keyed by reading.id in the LazyColumn). When an item is restored via Undo it
    // would come back still visually dismissed, and — worse — SwipeToDismissBox's
    // internal LaunchedEffect would see settledValue != Settled and immediately fire
    // onDismiss again, re-deleting the reading right after restore.
    val density = LocalDensity.current
    val dismissState = remember {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = with(density) { { 56.dp.toPx() } },
        )
    }

    // Stable lambda — created once, reads the latest onDelete via the State wrapper.
    val onDismissStable = remember { { _: SwipeToDismissBoxValue -> currentOnDelete.value() } }

    SwipeToDismissBox(
        state = dismissState,
        onDismiss = onDismissStable,
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
                    contentDescription = stringResource(R.string.dashboard_cd_delete),
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
    // SwipeToDismissBox renders the background (trash icon) at matchParentSize below this
    // composable. Without an opaque background here the icon bleeds through wherever the
    // foreground is transparent — most visibly on tall rows where the icon's CenterEnd
    // position sits below the CategoryBadge.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
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
                val pulseSuffix = if (reading.pulse != null) {
                    stringResource(R.string.dashboard_reading_pulse_format, reading.pulse)
                } else ""
                Text(
                    text = "${reading.systolic}/${reading.diastolic}$pulseSuffix",
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

private fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export BP readings").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
