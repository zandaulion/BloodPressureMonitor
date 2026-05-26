package com.example.bpdigitizer.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bpdigitizer.domain.model.BPReading
import com.example.bpdigitizer.domain.repository.BPRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
)

sealed interface DashboardEvent {
    data class ShowUndoSnackbar(val reading: BPReading) : DashboardEvent
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: BPRepository,
) : ViewModel() {

    private val selectedRange = MutableStateFlow(ChartRange.THIRTY_DAYS)
    private val pendingDeletions = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingJobs = mutableMapOf<Long, Job>()

    val ui: StateFlow<DashboardUi> = combine(
        repository.observeAll(),
        selectedRange,
        pendingDeletions,
    ) { all, range, pending ->
        val visible = all.filterNot { it.id in pending }
        val chart = range.days?.let { days ->
            val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
            visible.filter { it.timestamp >= cutoff }
        } ?: visible
        DashboardUi(visible, chart.sortedBy { it.timestamp }, range)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUi(emptyList(), emptyList(), ChartRange.THIRTY_DAYS),
    )

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun selectRange(range: ChartRange) {
        selectedRange.value = range
    }

    fun requestDelete(reading: BPReading) {
        pendingDeletions.update { it + reading.id }
        pendingJobs[reading.id] = viewModelScope.launch {
            _events.send(DashboardEvent.ShowUndoSnackbar(reading))
            delay(UNDO_WINDOW_MILLIS)
            repository.delete(reading.id)
            pendingDeletions.update { it - reading.id }
            pendingJobs.remove(reading.id)
        }
    }

    fun undoDelete(reading: BPReading) {
        pendingJobs.remove(reading.id)?.cancel()
        pendingDeletions.update { it - reading.id }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val UNDO_WINDOW_MILLIS = 5_000L
    }
}
