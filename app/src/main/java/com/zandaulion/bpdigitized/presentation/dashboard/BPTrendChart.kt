package com.zandaulion.bpdigitized.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1_000

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

    // Use the full dataset time span to decide the label format.
    // Previously this was computed from the scroll position on every frame, which caused
    // the whole composable to recompose on every scroll frame. At the right edge, floating-
    // point imprecision made scrollFraction briefly exceed 1.0, shifting the computed
    // startIdx/endIdx and making the axis labels flicker.
    // Basing the decision on the full span is stable during scrolling and correct for all
    // typical BP use cases (readings spread over days or weeks → M/d labels;
    // a handful of readings all on the same day → M/d HH:mm labels).
    val isSubDayView = remember(readings) {
        readings.size >= 2 &&
        readings.last().timestamp - readings.first().timestamp < MILLIS_PER_DAY
    }

    val bottomFormatter = remember(readings, isSubDayView) {
        // x values are "seconds elapsed since the first reading in this dataset".
        // Reconstruct the timestamp as firstTs + (value × 1000).
        // Using seconds (÷ 1000) keeps precision to ≤ 3 decimal places because
        // ms_diff is always an integer, so ms_diff / 1000.0 × 10_000 = ms_diff × 10
        // which is always a whole number — satisfying Vico's 4-decimal-place limit.
        val firstTs = readings.first().timestamp
        val fmt = if (isSubDayView) {
            SimpleDateFormat("HH:mm", Locale.getDefault())   // date redundant: all same day
        } else {
            SimpleDateFormat("M/d", Locale.getDefault())
        }
        CartesianValueFormatter { _, value, _ ->
            fmt.format(Date(firstTs + (value * 1_000).toLong()))
        }
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

        // Default zoom = half of the displayed time span, scrolled to the most recent
        // end. Examples: 7d range → ~3.5 days visible; 30d → ~15 days; 90d → ~45 days.
        // Zoom.max ensures we never zoom past the content when few readings exist.
        val halfSpanSecs = if (readings.size >= 2)
            (readings.last().timestamp - readings.first().timestamp) / 2_000.0
        else 0.0
        val zoomState = rememberVicoZoomState(
            initialZoom = if (halfSpanSecs > 0.0)
                Zoom.max(Zoom.x(halfSpanSecs), Zoom.Content)
            else
                Zoom.Content,
        )

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
