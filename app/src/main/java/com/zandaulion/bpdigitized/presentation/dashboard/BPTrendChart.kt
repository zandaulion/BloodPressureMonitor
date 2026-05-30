package com.zandaulion.bpdigitized.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
import com.patrykandpatrick.vico.core.scroll.Scroll
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

    val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
    val zoomState   = rememberVicoZoomState()

    // Track the actual rendered chart width (pixels) so we can compute visibleFraction
    // without relying on the zoom factor (whose initial value is Zoom.Content, not 1.0).
    val chartWidthState = remember { mutableFloatStateOf(0f) }

    // Vico's maxScroll = totalContentWidth − chartWidth, so:
    //   visibleFraction = chartWidth / (chartWidth + maxScroll)
    // This is purely geometric and is correct regardless of the zoom starting point.
    // Both chartWidthState and scrollState fields are MutableFloatState, so derivedStateOf
    // re-evaluates automatically on every scroll or layout-width change.
    val visibleSpanMs by remember(readings) {
        derivedStateOf {
            val n = readings.size
            if (n < 2) return@derivedStateOf 0L

            val w         = chartWidthState.floatValue
            val maxScroll = scrollState.maxValue
            val total     = w + maxScroll

            val visibleFraction = if (total > 0f) w / total else 1f
            val visibleCount    = (n * visibleFraction).toInt().coerceIn(1, n)

            // Map scroll offset to a start-index into the readings list.
            val scrollFraction = if (maxScroll > 0f) scrollState.value / maxScroll else 0f
            val startIdx = ((n - visibleCount) * scrollFraction).toInt()
                .coerceIn(0, n - visibleCount)
            val endIdx = (startIdx + visibleCount - 1).coerceAtMost(n - 1)

            readings[endIdx].timestamp - readings[startIdx].timestamp
        }
    }

    // When the visible window is narrower than a day, include the hour in labels.
    val isSubDayView = visibleSpanMs < MILLIS_PER_DAY

    val bottomFormatter = remember(readings, isSubDayView) {
        val fmt = if (isSubDayView) {
            SimpleDateFormat("M/d HH:mm", Locale.getDefault())
        } else {
            SimpleDateFormat("M/d", Locale.getDefault())
        }
        CartesianValueFormatter { _, value, _ ->
            val ts = readings.getOrNull(value.toInt())?.timestamp
                ?: return@CartesianValueFormatter ""
            fmt.format(Date(ts))
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
            val xs  = readings.indices.map { it.toDouble() }
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
            // onSizeChanged captures the rendered pixel width so derivedStateOf can
            // compute the visible fraction correctly.
            modifier = modifier.onSizeChanged { chartWidthState.floatValue = it.width.toFloat() },
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
