package com.zandaulion.bpdigitized.presentation.dashboard

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.model.HealthAssessment
import com.zandaulion.bpdigitized.domain.model.Insight
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import com.zandaulion.bpdigitized.domain.repository.ChartSettingsRepository
import com.zandaulion.bpdigitized.domain.repository.UserProfileRepository
import com.zandaulion.bpdigitized.domain.billing.BillingManager
import com.zandaulion.bpdigitized.domain.usecase.AggregateReadingsUseCase
import com.zandaulion.bpdigitized.domain.usecase.CalculateHealthRiskUseCase
import com.zandaulion.bpdigitized.domain.usecase.GenerateInsightsUseCase
import com.zandaulion.bpdigitized.domain.usecase.ExportCsvUseCase
import com.zandaulion.bpdigitized.domain.usecase.ExportPdfUseCase
import com.zandaulion.bpdigitized.domain.usecase.ExportReadingsUseCase
import com.zandaulion.bpdigitized.domain.usecase.ImportReadingsUseCase
import com.zandaulion.bpdigitized.domain.usecase.SyncToHealthConnectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChartRange(val days: Int?, val label: String) {
    SEVEN_DAYS(7, "7d"),
    THIRTY_DAYS(30, "30d"),
    NINETY_DAYS(90, "90d"),
    ALL(null, "All"),
}

data class DashboardUi(
    val readings: List<BPReading>,
    val chartReadings: List<BPReading>,
    val selectedRange: ChartRange,
    /** Risk assessment for the most recent reading, null when there are no readings. */
    val healthAssessment: HealthAssessment?,
    /** True if the user has purchased the pro subscription. */
    val isProUser: Boolean = false,
    /** True when back-to-back readings are averaged into one smoothed chart point. */
    val smoothBursts: Boolean = true,
    /** On-device pattern insights derived from the full reading history. */
    val insights: List<Insight> = emptyList(),
)

enum class ExportType { JSON, CSV, PDF }

/**
 * Transient UI state for the long-press tag editor bottom sheet.
 * [selectedTags] mirrors the user's in-progress selection; it is only
 * written to the DB when the user taps Save.
 */
data class TagEditorState(
    val reading: BPReading,
    val selectedTags: Set<BpTag>,
)

sealed interface DashboardEvent {
    data class ShowUndoSnackbar(val reading: BPReading) : DashboardEvent
    data class ShareExport(val file: File, val mimeType: String, val exportType: ExportType) : DashboardEvent
    data object ExportFailed : DashboardEvent
    data class ImportSucceeded(val count: Int) : DashboardEvent
    data object ImportFailed : DashboardEvent
    /** Tell the screen to launch the HC permission dialog with these permission strings. */
    data class RequestHealthConnectPermissions(val permissions: Set<String>) : DashboardEvent
    data class SyncToHealthConnectSucceeded(val count: Int) : DashboardEvent
    data object SyncToHealthConnectFailed : DashboardEvent
    /** HC is not installed; screen should offer to redirect to Play Store. */
    data object HealthConnectInstallRequired : DashboardEvent
    data object HealthConnectPermissionsDenied : DashboardEvent
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BPRepository,
    private val profileRepository: UserProfileRepository,
    private val chartSettings: ChartSettingsRepository,
    private val billingManager: BillingManager,
    private val aggregateReadings: AggregateReadingsUseCase,
    private val exportReadings: ExportReadingsUseCase,
    private val exportCsv: ExportCsvUseCase,
    private val exportPdf: ExportPdfUseCase,
    private val importReadings: ImportReadingsUseCase,
    private val syncToHealthConnect: SyncToHealthConnectUseCase,
    private val calculateHealthRisk: CalculateHealthRiskUseCase,
    private val generateInsights: GenerateInsightsUseCase,
) : ViewModel() {

    private val selectedRange = MutableStateFlow(ChartRange.THIRTY_DAYS)

    val ui: StateFlow<DashboardUi> = combine(
        repository.observeAll(),
        selectedRange,
        profileRepository.observe(),
        billingManager.isProUser,
        chartSettings.observeSmoothBursts(),
    ) { all, range, profile, isPro, smooth ->
        val inRange = range.days?.let { days ->
            val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
            all.filter { it.timestamp >= cutoff }
        } ?: all
        // Smoothing affects only the chart; the readings list below stays raw.
        val chart = if (smooth) aggregateReadings(inRange) else inRange.sortedBy { it.timestamp }
        val latest = all.maxByOrNull { it.timestamp }
        val assessment = latest?.let { calculateHealthRisk(it.category, profile) }
        val insights = generateInsights(all)
        DashboardUi(all, chart, range, assessment, isPro, smooth, insights)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUi(emptyList(), emptyList(), ChartRange.THIRTY_DAYS, null, false),
    )

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Tag editor (long-press) ────────────────────────────────────────────
    private val _tagEditorState = MutableStateFlow<TagEditorState?>(null)
    val tagEditorState: StateFlow<TagEditorState?> = _tagEditorState.asStateFlow()

    fun selectRange(range: ChartRange) {
        selectedRange.value = range
    }

    fun setSmoothBursts(enabled: Boolean) {
        viewModelScope.launch {
            chartSettings.setSmoothBursts(enabled)
        }
    }

    fun requestDelete(reading: BPReading) {
        viewModelScope.launch {
            repository.delete(reading.id)
            _events.send(DashboardEvent.ShowUndoSnackbar(reading))
        }
    }

    /** Re-inserts the reading with its original ID, restoring it after an undo. */
    fun undoDelete(reading: BPReading) {
        viewModelScope.launch {
            repository.insert(reading)
        }
    }

    /** Opens the tag editor bottom sheet pre-loaded with the reading's current tags. */
    fun startEditTags(reading: BPReading) {
        _tagEditorState.value = TagEditorState(reading, reading.tags.toSet())
    }

    /** Toggles a single tag inside the editor without touching the DB yet. */
    fun toggleTagInEditor(tag: BpTag) {
        _tagEditorState.update { current ->
            current?.let {
                val updated = if (tag in it.selectedTags) it.selectedTags - tag else it.selectedTags + tag
                it.copy(selectedTags = updated)
            }
        }
    }

    /** Persists the editor's current selection to the DB and closes the sheet. */
    fun saveEditedTags() {
        val state = _tagEditorState.value ?: return
        viewModelScope.launch {
            repository.updateTags(state.reading.id, state.selectedTags.sortedBy { it.ordinal })
            _tagEditorState.value = null
        }
    }

    /** Closes the tag editor without saving. */
    fun cancelEditTags() {
        _tagEditorState.value = null
    }

    fun requestExport() {
        viewModelScope.launch {
            runCatching { exportReadings() }
                .onSuccess { file -> _events.send(DashboardEvent.ShareExport(file, "application/json", ExportType.JSON)) }
                .onFailure { _events.send(DashboardEvent.ExportFailed) }
        }
    }

    fun requestCsvExport() {
        viewModelScope.launch {
            runCatching { exportCsv() }
                .onSuccess { file -> _events.send(DashboardEvent.ShareExport(file, "text/csv", ExportType.CSV)) }
                .onFailure { _events.send(DashboardEvent.ExportFailed) }
        }
    }

    fun requestPdfExport() {
        viewModelScope.launch {
            runCatching { exportPdf() }
                .onSuccess { file -> _events.send(DashboardEvent.ShareExport(file, "application/pdf", ExportType.PDF)) }
                .onFailure { _events.send(DashboardEvent.ExportFailed) }
        }
    }

    fun requestImport(uri: Uri) {
        viewModelScope.launch {
            runCatching { importReadings(uri) }
                .onSuccess { count -> _events.send(DashboardEvent.ImportSucceeded(count)) }
                .onFailure { _events.send(DashboardEvent.ImportFailed) }
        }
    }

    /** Called when the user taps the Health Connect sync button. */
    fun onSyncToHealthConnectClicked() {
        viewModelScope.launch {
            try {
                val status = HealthConnectClient.getSdkStatus(context)
                Log.d(TAG, "HC getSdkStatus = $status")
                when (status) {
                    HealthConnectClient.SDK_UNAVAILABLE -> {
                        Log.w(TAG, "HC: SDK_UNAVAILABLE")
                        _events.send(DashboardEvent.HealthConnectInstallRequired)
                        return@launch
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        Log.w(TAG, "HC: SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED")
                        _events.send(DashboardEvent.HealthConnectInstallRequired)
                        return@launch
                    }
                }
                val client  = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                Log.d(TAG, "HC granted permissions: $granted")
                Log.d(TAG, "HC required permissions: $HC_PERMISSIONS")
                if (!granted.containsAll(HC_PERMISSIONS)) {
                    // Always send the complete required set — HC's launcher needs all
                    // requested permissions at once to show the correct dialog.
                    _events.send(DashboardEvent.RequestHealthConnectPermissions(HC_PERMISSIONS))
                } else {
                    doHealthConnectSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "HC pre-flight failed", e)
                _events.send(DashboardEvent.SyncToHealthConnectFailed)
            }
        }
    }

    /** Called by the screen after the HC permission dialog returns. */
    fun onHealthConnectPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            Log.d(TAG, "HC permissions result: $granted")
            if (!granted.containsAll(HC_PERMISSIONS)) {
                _events.send(DashboardEvent.HealthConnectPermissionsDenied)
            } else {
                doHealthConnectSync()
            }
        }
    }

    private suspend fun doHealthConnectSync() {
        runCatching { syncToHealthConnect() }
            .onSuccess { count -> _events.send(DashboardEvent.SyncToHealthConnectSucceeded(count)) }
            .onFailure { e ->
                Log.e(TAG, "HC sync failed", e)
                _events.send(DashboardEvent.SyncToHealthConnectFailed)
            }
    }

    private companion object {
        const val TAG = "BpDashboard"
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

        val HC_PERMISSIONS = setOf(
            HealthPermission.getWritePermission(BloodPressureRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
        )
    }
}
