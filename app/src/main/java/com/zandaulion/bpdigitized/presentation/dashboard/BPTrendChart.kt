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

/** Default number of x-units (seconds) shown on first render — approximately 3 days. */
private const val DEFAULT_VISIBLE_SECS = 3.0 * 24 * 3600   // 259 200 s

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

    val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
    // Zoom.x(n) ensures exactly n x-units (seconds) are visible.
    // Zoom.max picks the larger zoom factor of the two, which means:
    //   • dataset > 3 days → Zoom.x wins → chart opens showing the last 3 days
    //   • dataset ≤ 3 days → Zoom.Content wins → all readings are visible
    val zoomState = rememberVicoZoomState(
        initialZoom = Zoom.max(Zoom.x(DEFAULT_VISIBLE_SECS), Zoom.Content),
    )

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

    // key(readings.size) resets all Vico internal state whenever the dataset SIZE changes.
    // This prevents the stale-bounds crash that occurs when going DOWN in range (e.g. 90d→30d):
    // Vico's layer keeps bounds from the old (larger) dataset and crashes indexing the new
    // shorter one before the next transaction lands.
    //
    // Within a given size, modelProducer is stable (plain remember {}), so content-only
    // changes (same count, different values) just trigger LaunchedEffect(readings) and run
    // a new transaction on the existing producer — no empty-producer flash.
    key(readings.size) {
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
