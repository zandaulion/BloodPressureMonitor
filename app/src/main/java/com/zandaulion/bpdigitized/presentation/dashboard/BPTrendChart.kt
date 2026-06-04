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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
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
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.shape.DashedShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** AHA normal-range upper limits shown as dotted reference lines. */
private const val NORMAL_SYSTOLIC  = 120.0
private const val NORMAL_DIASTOLIC = 80.0

private const val MILLIS_PER_DAY    = 24L * 60 * 60 * 1_000
private const val MAX_VISIBLE_SECS  = 10.0 * 24 * 3600   // cap: never open wider than 10 days

@Composable
fun BPTrendChart(
    readings: List<BPReading>,
    modifier: Modifier = Modifier,
) {
    if (readings.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No readings in this range",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // True when the entire dataset falls within a single day — in that case labels
    // show only "HH:mm" since showing the date is redundant.
    val isSubDayDataset = remember(readings) {
        readings.size >= 2 &&
        readings.last().timestamp - readings.first().timestamp < MILLIS_PER_DAY
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

        val modelProducer = remember { CartesianChartModelProducer() }

        LaunchedEffect(readings) {
            // x = seconds elapsed since the first reading, so points close in time
            // appear close on the chart. Seconds (÷ 1000) satisfies Vico's constraint
            // of ≤ 4 decimal places; days (÷ 86_400_000) would produce values like
            // 0.00001157… which triggers IllegalArgumentException in getXDeltaGcd.
            val firstTs = readings.first().timestamp
            val xs  = readings.map { (it.timestamp - firstTs).toDouble() / 1_000.0 }
            val sys = readings.map { it.systolic.toDouble() }
            val dia = readings.map { it.diastolic.toDouble() }
            modelProducer.runTransaction {
                lineSeries {
                    series(x = xs, y = sys)
                    series(x = xs, y = dia)
                }
            }
        }

        CartesianChartHost(
            modelProducer = modelProducer,
            modifier = modifier,
            animationSpec = null,
            scrollState = scrollState,
            zoomState = zoomState,
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    rangeProvider = CartesianLayerRangeProvider.fixed(minY = 40.0, maxY = 180.0),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
                decorations = listOf(sysRefLine, diaRefLine),
            ),
        )
    }
}
