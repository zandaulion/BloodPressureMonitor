package com.example.bpdigitizer.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bpdigitizer.domain.model.BPReading
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
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

    val dateLabels = remember(readings) {
        val fmt = SimpleDateFormat("M/d", Locale.getDefault())
        readings.map { fmt.format(Date(it.timestamp)) }
    }

    val bottomFormatter = remember(dateLabels) {
        CartesianValueFormatter { _, value, _ ->
            dateLabels.getOrElse(value.toInt()) { "" }
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

    CartesianChartHost(
        modelProducer = modelProducer,
        modifier = modifier,
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
