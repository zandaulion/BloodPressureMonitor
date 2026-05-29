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
import com.zandaulion.bpdigitized.domain.model.HealthAssessment
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import com.zandaulion.bpdigitized.domain.repository.UserProfileRepository
import com.zandaulion.bpdigitized.domain.billing.BillingManager
import com.zandaulion.bpdigitized.domain.usecase.CalculateHealthRiskUseCase
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
)

sealed interface DashboardEvent {
    data class ShowUndoSnackbar(val reading: BPReading) : DashboardEvent
    data class ShareExport(val file: File, val mimeType: String) : DashboardEvent
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
    private val billingManager: BillingManager,
    private val exportReadings: ExportReadingsUseCase,
    private val exportCsv: ExportCsvUseCase,
    private val exportPdf: ExportPdfUseCase,
    private val importReadings: ImportReadingsUseCase,
    private val syncToHealthConnect: SyncToHealthConnectUseCase,
    private val calculateHealthRisk: CalculateHealthRiskUseCase,
) : ViewModel() {

    private val selectedRange = MutableStateFlow(ChartRange.THIRTY_DAYS)

    val ui: StateFlow<DashboardUi> = combine(
        repository.observeAll(),
        selectedRange,
        profileRepository.observe(),
        billingManager.isProUser,
    ) { all, range, profile, isPro ->
        val chart = range.days?.let { days ->
            val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
            all.filter { it.timestamp >= cutoff }
        } ?: all
        val latest = all.maxByOrNull { it.timestamp }
        val assessment = latest?.let { calculateHealthRisk(it.category, profile) }
        DashboardUi(all, chart.sortedBy { it.timestamp }, range, assessment, isPro)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUi(emptyList(), emptyList(), ChartRange.THIRTY_DAYS, null, false),
    )

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun selectRange(range: ChartRange) {
        selectedRange.value = range
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

    fun requestExport() {
        viewModelScope.launch {
            runCatching { exportReadings() }
                .onSuccess { file -> _events.send(DashboardEvent.ShareExport(file, "application/json")) }
                .onFailure { _events.send(DashboardEvent.ExportFailed) }
        }
    }

    fun requestCsvExport() {
        viewModelScope.launch {
            runCatching { exportCsv() }
                .onSuccess { file -> _events.send(DashboardEvent.ShareExport(file, "text/csv")) }
                .onFailure { _events.send(DashboardEvent.ExportFailed) }
        }
    }

    fun requestPdfExport() {
        viewModelScope.launch {
            runCatching { exportPdf() }
                .onSuccess { file -> _events.send(DashboardEvent.ShareExport(file, "application/pdf")) }
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
