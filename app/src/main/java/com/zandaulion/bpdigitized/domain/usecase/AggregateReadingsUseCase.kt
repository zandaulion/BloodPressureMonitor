package com.zandaulion.bpdigitized.domain.usecase

import com.zandaulion.bpdigitized.domain.model.BPReading
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Collapses "bursts" — multiple readings taken back-to-back in one sitting — into a
 * single averaged point, mirroring clinical practice (AHA/ACC recommend averaging the
 * 2–3 readings of a sitting rather than trusting any single one).
 *
 * Clustering is single-linkage by time gap: a reading joins the current session while
 * it is within [windowMillis] of the previous reading AND the session's total span stays
 * within [maxSpanMillis] (the span guard stops a long string of near-threshold gaps from
 * snowballing into one giant cluster). Otherwise it starts a new session.
 *
 * Each session becomes one [BPReading]:
 *  - systolic / diastolic / pulse → rounded mean (pulse skips nulls; null if none)
 *  - timestamp → mean of the session
 *  - category  → **recomputed** from the averaged sys/dia (not averaged)
 *  - notes     → distinct member notes joined, so PDF callouts/labels survive averaging
 *
 * Singleton sessions pass through unchanged. The result is ordered by timestamp.
 */
class AggregateReadingsUseCase @Inject constructor(
    private val calculateZone: CalculateBPZoneUseCase,
) {

    operator fun invoke(
        readings: List<BPReading>,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
        maxSpanMillis: Long = DEFAULT_MAX_SPAN_MILLIS,
    ): List<BPReading> {
        if (readings.size < 2) return readings
        val sorted = readings.sortedBy { it.timestamp }

        val sessions = mutableListOf<MutableList<BPReading>>()
        var current = mutableListOf(sorted.first())
        for (r in sorted.drop(1)) {
            val withinGap  = r.timestamp - current.last().timestamp  <= windowMillis
            val withinSpan = r.timestamp - current.first().timestamp <= maxSpanMillis
            if (withinGap && withinSpan) {
                current.add(r)
            } else {
                sessions.add(current)
                current = mutableListOf(r)
            }
        }
        sessions.add(current)

        return sessions.map { session ->
            if (session.size == 1) session.first() else aggregate(session)
        }
    }

    private fun aggregate(session: List<BPReading>): BPReading {
        val sys    = session.map { it.systolic }.average().roundToInt()
        val dia    = session.map { it.diastolic }.average().roundToInt()
        val pulses = session.mapNotNull { it.pulse }
        val pulse  = if (pulses.isEmpty()) null else pulses.average().roundToInt()
        val ts     = session.sumOf { it.timestamp } / session.size
        val notes  = session.mapNotNull { it.notes?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" • ")
            .ifBlank { null }
        return BPReading(
            id        = session.first().id,
            timestamp = ts,
            systolic  = sys,
            diastolic = dia,
            pulse     = pulse,
            category  = calculateZone(sys, dia),
            notes     = notes,
        )
    }

    companion object {
        /** Readings closer than this to the previous one are treated as the same sitting. */
        const val DEFAULT_WINDOW_MILLIS   = 10L * 60 * 1000   // 10 minutes
        /** Hard cap on a single session's span, so near-threshold gaps can't snowball. */
        const val DEFAULT_MAX_SPAN_MILLIS = 30L * 60 * 1000   // 30 minutes
    }
}
