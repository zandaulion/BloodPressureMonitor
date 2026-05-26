package com.example.bpdigitizer.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.bpdigitizer.domain.model.BPReading
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Build date labels once per readings list change; captured by the formatter below.
    val dateLabels = remember(readings) {
        val fmt = SimpleDateFormat("M/d", Locale.getDefault())
        readings.map { fmt.format(Date(it.timestamp)) }
    }

    val bottomFormatter = remember(dateLabels) {
        CartesianValueFormatter { _, value, _ ->
            dateLabels.getOrElse(value.toInt()) { "" }
        }
    }

    CartesianChartHost(
        modelProducer = modelProducer,
        modifier = modifier,
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
    )
}
