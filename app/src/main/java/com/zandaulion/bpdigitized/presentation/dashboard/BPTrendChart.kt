package com.zandaulion.bpdigitized.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.presentation.extensions.labelRes
import com.zandaulion.bpdigitized.presentation.theme.CategoryCrisis
import com.zandaulion.bpdigitized.presentation.theme.CategoryElevated
import com.zandaulion.bpdigitized.presentation.theme.CategoryNormal
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage1
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage2
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.vicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.DashedShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** AHA normal-range upper limits shown as dotted reference lines. */
private const val NORMAL_SYSTOLIC  = 120.0
private const val NORMAL_DIASTOLIC = 80.0

private const val MILLIS_PER_DAY    = 24L * 60 * 60 * 1_000
private const val MAX_VISIBLE_SECS  = 10.0 * 24 * 3600   // cap: never open wider than 10 days

// Zone colour stops (bpValue → ARGB) for the severity gradient — green (optimal) up through
// crimson (extreme), using the same palette as the badges / scatter. Systolic and diastolic
// have their own thresholds.
private val SYS_ZONE_STOPS = listOf(
    110f to CategoryNormal.toArgb(),
    120f to CategoryElevated.toArgb(),
    130f to CategoryStage1.toArgb(),
    140f to CategoryStage2.toArgb(),
    180f to CategoryCrisis.toArgb(),
)
private val DIA_ZONE_STOPS = listOf(
    70f  to CategoryNormal.toArgb(),
    80f  to CategoryElevated.toArgb(),
    90f  to CategoryStage1.toArgb(),
    100f to CategoryStage2.toArgb(),
    120f to CategoryCrisis.toArgb(),
)

/**
 * Builds a vertical colour gradient (top = high BP / severe) for a line, mapping the given
 * (bpValue → ARGB) zone stops onto the chart's [yMin, yMax] range. Positions are clamped to
 * [0, 1] and kept strictly increasing, with the edge colours extended to the chart top/bottom.
 */
private fun severityGradient(stops: List<Pair<Float, Int>>, yMin: Float, yMax: Float): ShaderProvider {
    val mapped = stops
        .map { (v, c) -> ((yMax - v) / (yMax - yMin)).coerceIn(0f, 1f) to c }
        .sortedBy { it.first }
    val pos = ArrayList<Float>(); val col = ArrayList<Int>()
    for ((p, c) in mapped) {
        val pp = if (pos.isEmpty()) p else maxOf(p, pos.last() + 1e-3f)
        if (pp <= 1f) { pos.add(pp); col.add(c) }
    }
    if (pos.isEmpty()) { pos.add(0f); col.add(stops.last().second) }
    if (pos.first() > 0f) { pos.add(0, 0f); col.add(0, col.first()) }
    if (pos.last() < 1f) { pos.add(1f); col.add(col.last()) }
    return ShaderProvider.verticalGradient(col.toIntArray(), pos.toFloatArray())
}

@Composable
fun BPTrendChart(
    readings: List<BPReading>,
    modifier: Modifier = Modifier,
) {
    if (readings.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chart_no_readings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // True when the entire dataset falls within a single day — in that case labels
    // show only "HH:mm" since showing the date is redundant.
    // A single reading has no time span, so it counts as sub-day.
    val isSubDayDataset = remember(readings) {
        readings.size < 2 ||
        readings.last().timestamp - readings.first().timestamp < MILLIS_PER_DAY
    }

    // Y-axis bounds:
    //   < 24 h span (or single reading) → fixed 40–180, no clutter
    //   ≥ 24 h span → pad ±5 then snap outward to the nearest 10,
    //                 so the chart fills the viewport with the actual data range
    val (yMinY, yMaxY) = if (isSubDayDataset) {
        40.0 to 180.0
    } else {
        val dataMin = readings.minOf { minOf(it.systolic, it.diastolic) }
        val dataMax = readings.maxOf { maxOf(it.systolic, it.diastolic) }
        floor((dataMin - 5).toDouble() / 10.0) * 10.0 to
        ceil((dataMax + 5).toDouble() / 10.0) * 10.0
    }

    // Match reference-line colours to the data-series colours (with reduced alpha so they
    // stay visually subordinate to the actual readings).
    val lineColors = vicoTheme.lineCartesianLayerColors
    val sysRefColor = lineColors.getOrElse(0) { MaterialTheme.colorScheme.primary }
        .copy(alpha = 0.55f)
    val diaRefColor = lineColors.getOrElse(1) { MaterialTheme.colorScheme.secondary }
        .copy(alpha = 0.55f)

    val dashedShape = remember { DashedShape(dashLengthDp = 8f, gapLengthDp = 5f) }

    val sysLineComp = rememberLineComponent(
        fill = fill(sysRefColor),
        thickness = 1.5.dp,
        shape = dashedShape,
    )
    val diaLineComp = rememberLineComponent(
        fill = fill(diaRefColor),
        thickness = 1.5.dp,
        shape = dashedShape,
    )
    val sysLabelComp = rememberTextComponent(color = sysRefColor, textSize = 10.sp)
    val diaLabelComp = rememberTextComponent(color = diaRefColor, textSize = 10.sp)

    val sysRefLine = remember(sysLineComp, sysLabelComp) {
        HorizontalLine(
            y = { NORMAL_SYSTOLIC },
            line = sysLineComp,
            labelComponent = sysLabelComp,
            label = { "SYS ${NORMAL_SYSTOLIC.toInt()}" },
            horizontalLabelPosition = Position.Horizontal.Start,
            verticalLabelPosition = Position.Vertical.Top,
        )
    }
    val diaRefLine = remember(diaLineComp, diaLabelComp) {
        HorizontalLine(
            y = { NORMAL_DIASTOLIC },
            line = diaLineComp,
            labelComponent = diaLabelComp,
            label = { "DIA ${NORMAL_DIASTOLIC.toInt()}" },
            horizontalLabelPosition = Position.Horizontal.Start,
            verticalLabelPosition = Position.Vertical.Top,
        )
    }

    // Key on the first reading's timestamp: this changes whenever the user switches
    // range tabs (the oldest visible reading shifts), which resets the model producer,
    // scroll position, and zoom level. It does NOT change when a new reading is merely
    // appended to the end, so the user's current scroll/zoom is preserved on live updates.
    //
    // This also fixes the stale-bounds crash that occurred when going DOWN in range
    // (e.g. 90d→30d): Vico's layer kept bounds from the old dataset and crashed before
    // the new transaction landed. Keying on firstTs guarantees a full remount on every
    // range change, so Vico always starts fresh.
    key(readings.first().timestamp) {
        val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)

        // Default zoom = half the displayed span, capped at 10 days.
        //   7d  → min(3.5d, 10d) = 3.5 days visible
        //   30d → min(15d,  10d) = 10 days visible
        //   90d → min(45d,  10d) = 10 days visible
        //   All → min(half, 10d) = 10 days visible
        // Zoom.max ensures we never zoom past the content when few readings exist.
        val halfSpanSecs = if (readings.size >= 2)
            (readings.last().timestamp - readings.first().timestamp) / 2_000.0
        else 0.0
        val visibleSecs = halfSpanSecs.coerceAtMost(MAX_VISIBLE_SECS)
        val zoomState = rememberVicoZoomState(
            initialZoom = if (visibleSecs > 0.0)
                Zoom.max(Zoom.x(visibleSecs), Zoom.Content)
            else
                Zoom.Content,
        )

        // Capture zoom factor after Vico's first layout pass (starts at 0 until then).
        // visibleSecs * (capturedInitialZoom / currentZoom) gives the current number
        // of seconds visible, regardless of xStep/xSpacing internals.
        var capturedInitialZoom by remember { mutableStateOf(0f) }
        LaunchedEffect(zoomState) {
            capturedInitialZoom = snapshotFlow { zoomState.value }.first { it > 0f }
        }

        // isSubDayVisible: true when the current visible window is < 24 h.
        // derivedStateOf ensures the composable only recomposes when this boolean
        // changes (threshold crossing), not on every zoom frame.
        val isSubDayVisible by remember {
            derivedStateOf {
                when {
                    visibleSecs <= 0.0 || capturedInitialZoom <= 0f
                        || zoomState.value <= 0f -> isSubDayDataset
                    else ->
                        visibleSecs * (capturedInitialZoom / zoomState.value) < 86_400.0
                }
            }
        }

        // Label format:
        //   same-day dataset → "HH:mm"          (date is redundant)
        //   zoomed in < 24 h → "M/d HH:mm"      (date + time)
        //   default          → "M/d"             (date only)
        val bottomFormatter = remember(readings, isSubDayDataset, isSubDayVisible) {
            val firstTs = readings.first().timestamp
            val fmt = when {
                isSubDayDataset -> SimpleDateFormat("HH:mm", Locale.getDefault())
                isSubDayVisible -> SimpleDateFormat("M/d HH:mm", Locale.getDefault())
                else            -> SimpleDateFormat("M/d", Locale.getDefault())
            }
            CartesianValueFormatter { _, value, _ ->
                fmt.format(Date(firstTs + (value * 1_000).toLong()))
            }
        }

        // Tap marker: tapping a point shows that reading's values, tags and note.
        // The chart's x is "seconds since the first reading"; map the tapped x back
        // to the nearest reading to pull its tags/notes (which the line series alone
        // doesn't carry).
        val markerFirstTs = readings.first().timestamp
        // Resolve localized tag labels in composable scope (stringResource can't run
        // inside the marker's value formatter), indexed by enum ordinal.
        val tagLabels = ArrayList<String>(BpTag.entries.size)
        for (tag in BpTag.entries) tagLabels.add(stringResource(tag.labelRes()))

        val markerLabel = rememberTextComponent(
            color = MaterialTheme.colorScheme.onSurface,
            background = rememberShapeComponent(
                fill = fill(MaterialTheme.colorScheme.surfaceVariant),
            ),
            textSize = 11.sp,
        )
        val tapMarker = rememberDefaultCartesianMarker(
            label = markerLabel,
            valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
                val x = targets.firstOrNull()?.x ?: return@ValueFormatter ""
                val r = readings.minByOrNull {
                    abs((it.timestamp - markerFirstTs) / 1_000.0 - x)
                } ?: return@ValueFormatter ""
                buildString {
                    append("${r.systolic}/${r.diastolic}")
                    if (r.tags.isNotEmpty()) {
                        append("  ·  ")
                        append(r.tags.joinToString(", ") { tagLabels[it.ordinal] })
                    }
                    r.notes?.takeIf { it.isNotBlank() }?.let { note ->
                        append("  ·  ")
                        append(if (note.length > 40) note.take(37) + "…" else note)
                    }
                }
            },
        )

        val modelProducer = remember { CartesianChartModelProducer() }

        LaunchedEffect(readings) {
            // x = WHOLE seconds elapsed since the first reading, so points close in time
            // appear close on the chart. Integer seconds (Long division — no fractional
            // part) keep Vico's getXDeltaGcd precision check (≤ 4 decimal places) happy.
            // Fractional seconds from ms-precision timestamps lose precision at large
            // magnitudes: over the "All" range (~2 years → x ≈ 6.3e7) a double can no
            // longer represent x to 4 dp, so Vico throws "The x values are too precise".
            // Days (÷ 86_400_000) would fail immediately for the same reason.
            val firstTs = readings.first().timestamp
            val xs  = readings.map { ((it.timestamp - firstTs) / 1_000L).toDouble() }
            val sys = readings.map { it.systolic.toDouble() }
            val dia = readings.map { it.diastolic.toDouble() }
            modelProducer.runTransaction {
                lineSeries {
                    series(x = xs, y = sys)
                    series(x = xs, y = dia)
                }
            }
        }

        // Severity-coloured lines: a vertical gradient (green→amber→orange→red→crimson) keyed
        // to each line's zone thresholds, so the line shifts colour smoothly with the reading's
        // severity (its height on the BP axis). Rebuilt when the Y range changes.
        val sysLineFill = remember(yMinY, yMaxY) {
            LineCartesianLayer.LineFill.single(
                Fill(severityGradient(SYS_ZONE_STOPS, yMinY.toFloat(), yMaxY.toFloat())))
        }
        val diaLineFill = remember(yMinY, yMaxY) {
            LineCartesianLayer.LineFill.single(
                Fill(severityGradient(DIA_ZONE_STOPS, yMinY.toFloat(), yMaxY.toFloat())))
        }
        val sysLine = LineCartesianLayer.rememberLine(fill = sysLineFill, areaFill = null)
        val diaLine = LineCartesianLayer.rememberLine(fill = diaLineFill, areaFill = null)

        CartesianChartHost(
            modelProducer = modelProducer,
            modifier = modifier,
            animationSpec = null,
            scrollState = scrollState,
            zoomState = zoomState,
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(sysLine, diaLine),
                    rangeProvider = CartesianLayerRangeProvider.fixed(minY = yMinY, maxY = yMaxY),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
                decorations = listOf(sysRefLine, diaRefLine),
                marker = tapMarker,
            ),
        )
    }
}
