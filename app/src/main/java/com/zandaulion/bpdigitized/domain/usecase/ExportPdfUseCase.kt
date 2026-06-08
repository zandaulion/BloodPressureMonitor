package com.zandaulion.bpdigitized.domain.usecase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.model.meanArterialPressure
import com.zandaulion.bpdigitized.domain.model.pulsePressure
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import com.zandaulion.bpdigitized.domain.repository.ChartSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Exports all readings to a multi-page PDF:
 *  - One landscape page per active time range (7 d, 30 d, all-time),
 *    with a line chart, numbered note callouts, a stats bar, and a note legend.
 *  - One or more portrait pages with the full readings table.
 *
 * Pages for a range are skipped when fewer than 2 readings exist for it, or
 * when the range would be identical to the preceding one.
 */
class ExportPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BPRepository,
    private val chartSettings: ChartSettingsRepository,
    private val aggregateReadings: AggregateReadingsUseCase,
) {

    // ── Entry point ───────────────────────────────────────────────────────────

    suspend operator fun invoke(): File = withContext(Dispatchers.Default) {
        val all = repository.getAll().sortedBy { it.timestamp }
        val smooth = chartSettings.observeSmoothBursts().first()
        val stamp = STAMP_FMT.format(Date())
        val file  = File(context.cacheDir, "bp_export_$stamp.pdf")
        val doc   = PdfDocument()
        try {
            var pageNum = 1
            buildChartRanges(all).forEach { range ->
                // Smooth the chart's readings (averaging back-to-back sittings); merged
                // notes are preserved so numbered callouts/labels still appear. The table
                // pages below stay raw.
                val drawn = if (smooth)
                    range.copy(readings = aggregateReadings(range.readings))
                else range
                drawChartPage(doc, pageNum++, drawn)
            }
            // Table pages show only the last 30 days (newest-first)
            val tableCutoff = System.currentTimeMillis() - 30L * MS_PER_DAY
            val tableReadings = all.filter { it.timestamp >= tableCutoff }
            if (tableReadings.isNotEmpty()) {
                drawTablePages(doc, pageNum, tableReadings.reversed())
            }
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        file
    }

    // ── Time-range selection ──────────────────────────────────────────────────

    private data class ChartRange(val heading: String, val readings: List<BPReading>)

    private fun buildChartRanges(all: List<BPReading>): List<ChartRange> {
        if (all.size < 2) return emptyList()
        val now = System.currentTimeMillis()
        val result  = mutableListOf<ChartRange>()
        var prevIds = emptySet<Long>()

        for ((days, label) in listOf(7 to "Last 7 days", 30 to "Last 30 days")) {
            val slice = all.filter { it.timestamp >= now - days * MS_PER_DAY }
            val ids   = slice.map { it.id }.toSet()
            if (slice.size >= 2 && ids != prevIds) {
                result += ChartRange("$label  ·  ${dateSpan(slice)}", slice)
                prevIds = ids
            }
        }
        val allIds = all.map { it.id }.toSet()
        if (all.size >= 2 && allIds != prevIds) {
            result += ChartRange("All readings  ·  ${dateSpan(all)}", all)
        }
        return result
    }

    private fun dateSpan(readings: List<BPReading>): String {
        val a = DATE_FMT.format(Date(readings.first().timestamp))
        val b = DATE_FMT.format(Date(readings.last().timestamp))
        return if (a == b) a else "$a – $b"
    }

    // ── Chart page ────────────────────────────────────────────────────────────

    private fun drawChartPage(doc: PdfDocument, pageNum: Int, range: ChartRange) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(LW, LH, pageNum).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)

        // --- header ---
        c.drawText("Blood Pressure Report", M, M + TS_TITLE, pTitle)
        c.drawText(range.heading,           M, M + TS_TITLE + TS_SUB + 10f, pSub)
        c.drawText("Exported: ${DATE_FMT.format(Date())}", LW - M, M + TS_TITLE, pRight)
        val divY = M + TS_TITLE + TS_SUB + 28f
        c.hRule(divY)

        // --- numbered callout assignments (notes OR tags; cap at MAX_CALLOUTS) ---
        val notedPairs = range.readings
            .mapIndexedNotNull { i, r ->
                if (!r.notes.isNullOrBlank() || r.tags.isNotEmpty()) i to r else null
            }
            .take(MAX_CALLOUTS)
        val noteNumAt  = notedPairs.mapIndexed { ni, (ri, _) -> ri to (ni + 1) }.toMap()

        // --- compute vertical zones (bottom-up) ---
        val statsTop    = LH - M - FOOTER_H - STATS_H
        val legendRows  = ceil(notedPairs.size / NOTE_COLS.toDouble()).toInt()
        val legendH     = if (notedPairs.isEmpty()) 0f
                          else legendRows * NOTE_ROW_H + 16f
        val legendTop   = statsTop - legendH
        val chartBottom = legendTop - X_LABEL_H

        drawChart(c, range.readings, noteNumAt,
            left = CHART_L, top = divY + 20f,
            right = CHART_R, bottom = chartBottom)
        drawStatsBar(c, range.readings, statsTop)
        if (notedPairs.isNotEmpty()) drawNoteLegend(c, notedPairs, legendTop)
        drawFooter(c, LW, LH)
        doc.finishPage(page)
    }

    // ── Chart drawing ─────────────────────────────────────────────────────────

    private fun drawChart(
        c: Canvas, readings: List<BPReading>, noteNums: Map<Int, Int>,
        left: Float, top: Float, right: Float, bottom: Float,
    ) {
        val n       = readings.size
        val firstTs = readings.first().timestamp
        val lastTs  = readings.last().timestamp

        // Y-axis bounds — same rule as the in-app Vico chart:
        //   < 24 h span  → fixed 40–180
        //   ≥ 24 h span  → pad ±5, snap outward to nearest 10
        val isMultiDay = (lastTs - firstTs) >= MS_PER_DAY
        val yMin: Double
        val yMax: Double
        if (isMultiDay) {
            val dataMin = readings.minOf { minOf(it.systolic, it.diastolic) }
            val dataMax = readings.maxOf { maxOf(it.systolic, it.diastolic) }
            yMin = floor((dataMin - 5).toDouble() / 10.0) * 10.0
            yMax = ceil ((dataMax + 5).toDouble() / 10.0) * 10.0
        } else {
            yMin = Y_FLOOR.toDouble()
            yMax = Y_CEIL.toDouble()
        }

        val dotR      = when { n > 100 -> 3f; n > 40 -> 5f; else -> 7f }
        // Grid step: ~6-8 lines regardless of range width
        val gridStep  = if ((yMax - yMin) <= 80) 10 else 20

        // grid + Y labels (left and right)
        var gv = yMin.toInt()
        while (gv <= yMax) {
            val gy = yC(gv.toDouble(), yMin, yMax, top, bottom)
            c.drawLine(left, gy, right, gy, pGrid)
            c.drawText(gv.toString(), left - 8f,  gy + 6f, pAxisLabel)
            c.drawText(gv.toString(), right + 8f, gy + 6f, pAxisLabelRight)
            gv += gridStep
        }

        // Reference lines — only drawn when the value falls within the Y range.
        fun refLine(yVal: Double, label: String) {
            if (yVal < yMin || yVal > yMax) return
            val ry = yC(yVal, yMin, yMax, top, bottom)
            c.drawLine(left, ry, right, ry, pRefLine)
            c.drawText(label, right + 62f, ry + 5f, pRefLabel)
        }
        refLine(120.0, "SYS 120")
        refLine(80.0,  "DIA 80")

        // X axis time labels — up to 10, evenly spaced in real time (not by index),
        // rotated 40° to avoid overlap.
        val xFmt       = xDateFmt(readings)
        val labelCount = 10.coerceAtMost(n)
        val denom      = (labelCount - 1).coerceAtLeast(1)
        val totalMs    = lastTs - firstTs
        for (li in 0 until labelCount) {
            val labelTs = firstTs + totalMs * li / denom
            val cx = xC(labelTs, firstTs, lastTs, left, right)
            c.save(); c.translate(cx, bottom + 16f); c.rotate(40f)
            c.drawText(xFmt.format(Date(labelTs)), 0f, 0f, pXLabel)
            c.restore()
        }

        // series paths — x position is time-proportional
        val sysPath = Path(); val diaPath = Path()
        readings.forEachIndexed { i, r ->
            val cx = xC(r.timestamp, firstTs, lastTs, left, right)
            val sy = yC(r.systolic.toDouble(),  yMin, yMax, top, bottom)
            val dy = yC(r.diastolic.toDouble(), yMin, yMax, top, bottom)
            if (i == 0) { sysPath.moveTo(cx, sy); diaPath.moveTo(cx, dy) }
            else        { sysPath.lineTo(cx, sy); diaPath.lineTo(cx, dy) }
        }
        c.drawPath(diaPath, pDiaLine)
        c.drawPath(sysPath, pSysLine)

        // dots + callout badges
        readings.forEachIndexed { i, r ->
            val cx = xC(r.timestamp, firstTs, lastTs, left, right)
            val sy = yC(r.systolic.toDouble(),  yMin, yMax, top, bottom)
            val dy = yC(r.diastolic.toDouble(), yMin, yMax, top, bottom)
            c.drawCircle(cx, sy, dotR, pSysDot)
            c.drawCircle(cx, dy, dotR, pDiaDot)
            noteNums[i]?.let { num ->
                badge(c, cx, min(sy, dy) - dotR - BADGE_R - 2f, num)
            }
        }

        // axes (on top of grid)
        c.drawLine(left, top, left, bottom, pAxis)
        c.drawLine(left, bottom, right, bottom, pAxis)
    }

    private fun badge(c: Canvas, cx: Float, cy: Float, number: Int) {
        c.drawCircle(cx, cy, BADGE_R, pBadgeFill)
        c.drawCircle(cx, cy, BADGE_R, pBadgeBorder)
        c.drawText(number.toString(), cx, cy + BADGE_R * 0.38f, pBadgeText)
    }

    // ── Stats bar ─────────────────────────────────────────────────────────────

    private fun drawStatsBar(c: Canvas, readings: List<BPReading>, top: Float) {
        val midY = top + STATS_H / 2f + 4f
        val sw   = 16f
        var x    = CHART_L

        fun swatch(p: Paint) {
            c.drawRect(x, midY - sw / 2f, x + sw, midY + sw / 2f, p)
            x += sw + 6f
        }
        fun label(t: String)  { c.drawText(t, x, midY + 5f, pStatsLabel); x += pStatsLabel.measureText(t) + 20f }
        fun value(t: String)  { c.drawText(t, x, midY + 5f, pStatsVal);   x += pStatsVal.measureText(t)   + 28f }
        fun vline()           { c.drawLine(x, top + 6f, x, top + STATS_H - 6f, pDivider); x += 20f }

        swatch(pSysDot); label("Systolic")
        swatch(pDiaDot); label("Diastolic")
        vline()
        val sys = readings.map { it.systolic }
        val dia = readings.map { it.diastolic }
        value("Avg  ${sys.average().roundToInt()} / ${dia.average().roundToInt()}")
        value("Min  ${sys.min()} / ${dia.min()}")
        value("Max  ${sys.max()} / ${dia.max()}")
        vline()
        val mapAvg = readings.map { meanArterialPressure(it.systolic, it.diastolic) }.average().roundToInt()
        val ppAvg  = readings.map { pulsePressure(it.systolic, it.diastolic) }.average().roundToInt()
        value("MAP  $mapAvg")
        value("PP  $ppAvg")
    }

    // ── Note legend ───────────────────────────────────────────────────────────

    private fun drawNoteLegend(c: Canvas, pairs: List<Pair<Int, BPReading>>, top: Float) {
        c.drawLine(CHART_L, top - 2f, CHART_R, top - 2f, pGrid)
        val colW = (CHART_R - CHART_L) / NOTE_COLS.toFloat()
        pairs.forEachIndexed { ni, (_, r) ->
            val col  = ni % NOTE_COLS
            val row  = ni / NOTE_COLS
            val colX = CHART_L + col * colW
            val rowY = top + NOTE_ROW_H * row + NOTE_ROW_H * 0.72f
            badge(c, colX + BADGE_R + 2f, rowY - BADGE_R, ni + 1)
            val tagPart = if (r.tags.isNotEmpty())
                "[" + r.tags.joinToString(", ") { tagLabel(it) } + "] " else ""
            val combined = tagPart + (r.notes?.takeIf { it.isNotBlank() } ?: "")
            val text = if (combined.length > 34) combined.take(31) + "…" else combined
            c.drawText(
                "${DATE_FMT.format(Date(r.timestamp))}  —  $text",
                colX + BADGE_R * 2 + 14f, rowY, pNoteLegend,
            )
        }
    }

    // ── Table pages ───────────────────────────────────────────────────────────

    private fun drawTablePages(doc: PdfDocument, firstPage: Int, readings: List<BPReading>) {
        // Portrait A4 @ 2×
        val w = PW; val h = PH
        // First row starts at: MARGIN + title + divider + column header row
        val firstRowY = M + TS_TITLE + 20f + TABLE_ROW_H * 2f
        val lastRowMaxY = h - M - FOOTER_H - TABLE_ROW_H
        val rowsPerPage = ((lastRowMaxY - firstRowY) / TABLE_ROW_H).toInt()

        readings.chunked(rowsPerPage).forEachIndexed { chunk, batch ->
            val pn    = firstPage + chunk
            val total = (readings.size + rowsPerPage - 1) / rowsPerPage
            val page  = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pn).create())
            val c     = page.canvas
            c.drawColor(Color.WHITE)

            val titleText = if (chunk == 0) "Blood Pressure Readings  ·  Last 30 Days"
                            else            "Blood Pressure Readings  ·  Last 30 Days (cont.)"
            val rightText = if (chunk == 0)
                "Exported: ${DATE_FMT.format(Date())}  ·  ${readings.size} readings"
            else
                "Page ${chunk + 1} of $total"
            c.drawText(titleText, M, M + TS_TITLE, pTitle)
            c.drawText(rightText, w - M, M + TS_TITLE, pRight)
            val divY = M + TS_TITLE + 20f
            c.drawLine(M, divY, w - M, divY, pDivider)

            // Column headers
            val hdrY = divY + TABLE_ROW_H
            tableRow(c, w, hdrY, isHeader = true, isAlt = false,
                dt = "Date / Time", sys = "SYS", dia = "DIA",
                pulse = "Pulse", cat = "Category", notes = "Notes",
                catColor = Color.rgb(20, 20, 20))
            c.drawLine(M, hdrY + 10f, w - M, hdrY + 10f, pDivider)

            batch.forEachIndexed { i, r ->
                val y = divY + TABLE_ROW_H * (i + 2)
                tableRow(c, w, y, isHeader = false, isAlt = i % 2 == 0,
                    dt    = TABLE_DATE_FMT.format(Date(r.timestamp)),
                    sys   = r.systolic.toString(),
                    dia   = r.diastolic.toString(),
                    pulse = r.pulse?.toString() ?: "—",
                    cat   = categoryLabel(r.category),
                    notes = notesCell(r),
                    catColor = categoryArgb(r.category))
            }
            drawFooter(c, w, h)
            doc.finishPage(page)
        }
    }

    private fun tableRow(
        c: Canvas, pageW: Int, y: Float,
        isHeader: Boolean, isAlt: Boolean,
        dt: String, sys: String, dia: String,
        pulse: String, cat: String, notes: String, catColor: Int,
    ) {
        if (isAlt && !isHeader) {
            c.drawRect(M, y - TABLE_ROW_H + 10f, pageW - M, y + 10f, pAltRow)
        }
        val p    = if (isHeader) pTblHeader else pTblRow
        val pCat = if (isHeader) pTblHeader else Paint(pTblRow).apply { color = catColor }
        c.drawText(dt,    COL_DATE.toFloat(),  y, p)
        c.drawText(sys,   COL_SYS.toFloat(),   y, p)
        c.drawText(dia,   COL_DIA.toFloat(),   y, p)
        c.drawText(pulse, COL_PULSE.toFloat(), y, p)
        c.drawText(cat,   COL_CAT.toFloat(),   y, pCat)
        c.drawText(notes, COL_NOTES.toFloat(), y, p)
    }

    // ── Shared footer ─────────────────────────────────────────────────────────

    private fun drawFooter(c: Canvas, w: Int, h: Int) {
        c.drawLine(M, h - M - FOOTER_H + 4f, w - M, h - M - FOOTER_H + 4f, pDivider)
        c.drawText(DISCLAIMER, M, h - M, pFooter)
    }

    // ── Math ──────────────────────────────────────────────────────────────────

    /** Data-space Y → canvas Y (higher values sit higher, i.e. lower canvas Y). */
    private fun yC(y: Double, yMin: Double, yMax: Double, top: Float, bottom: Float) =
        (bottom - (y - yMin) / (yMax - yMin) * (bottom - top)).toFloat()

    /** Timestamp → canvas X (time-proportional, matching the interactive chart). */
    private fun xC(ts: Long, firstTs: Long, lastTs: Long, left: Float, right: Float) =
        if (firstTs == lastTs) (left + right) / 2f
        else left + (ts - firstTs).toFloat() / (lastTs - firstTs).toFloat() * (right - left)

    private fun xDateFmt(readings: List<BPReading>) = SimpleDateFormat(
        when (readings.last().timestamp - readings.first().timestamp) {
            in 0..(14 * MS_PER_DAY)  -> "MMM d"
            in 0..(180 * MS_PER_DAY) -> "M/d"
            else                      -> "MM/yy"
        }, Locale.getDefault()
    )

    // ── Domain helpers ────────────────────────────────────────────────────────

    private fun categoryLabel(cat: BPCategory) = when (cat) {
        BPCategory.NORMAL              -> "Normal"
        BPCategory.ELEVATED            -> "Elevated"
        BPCategory.STAGE_1             -> "Stage 1"
        BPCategory.STAGE_2             -> "Stage 2"
        BPCategory.HYPERTENSIVE_CRISIS -> "Crisis"
    }

    private fun categoryArgb(cat: BPCategory) = when (cat) {
        BPCategory.NORMAL              -> Color.rgb(46, 125, 50)
        BPCategory.ELEVATED            -> Color.rgb(249, 168, 37)
        BPCategory.STAGE_1             -> Color.rgb(239, 108, 0)
        BPCategory.STAGE_2             -> Color.rgb(211, 47, 47)
        BPCategory.HYPERTENSIVE_CRISIS -> Color.rgb(183, 28, 28)
    }

    // English labels for the PDF (the report is English-only by design).
    private fun tagLabel(tag: BpTag) = when (tag) {
        BpTag.BEFORE_MEDICATION -> "before meds"
        BpTag.AFTER_MEDICATION  -> "after meds"
        BpTag.CAFFEINE          -> "caffeine"
        BpTag.ALCOHOL           -> "alcohol"
        BpTag.SALTY_MEAL        -> "salty meal"
        BpTag.EXERCISE          -> "exercise"
        BpTag.STRESS            -> "stress"
        BpTag.POOR_SLEEP        -> "poor sleep"
        BpTag.RESTING           -> "resting"
        BpTag.ON_WAKING         -> "on waking"
    }

    /** Notes table cell: prefixes "[tag, tag] " before any note, then truncates. */
    private fun notesCell(r: BPReading): String {
        val tagPart = if (r.tags.isNotEmpty())
            "[" + r.tags.joinToString(", ") { tagLabel(it) } + "] " else ""
        val full = tagPart + (r.notes ?: "")
        return if (full.length > 52) full.take(49) + "…" else full
    }

    // ── Canvas extension ──────────────────────────────────────────────────────

    private fun Canvas.hRule(y: Float) =
        drawLine(M, y, width - M, y, pDivider)

    // ── Paint factory helpers ─────────────────────────────────────────────────

    private fun tp(
        size: Float, color: Int,
        bold: Boolean = false, mono: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color    = color
        textSize      = size
        textAlign     = align
        typeface      = when { mono -> Typeface.MONOSPACE
                               bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                               else -> Typeface.DEFAULT }
    }

    private fun sp(color: Int, width: Float, effect: android.graphics.PathEffect? = null) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color  = color
            strokeWidth = width
            style       = Paint.Style.STROKE
            pathEffect  = effect
        }

    private fun fp(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style      = Paint.Style.FILL
    }

    // ── Paint instances ───────────────────────────────────────────────────────

    private val pTitle      = tp(TS_TITLE, Color.rgb(20, 20, 20), bold = true)
    private val pSub        = tp(TS_SUB,   Color.rgb(60, 60, 60))
    private val pRight      = tp(TS_SMALL, Color.rgb(100, 100, 100), align = Paint.Align.RIGHT)
    private val pAxisLabel      = tp(TS_SMALL, Color.rgb(100, 100, 100), align = Paint.Align.RIGHT)
    private val pAxisLabelRight = tp(TS_SMALL, Color.rgb(100, 100, 100), align = Paint.Align.LEFT)
    private val pXLabel         = tp(TS_SMALL, Color.rgb(100, 100, 100))
    private val pRefLabel   = tp(TS_SMALL - 2f, Color.rgb(140, 140, 140))
    private val pStatsLabel = tp(TS_SMALL, Color.rgb(60, 60, 60))
    private val pStatsVal   = tp(TS_SMALL, Color.rgb(20, 20, 20), mono = true)
    private val pNoteLegend = tp(TS_SMALL - 2f, Color.rgb(50, 50, 50))
    private val pFooter     = tp(TS_SMALL - 4f, Color.rgb(150, 150, 150))
    private val pTblHeader  = tp(TS_SMALL, Color.rgb(20, 20, 20), bold = true)
    private val pTblRow     = tp(TS_SMALL, Color.rgb(50, 50, 50))

    private val pDivider    = sp(Color.rgb(200, 200, 200), 2f)
    private val pGrid       = sp(Color.rgb(232, 232, 232), 1f)
    private val pAxis       = sp(Color.rgb(150, 150, 150), 2f)
    private val pRefLine    = sp(Color.rgb(160, 160, 160), 1.5f,
                                  DashPathEffect(floatArrayOf(14f, 7f), 0f))
    private val pSysLine    = sp(SYS_COLOR, LINE_W).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pDiaLine    = sp(DIA_COLOR, LINE_W).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    private val pSysDot        = fp(SYS_COLOR)
    private val pDiaDot        = fp(DIA_COLOR)
    private val pBadgeFill     = fp(Color.rgb(255, 230, 50))
    private val pBadgeBorder   = sp(Color.rgb(160, 120, 0), 1.5f)
    private val pBadgeText     = tp(TS_SMALL - 5f, Color.rgb(60, 40, 0),
                                     bold = true, align = Paint.Align.CENTER)
    private val pAltRow        = fp(Color.rgb(248, 249, 250))

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        // A4 landscape @ 2× (1684 × 1190)
        private const val LW = 1684
        private const val LH = 1190
        // A4 portrait @ 2× (1190 × 1684)
        private const val PW = 1190
        private const val PH = 1684

        private const val M  = 80f          // page margin
        private const val CHART_L = 148f    // M + Y-label space
        private const val CHART_R = 1524f   // LW - M - ref-label space

        private const val Y_FLOOR = 40
        private const val Y_CEIL  = 180

        private const val TS_TITLE = 36f
        private const val TS_SUB   = 24f
        private const val TS_SMALL = 22f

        private const val LINE_W      = 4f
        private const val BADGE_R     = 13f
        private const val STATS_H     = 52f
        private const val FOOTER_H    = 40f
        private const val X_LABEL_H   = 55f   // space for rotated X-axis labels
        private const val NOTE_ROW_H  = 36f
        private const val TABLE_ROW_H = 46f

        private const val MAX_CALLOUTS = 12   // cap numbered callouts per chart page
        private const val NOTE_COLS    = 3    // columns in the note legend

        private val SYS_COLOR = Color.rgb(211, 47, 47)
        private val DIA_COLOR = Color.rgb(25, 118, 210)

        // Table column X positions (portrait page)
        private const val COL_DATE  = M.toInt() + 4
        private const val COL_SYS   = COL_DATE + 290
        private const val COL_DIA   = COL_SYS  + 90
        private const val COL_PULSE = COL_DIA  + 90
        private const val COL_CAT   = COL_PULSE + 90
        private const val COL_NOTES = COL_CAT  + 160

        private const val DISCLAIMER =
            "ⓘ  Informational only — does not constitute medical advice. Consult a qualified healthcare professional."

        private val STAMP_FMT      = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        private val DATE_FMT       = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        private val TABLE_DATE_FMT = SimpleDateFormat("d MMM yyyy  HH:mm", Locale.getDefault())

        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
