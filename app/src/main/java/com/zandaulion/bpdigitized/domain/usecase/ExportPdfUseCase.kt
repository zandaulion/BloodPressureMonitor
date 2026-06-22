package com.zandaulion.bpdigitized.domain.usecase

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.annotation.StringRes
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.model.meanArterialPressure
import com.zandaulion.bpdigitized.domain.model.pulsePressure
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import com.zandaulion.bpdigitized.domain.repository.ChartSettingsRepository
import com.zandaulion.bpdigitized.presentation.extensions.badgeLabelRes
import com.zandaulion.bpdigitized.presentation.extensions.labelRes
import com.zandaulion.bpdigitized.presentation.locale.LocaleHelper
import com.zandaulion.bpdigitized.presentation.locale.LocalePrefs
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
 *  - One landscape page per active time range (7, 30, 90 days),
 *    with a line chart, numbered tag badges, a stats bar, and a tag legend.
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
        // Render the report in the user's chosen app language (blank = system locale).
        val code = LocalePrefs.getLocale(context)
        val t = L10n(
            ctx    = LocaleHelper.applyLocale(context, code),
            locale = if (code.isBlank()) Locale.getDefault() else Locale(code),
        )
        val stamp = STAMP_FMT.format(Date())
        val file  = File(context.cacheDir, "bp_export_$stamp.pdf")
        val doc   = PdfDocument()
        try {
            var pageNum = 1
            // Lead with an at-a-glance overview (zone mix + Sys/Dia scatter) over
            // the last 90 days, when there's enough data for it to be meaningful.
            val overviewCutoff   = System.currentTimeMillis() - 90L * MS_PER_DAY
            val overviewReadings = all.filter { it.timestamp >= overviewCutoff }
            if (overviewReadings.size >= OVERVIEW_MIN) {
                drawOverviewPage(doc, pageNum++, overviewReadings, t)
            }
            buildChartRanges(all, t).forEach { range ->
                // Smooth the chart's readings (averaging back-to-back sittings); merged
                // notes are preserved so numbered callouts/labels still appear. The table
                // pages below stay raw.
                val drawn = if (smooth)
                    range.copy(readings = aggregateReadings(range.readings))
                else range
                drawChartPage(doc, pageNum++, drawn, t)
            }
            // Table pages show only the last 30 days (newest-first)
            val tableCutoff = System.currentTimeMillis() - 30L * MS_PER_DAY
            val tableReadings = all.filter { it.timestamp >= tableCutoff }
            if (tableReadings.isNotEmpty()) {
                drawTablePages(doc, pageNum, tableReadings.reversed(), t)
            }
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        file
    }

    // ── Localized text ────────────────────────────────────────────────────────

    /**
     * All localized PDF text plus locale-aware date formatters, resolved once per
     * export from the user's saved language. Threading a single instance through the
     * draw methods keeps this singleton use-case free of mutable per-export state.
     */
    private class L10n(private val ctx: Context, val locale: Locale) {
        private fun s(@StringRes id: Int): String = ctx.getString(id)

        val reportTitle    = s(R.string.pdf_report_title)
        val statSystolic   = s(R.string.pdf_stat_systolic)
        val statDiastolic  = s(R.string.pdf_stat_diastolic)
        val statAvg        = s(R.string.pdf_stat_avg)
        val statMin        = s(R.string.pdf_stat_min)
        val statMax        = s(R.string.pdf_stat_max)
        val statMap        = s(R.string.pdf_stat_map)
        val statPp         = s(R.string.pdf_stat_pp)
        val colDateTime    = s(R.string.pdf_col_datetime)
        val colSys         = s(R.string.pdf_col_sys)
        val colDia         = s(R.string.pdf_col_dia)
        val colPulse       = s(R.string.pdf_col_pulse)
        val colCategory    = s(R.string.pdf_col_category)
        val colNotes       = s(R.string.pdf_col_notes)
        val disclaimer     = s(R.string.pdf_disclaimer)
        val tableTitle     = s(R.string.pdf_table_title)
        val tableTitleCont = s(R.string.pdf_table_title_cont)
        val overviewSubtitle = s(R.string.pdf_overview_subtitle)
        val zoneDistribution = s(R.string.pdf_zone_distribution)
        val sysVsDia         = s(R.string.pdf_sys_vs_dia)

        val dateFmt      = SimpleDateFormat("d MMM yyyy", locale)
        val tableDateFmt = SimpleDateFormat("d MMM yyyy  HH:mm", locale)

        fun rangeHeading(days: Int): String = ctx.getString(
            when (days) {
                7  -> R.string.pdf_range_7d
                30 -> R.string.pdf_range_30d
                else -> R.string.pdf_range_90d
            }
        )
        fun exported(date: String)    = ctx.getString(R.string.pdf_exported, date)
        fun refSys(v: Int)            = ctx.getString(R.string.pdf_ref_sys, v)
        fun refDia(v: Int)            = ctx.getString(R.string.pdf_ref_dia, v)
        fun readingsCount(n: Int)     = ctx.getString(R.string.pdf_readings_count, n)
        fun pageXofY(x: Int, y: Int)  = ctx.getString(R.string.pdf_page_x_of_y, x, y)
        fun category(cat: BPCategory) = ctx.getString(cat.badgeLabelRes())
        fun tag(tag: BpTag)           = ctx.getString(tag.labelRes())
    }

    // ── Time-range selection ──────────────────────────────────────────────────

    private data class ChartRange(val heading: String, val readings: List<BPReading>)

    private fun buildChartRanges(all: List<BPReading>, t: L10n): List<ChartRange> {
        if (all.size < 2) return emptyList()
        val now = System.currentTimeMillis()
        val result  = mutableListOf<ChartRange>()
        var prevIds = emptySet<Long>()

        for (days in listOf(7, 30, 90)) {
            val slice = all.filter { it.timestamp >= now - days * MS_PER_DAY }
            val ids   = slice.map { it.id }.toSet()
            if (slice.size >= 2 && ids != prevIds) {
                result += ChartRange("${t.rangeHeading(days)}  ·  ${dateSpan(slice, t)}", slice)
                prevIds = ids
            }
        }
        return result
    }

    private fun dateSpan(readings: List<BPReading>, t: L10n): String {
        val a = t.dateFmt.format(Date(readings.first().timestamp))
        val b = t.dateFmt.format(Date(readings.last().timestamp))
        return if (a == b) a else "$a – $b"
    }

    // ── Chart page ────────────────────────────────────────────────────────────

    private fun drawChartPage(doc: PdfDocument, pageNum: Int, range: ChartRange, t: L10n) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(LW, LH, pageNum).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)

        // --- header ---
        c.drawText(t.reportTitle, M, M + TS_TITLE, pTitle)
        c.drawText(range.heading, M, M + TS_TITLE + TS_SUB + 10f, pSub)
        c.drawText(t.exported(t.dateFmt.format(Date())), LW - M, M + TS_TITLE, pRight)
        val divY = M + TS_TITLE + TS_SUB + 28f
        c.hRule(divY)

        // --- tag numbering: each distinct tag in this range gets a number (enum
        //     order); every tagged reading shows its tags' numbers concatenated,
        //     e.g. a reading tagged #2, #4 and #7 → badge "247". ---
        val tagsPresent = BpTag.entries.filter { tag -> range.readings.any { tag in it.tags } }
        val tagNumber   = tagsPresent.withIndex().associate { (i, tag) -> tag to (i + 1) }
        val badgeAt: Map<Int, String> = range.readings
            .mapIndexedNotNull { i, r ->
                if (r.tags.isEmpty()) null
                else i to r.tags.mapNotNull { tagNumber[it] }.sorted().joinToString("")
            }
            .toMap()

        // --- compute vertical zones (bottom-up) ---
        val statsTop    = LH - M - FOOTER_H - STATS_H
        val legendRows  = ceil(tagsPresent.size / NOTE_COLS.toDouble()).toInt()
        val legendH     = if (tagsPresent.isEmpty()) 0f
                          else legendRows * NOTE_ROW_H + 16f
        val legendTop   = statsTop - legendH
        val chartBottom = legendTop - X_LABEL_H

        drawChart(c, range.readings, badgeAt,
            left = CHART_L, top = divY + 20f,
            right = CHART_R, bottom = chartBottom, t = t)
        drawStatsBar(c, range.readings, statsTop, t)
        if (tagsPresent.isNotEmpty()) drawTagLegend(c, tagsPresent, legendTop, t)
        drawFooter(c, LW, LH, t)
        doc.finishPage(page)
    }

    // ── Overview page ─────────────────────────────────────────────────────────

    /** At-a-glance summary: zone-distribution donut + Systolic-vs-Diastolic scatter. */
    private fun drawOverviewPage(doc: PdfDocument, pageNum: Int, readings: List<BPReading>, t: L10n) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(LW, LH, pageNum).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)

        // header (matches the chart pages)
        c.drawText(t.reportTitle, M, M + TS_TITLE, pTitle)
        c.drawText("${t.overviewSubtitle}  ·  ${dateSpan(readings, t)}",
            M, M + TS_TITLE + TS_SUB + 10f, pSub)
        c.drawText(t.exported(t.dateFmt.format(Date())), LW - M, M + TS_TITLE, pRight)
        val divY = M + TS_TITLE + TS_SUB + 28f
        c.hRule(divY)

        val contentTop    = divY + 46f
        val contentBottom = LH - M - FOOTER_H - 24f
        val midY          = (contentTop + contentBottom) / 2f

        // left: zone-distribution donut + legend
        c.drawText(t.zoneDistribution, CHART_L, contentTop, pSection)
        drawZoneDonut(c, readings, cx = CHART_L + 232f, cy = midY + 24f, t = t)

        // right: systolic-vs-diastolic scatter
        val scatterTitleX = 900f
        c.drawText(t.sysVsDia, scatterTitleX, contentTop, pSection)
        drawScatter(
            c, readings,
            left = scatterTitleX + 86f, top = contentTop + 44f,
            right = CHART_R, bottom = contentBottom - 56f, t = t,
        )

        drawFooter(c, LW, LH, t)
        doc.finishPage(page)
    }

    /** Donut of the share of readings in each BP zone, with a count/percent legend. */
    private fun drawZoneDonut(c: Canvas, readings: List<BPReading>, cx: Float, cy: Float, t: L10n) {
        val total  = readings.size
        val counts = BPCategory.entries.associateWith { cat -> readings.count { it.category == cat } }

        val outerR    = 150f
        val thickness = 56f
        val ringR     = outerR - thickness / 2f
        val oval      = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        pRing.strokeWidth = thickness

        var start = -90f
        for (cat in BPCategory.entries) {
            val cnt = counts[cat] ?: 0
            if (cnt == 0) continue
            val sweep = 360f * cnt / total
            pRing.color = categoryArgb(cat)
            c.drawArc(oval, start, sweep, false, pRing)
            start += sweep
        }
        // centre total + caption
        c.drawText(total.toString(), cx, cy + 15f, pDonutCenter)
        c.drawText(t.readingsCount(total), cx, cy + outerR + 40f, pDonutCaption)

        // legend — only the zones actually present
        var ly = cy - ringR + 8f
        val lx = cx + outerR + 34f
        val sw = 20f
        for (cat in BPCategory.entries) {
            val cnt = counts[cat] ?: 0
            if (cnt == 0) continue
            val pct = (100.0 * cnt / total).roundToInt()
            pSwatch.color = categoryArgb(cat)
            c.drawRect(lx, ly - sw + 3f, lx + sw, ly + 3f, pSwatch)
            c.drawText("${t.category(cat)}    $cnt  ($pct%)", lx + sw + 12f, ly, pLegendText)
            ly += 48f
        }
    }

    /** Scatter of each reading: diastolic (X) vs systolic (Y), coloured by zone. */
    private fun drawScatter(
        c: Canvas, readings: List<BPReading>,
        left: Float, top: Float, right: Float, bottom: Float, t: L10n,
    ) {
        val sysMin = floor((readings.minOf { it.systolic } - 5).toDouble() / 10.0) * 10.0
        val sysMax = ceil ((readings.maxOf { it.systolic } + 5).toDouble() / 10.0) * 10.0
        val diaMin = floor((readings.minOf { it.diastolic } - 5).toDouble() / 10.0) * 10.0
        val diaMax = ceil ((readings.maxOf { it.diastolic } + 5).toDouble() / 10.0) * 10.0

        fun mx(d: Double) = (left + (d - diaMin) / (diaMax - diaMin) * (right - left)).toFloat()
        fun my(s: Double) = (bottom - (s - sysMin) / (sysMax - sysMin) * (bottom - top)).toFloat()

        // grid + Y labels (systolic)
        val yStep = if ((sysMax - sysMin) <= 80) 10 else 20
        var gy = sysMin.toInt()
        while (gy <= sysMax) {
            val y = my(gy.toDouble())
            c.drawLine(left, y, right, y, pGrid)
            c.drawText(gy.toString(), left - 8f, y + 6f, pAxisLabel)
            gy += yStep
        }
        // grid + X labels (diastolic)
        val xStep = if ((diaMax - diaMin) <= 80) 10 else 20
        var gx = diaMin.toInt()
        while (gx <= diaMax) {
            val x = mx(gx.toDouble())
            c.drawLine(x, top, x, bottom, pGrid)
            c.drawText(gx.toString(), x, bottom + 28f, pXLabelCenter)
            gx += xStep
        }
        // reference lines at 120 / 80 when within range
        if (sysMin <= 120.0 && 120.0 <= sysMax) { val y = my(120.0); c.drawLine(left, y, right, y, pRefLine) }
        if (diaMin <= 80.0  && 80.0  <= diaMax) { val x = mx(80.0);  c.drawLine(x, top, x, bottom, pRefLine) }

        // points, coloured by zone
        for (r in readings) {
            pScatterDot.color = categoryArgb(r.category)
            c.drawCircle(mx(r.diastolic.toDouble()), my(r.systolic.toDouble()), 6f, pScatterDot)
        }

        // axes + titles
        c.drawLine(left, top, left, bottom, pAxis)
        c.drawLine(left, bottom, right, bottom, pAxis)
        c.drawText(t.statDiastolic, (left + right) / 2f, bottom + 64f, pAxisTitle)
        c.save(); c.translate(left - 58f, (top + bottom) / 2f); c.rotate(-90f)
        c.drawText(t.statSystolic, 0f, 0f, pAxisTitle); c.restore()
    }

    // ── Chart drawing ─────────────────────────────────────────────────────────

    private fun drawChart(
        c: Canvas, readings: List<BPReading>, badgeAt: Map<Int, String>,
        left: Float, top: Float, right: Float, bottom: Float, t: L10n,
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
        refLine(120.0, t.refSys(120))
        refLine(80.0,  t.refDia(80))

        // X axis time labels — up to 10, evenly spaced in real time (not by index),
        // rotated 40° to avoid overlap.
        val xFmt       = xDateFmt(readings, t.locale)
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
            badgeAt[i]?.let { label ->
                badge(c, cx, min(sy, dy) - dotR - BADGE_R - 2f, label)
            }
        }

        // axes (on top of grid)
        c.drawLine(left, top, left, bottom, pAxis)
        c.drawLine(left, bottom, right, bottom, pAxis)
    }

    /** Yellow pill badge containing [text]; widens for multi-digit labels. */
    private fun badge(c: Canvas, cx: Float, cy: Float, text: String) {
        val halfW = maxOf(BADGE_R, pBadgeText.measureText(text) / 2f + 6f)
        val rect  = RectF(cx - halfW, cy - BADGE_R, cx + halfW, cy + BADGE_R)
        c.drawRoundRect(rect, BADGE_R, BADGE_R, pBadgeFill)
        c.drawRoundRect(rect, BADGE_R, BADGE_R, pBadgeBorder)
        c.drawText(text, cx, cy + BADGE_R * 0.38f, pBadgeText)
    }

    // ── Stats bar ─────────────────────────────────────────────────────────────

    private fun drawStatsBar(c: Canvas, readings: List<BPReading>, top: Float, t: L10n) {
        val midY = top + STATS_H / 2f + 4f
        val sw   = 16f
        var x    = CHART_L

        fun swatch(p: Paint) {
            c.drawRect(x, midY - sw / 2f, x + sw, midY + sw / 2f, p)
            x += sw + 6f
        }
        fun label(s: String)  { c.drawText(s, x, midY + 5f, pStatsLabel); x += pStatsLabel.measureText(s) + 20f }
        fun value(s: String)  { c.drawText(s, x, midY + 5f, pStatsVal);   x += pStatsVal.measureText(s)   + 28f }
        fun vline()           { c.drawLine(x, top + 6f, x, top + STATS_H - 6f, pDivider); x += 20f }

        swatch(pSysDot); label(t.statSystolic)
        swatch(pDiaDot); label(t.statDiastolic)
        vline()
        val sys = readings.map { it.systolic }
        val dia = readings.map { it.diastolic }
        value("${t.statAvg}  ${sys.average().roundToInt()} / ${dia.average().roundToInt()}")
        value("${t.statMin}  ${sys.min()} / ${dia.min()}")
        value("${t.statMax}  ${sys.max()} / ${dia.max()}")
        vline()
        val mapAvg = readings.map { meanArterialPressure(it.systolic, it.diastolic) }.average().roundToInt()
        val ppAvg  = readings.map { pulsePressure(it.systolic, it.diastolic) }.average().roundToInt()
        value("${t.statMap}  $mapAvg")
        value("${t.statPp}  $ppAvg")
    }

    // ── Tag legend ────────────────────────────────────────────────────────────

    /** Maps each badge number back to its tag label (≤ 10 rows, in NOTE_COLS columns). */
    private fun drawTagLegend(c: Canvas, tags: List<BpTag>, top: Float, t: L10n) {
        c.drawLine(CHART_L, top - 2f, CHART_R, top - 2f, pGrid)
        val colW = (CHART_R - CHART_L) / NOTE_COLS.toFloat()
        tags.forEachIndexed { idx, tag ->
            val col  = idx % NOTE_COLS
            val row  = idx / NOTE_COLS
            val colX = CHART_L + col * colW
            val rowY = top + NOTE_ROW_H * row + NOTE_ROW_H * 0.72f
            badge(c, colX + BADGE_R + 2f, rowY - BADGE_R, (idx + 1).toString())
            c.drawText(
                "${idx + 1} = ${t.tag(tag)}",
                colX + BADGE_R * 2 + 14f, rowY, pNoteLegend,
            )
        }
    }

    // ── Table pages ───────────────────────────────────────────────────────────

    private fun drawTablePages(doc: PdfDocument, firstPage: Int, readings: List<BPReading>, t: L10n) {
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

            val titleText = if (chunk == 0) t.tableTitle else t.tableTitleCont
            val rightText = if (chunk == 0)
                "${t.exported(t.dateFmt.format(Date()))}  ·  ${t.readingsCount(readings.size)}"
            else
                t.pageXofY(chunk + 1, total)
            c.drawText(titleText, M, M + TS_TITLE, pTitle)
            c.drawText(rightText, w - M, M + TS_TITLE, pRight)
            val divY = M + TS_TITLE + 20f
            c.drawLine(M, divY, w - M, divY, pDivider)

            // Column headers
            val hdrY = divY + TABLE_ROW_H
            tableRow(c, w, hdrY, isHeader = true, isAlt = false,
                dt = t.colDateTime, sys = t.colSys, dia = t.colDia,
                pulse = t.colPulse, cat = t.colCategory, notes = t.colNotes,
                catColor = Color.rgb(20, 20, 20))
            c.drawLine(M, hdrY + 10f, w - M, hdrY + 10f, pDivider)

            batch.forEachIndexed { i, r ->
                val y = divY + TABLE_ROW_H * (i + 2)
                tableRow(c, w, y, isHeader = false, isAlt = i % 2 == 0,
                    dt    = t.tableDateFmt.format(Date(r.timestamp)),
                    sys   = r.systolic.toString(),
                    dia   = r.diastolic.toString(),
                    pulse = r.pulse?.toString() ?: "—",
                    cat   = t.category(r.category),
                    notes = notesCell(r, t),
                    catColor = categoryArgb(r.category))
            }
            drawFooter(c, w, h, t)
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

    private fun drawFooter(c: Canvas, w: Int, h: Int, t: L10n) {
        c.drawLine(M, h - M - FOOTER_H + 4f, w - M, h - M - FOOTER_H + 4f, pDivider)
        c.drawText(t.disclaimer, M, h - M, pFooter)
        // Unintrusive link to the app's web page, right-aligned just above the disclaimer.
        c.drawText(SITE_URL, w - M, h - M - 20f, pFooterRight)
    }

    // ── Math ──────────────────────────────────────────────────────────────────

    /** Data-space Y → canvas Y (higher values sit higher, i.e. lower canvas Y). */
    private fun yC(y: Double, yMin: Double, yMax: Double, top: Float, bottom: Float) =
        (bottom - (y - yMin) / (yMax - yMin) * (bottom - top)).toFloat()

    /** Timestamp → canvas X (time-proportional, matching the interactive chart). */
    private fun xC(ts: Long, firstTs: Long, lastTs: Long, left: Float, right: Float) =
        if (firstTs == lastTs) (left + right) / 2f
        else left + (ts - firstTs).toFloat() / (lastTs - firstTs).toFloat() * (right - left)

    private fun xDateFmt(readings: List<BPReading>, locale: Locale) = SimpleDateFormat(
        when (readings.last().timestamp - readings.first().timestamp) {
            in 0..(14 * MS_PER_DAY)  -> "MMM d"
            in 0..(180 * MS_PER_DAY) -> "M/d"
            else                      -> "MM/yy"
        }, locale
    )

    // ── Domain helpers ────────────────────────────────────────────────────────

    private fun categoryArgb(cat: BPCategory) = when (cat) {
        BPCategory.NORMAL              -> Color.rgb(46, 125, 50)
        BPCategory.ELEVATED            -> Color.rgb(249, 168, 37)
        BPCategory.STAGE_1             -> Color.rgb(239, 108, 0)
        BPCategory.STAGE_2             -> Color.rgb(211, 47, 47)
        BPCategory.HYPERTENSIVE_CRISIS -> Color.rgb(183, 28, 28)
    }

    /** Notes table cell: prefixes "[tag, tag] " before any note, then truncates. */
    private fun notesCell(r: BPReading, t: L10n): String {
        val tagPart = if (r.tags.isNotEmpty())
            "[" + r.tags.joinToString(", ") { t.tag(it) } + "] " else ""
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
    private val pFooterRight = tp(TS_SMALL - 4f, Color.rgb(150, 150, 150), align = Paint.Align.RIGHT)
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

    // overview page
    private val pSection      = tp(26f, Color.rgb(20, 20, 20), bold = true)
    private val pDonutCenter  = tp(46f, Color.rgb(20, 20, 20), bold = true, align = Paint.Align.CENTER)
    private val pDonutCaption = tp(TS_SMALL - 2f, Color.rgb(120, 120, 120), align = Paint.Align.CENTER)
    private val pLegendText   = tp(TS_SMALL, Color.rgb(40, 40, 40))
    private val pXLabelCenter = tp(TS_SMALL, Color.rgb(100, 100, 100), align = Paint.Align.CENTER)
    private val pAxisTitle    = tp(TS_SMALL, Color.rgb(80, 80, 80), align = Paint.Align.CENTER)
    private val pRing         = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT
    }
    private val pSwatch       = fp(Color.BLACK)
    private val pScatterDot   = fp(Color.BLACK)

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
        private const val SITE_URL    = "zandaulion.com/bpdigitizer.html"
        private const val X_LABEL_H   = 55f   // space for rotated X-axis labels
        private const val NOTE_ROW_H  = 36f
        private const val TABLE_ROW_H = 46f

        private const val NOTE_COLS    = 3    // columns in the tag legend

        private val SYS_COLOR = Color.rgb(211, 47, 47)
        private val DIA_COLOR = Color.rgb(25, 118, 210)

        // Table column X positions (portrait page)
        private const val COL_DATE  = M.toInt() + 4
        private const val COL_SYS   = COL_DATE + 290
        private const val COL_DIA   = COL_SYS  + 90
        private const val COL_PULSE = COL_DIA  + 90
        private const val COL_CAT   = COL_PULSE + 90
        private const val COL_NOTES = COL_CAT  + 160

        private val STAMP_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        private const val MS_PER_DAY = 24L * 60 * 60 * 1000

        private const val OVERVIEW_MIN = 5   // min readings (last 90 days) to render the overview page
    }
}
