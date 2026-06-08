package com.zandaulion.bpdigitized.domain.usecase

import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.model.Insight
import com.zandaulion.bpdigitized.domain.model.InsightKind
import com.zandaulion.bpdigitized.domain.model.InsightTone
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure on-device insights engine. No network, no cloud, no model download.
 *
 * All six insight types run as independent passes over the reading list:
 *   1. Morning vs evening (time-of-day split at noon)
 *   2. 30-day systolic trend (least-squares slope)
 *   3. Time-in-range (% of recent readings at NORMAL)
 *   4. Healthy-range streak (consecutive NORMAL readings from the latest)
 *   5. Weekday vs weekend comparison
 *   6. Tag correlation (strongest tag effect, if any)
 *
 * Each insight only appears when it clears both minimum-sample and
 * minimum-effect thresholds so we never surface noise.
 */
class GenerateInsightsUseCase @Inject constructor() {

    operator fun invoke(all: List<BPReading>): List<Insight> {
        if (all.size < MIN_TOTAL) return emptyList()
        val cutoff = System.currentTimeMillis() - 30L * MS_PER_DAY
        val recent = all.filter { it.timestamp >= cutoff }

        return buildList {
            morningVsEvening(recent)?.let(::add)
            trend30d(recent)?.let(::add)
            timeInRange(recent)?.let(::add)
            streak(all)?.let(::add)
            weekdayVsWeekend(recent)?.let(::add)
            tagCorrelations(recent).forEach(::add)
        }.sortedByDescending { it.priority }
    }

    // ── 1. Morning vs evening ─────────────────────────────────────────────

    private fun morningVsEvening(readings: List<BPReading>): Insight? {
        val morning = readings.filter { hourOf(it.timestamp) < 12 }
        val evening = readings.filter { hourOf(it.timestamp) >= 12 }
        if (morning.size < MIN_SIDE || evening.size < MIN_SIDE) return null

        val mSys = morning.map { it.systolic }.average()
        val eSys = evening.map { it.systolic }.average()
        val mDia = morning.map { it.diastolic }.average()
        val eDia = evening.map { it.diastolic }.average()
        val sysDelta = mSys - eSys
        if (abs(sysDelta) < MIN_DELTA) return null

        val sys = abs(sysDelta).roundToInt().toString()
        val dia = abs(mDia - eDia).roundToInt().toString()
        return if (sysDelta > 0) {
            Insight(InsightKind.MORNING_HIGHER, InsightTone.NEUTRAL, listOf(sys, dia), 50)
        } else {
            Insight(InsightKind.EVENING_HIGHER, InsightTone.NEUTRAL, listOf(sys, dia), 50)
        }
    }

    // ── 2. 30-day trend ───────────────────────────────────────────────────

    private fun trend30d(readings: List<BPReading>): Insight? {
        if (readings.size < MIN_TREND) return null
        val sorted = readings.sortedBy { it.timestamp }
        val firstTs = sorted.first().timestamp
        val xs = sorted.map { (it.timestamp - firstTs).toDouble() / MS_PER_DAY }
        val ys = sorted.map { it.systolic.toDouble() }
        val slope = linregress(xs, ys) ?: return null
        val projected = slope * 30.0            // projected change over 30 days
        if (abs(projected) < MIN_TREND_DELTA) return null

        val delta = abs(projected).roundToInt().toString()
        return if (projected < 0) {
            Insight(InsightKind.TREND_DOWN, InsightTone.POSITIVE, listOf(delta), 85)
        } else {
            Insight(InsightKind.TREND_UP, InsightTone.WATCH, listOf(delta), 80)
        }
    }

    // ── 3. Time in range ──────────────────────────────────────────────────

    private fun timeInRange(readings: List<BPReading>): Insight? {
        if (readings.size < MIN_RANGE) return null
        val pct = (readings.count { it.category == BPCategory.NORMAL } * 100) / readings.size
        val tone = when {
            pct >= 80 -> InsightTone.POSITIVE
            pct < 60  -> InsightTone.WATCH
            else      -> InsightTone.NEUTRAL
        }
        return Insight(InsightKind.TIME_IN_RANGE, tone, listOf(pct.toString()), 65)
    }

    // ── 4. Streak ─────────────────────────────────────────────────────────

    private fun streak(all: List<BPReading>): Insight? {
        var count = 0
        for (r in all.sortedByDescending { it.timestamp }) {
            if (r.category == BPCategory.NORMAL) count++ else break
        }
        if (count < MIN_STREAK) return null
        return Insight(InsightKind.STREAK, InsightTone.POSITIVE, listOf(count.toString()), 90)
    }

    // ── 5. Weekday vs weekend ─────────────────────────────────────────────

    private fun weekdayVsWeekend(readings: List<BPReading>): Insight? {
        val weekday = readings.filter { !isWeekend(it.timestamp) }
        val weekend = readings.filter { isWeekend(it.timestamp) }
        if (weekday.size < MIN_SIDE || weekend.size < MIN_SIDE) return null

        val wdSys = weekday.map { it.systolic }.average()
        val weSys = weekend.map { it.systolic }.average()
        val delta = abs(wdSys - weSys)
        if (delta < MIN_DELTA) return null

        val d = delta.roundToInt().toString()
        return if (weSys > wdSys) {
            Insight(InsightKind.WEEKEND_HIGHER, InsightTone.NEUTRAL, listOf(d), 40)
        } else {
            Insight(InsightKind.WEEKDAY_HIGHER, InsightTone.NEUTRAL, listOf(d), 40)
        }
    }

    // ── 6. Tag correlations ───────────────────────────────────────────────

    private fun tagCorrelations(readings: List<BPReading>): List<Insight> {
        if (readings.isEmpty()) return emptyList()
        val baseline = readings.map { it.systolic }.average()
        val candidates = BpTag.entries.mapNotNull { tag ->
            val tagged = readings.filter { tag in it.tags }
            if (tagged.size < MIN_TAG) return@mapNotNull null
            val delta = tagged.map { it.systolic }.average() - baseline
            if (abs(delta) < MIN_TAG_DELTA) return@mapNotNull null
            val d = abs(delta).roundToInt().toString()
            if (delta > 0) {
                Insight(InsightKind.TAG_HIGHER, InsightTone.WATCH,
                    listOf(tag.name, d), 75)
            } else {
                Insight(InsightKind.TAG_LOWER, InsightTone.POSITIVE,
                    listOf(tag.name, d), 70)
            }
        }
        // Surface only the single strongest tag signal to avoid overwhelming the card.
        return listOfNotNull(candidates.maxByOrNull { it.priority })
    }

    // ── Math helpers ──────────────────────────────────────────────────────

    /**
     * Ordinary least-squares slope (y per x unit).
     * Returns null when the x values are constant (degenerate dataset).
     */
    private fun linregress(xs: List<Double>, ys: List<Double>): Double? {
        val n = xs.size
        if (n < 2) return null
        val xMean = xs.average()
        val yMean = ys.average()
        val ssXX  = xs.sumOf { (it - xMean) * (it - xMean) }
        if (ssXX == 0.0) return null
        val ssXY  = xs.indices.sumOf { (xs[it] - xMean) * (ys[it] - yMean) }
        return ssXY / ssXX
    }

    private fun hourOf(ts: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun isWeekend(ts: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        return cal.get(Calendar.DAY_OF_WEEK).let {
            it == Calendar.SATURDAY || it == Calendar.SUNDAY
        }
    }

    // ── Thresholds ────────────────────────────────────────────────────────

    companion object {
        private const val MS_PER_DAY       = 24L * 60 * 60 * 1_000
        private const val MIN_TOTAL        = 5     // minimum total readings to run the engine
        private const val MIN_SIDE         = 5     // minimum readings per side for split comparisons
        private const val MIN_DELTA        = 3.0   // minimum mmHg difference to surface a comparison
        private const val MIN_TREND        = 7     // minimum readings needed for a trend
        private const val MIN_TREND_DELTA  = 4.0   // minimum projected 30-day change (mmHg) to surface
        private const val MIN_RANGE        = 7     // minimum readings needed for time-in-range
        private const val MIN_STREAK       = 3     // minimum consecutive normals to surface a streak
        private const val MIN_TAG          = 5     // minimum tagged readings to compute a correlation
        private const val MIN_TAG_DELTA    = 5.0   // minimum tag-effect size (mmHg) to surface
    }
}
