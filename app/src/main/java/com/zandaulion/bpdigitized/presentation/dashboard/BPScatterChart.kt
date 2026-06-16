package com.zandaulion.bpdigitized.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.presentation.extensions.badgeLabelRes
import com.zandaulion.bpdigitized.presentation.theme.CategoryCrisis
import com.zandaulion.bpdigitized.presentation.theme.CategoryElevated
import com.zandaulion.bpdigitized.presentation.theme.CategoryNormal
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage1
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/** AHA normal-range upper limits, shown as reference lines (matches the trend chart). */
private const val REF_SYSTOLIC  = 120.0
private const val REF_DIASTOLIC = 80.0

/** Zone fill for a category — the same vivid palette used by the badges and the PDF. */
private fun zoneColor(category: BPCategory): Color = when (category) {
    BPCategory.NORMAL              -> CategoryNormal
    BPCategory.ELEVATED            -> CategoryElevated
    BPCategory.STAGE_1             -> CategoryStage1
    BPCategory.STAGE_2             -> CategoryStage2
    BPCategory.HYPERTENSIVE_CRISIS -> CategoryCrisis
}

/**
 * Scatter of each reading: diastolic (X) vs systolic (Y), coloured by zone, with
 * dashed 120/80 reference lines. Mirrors the PDF overview scatter. Tapping a dot
 * reveals that reading's values and date, mirroring the trend chart's tap marker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BPScatterChart(
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

    // Axis bounds — pad ±5 then snap outward to the nearest 10 (same rule as the PDF).
    // The ±5 pad also guarantees a non-zero span when every reading shares a value.
    val sysMin = floor((readings.minOf { it.systolic } - 5).toDouble() / 10.0) * 10.0
    val sysMax = ceil ((readings.maxOf { it.systolic } + 5).toDouble() / 10.0) * 10.0
    val diaMin = floor((readings.minOf { it.diastolic } - 5).toDouble() / 10.0) * 10.0
    val diaMax = ceil ((readings.maxOf { it.diastolic } + 5).toDouble() / 10.0) * 10.0

    val gridColor  = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val axisColor  = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sysRefColor = CategoryStage2          // red, echoes the trend chart's systolic line
    val diaRefColor = MaterialTheme.colorScheme.primary
    val tooltipBg   = MaterialTheme.colorScheme.surfaceVariant
    val tooltipFg   = MaterialTheme.colorScheme.onSurface

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tickStyle    = remember(labelColor) { TextStyle(color = labelColor, fontSize = 10.sp) }
    val refStyleSys  = remember(sysRefColor) { TextStyle(color = sysRefColor, fontSize = 10.sp) }
    val refStyleDia  = remember(diaRefColor) { TextStyle(color = diaRefColor, fontSize = 10.sp) }
    val tooltipStyle = remember(tooltipFg) { TextStyle(color = tooltipFg, fontSize = 11.sp) }

    // Padding reserved around the plot for the axis tick labels (in px).
    val padLeft   = with(density) { 30.dp.toPx() }
    val padBottom = with(density) { 18.dp.toPx() }
    val padTop    = with(density) { 8.dp.toPx() }
    val padRight  = with(density) { 8.dp.toPx() }
    val dotRadius = with(density) {
        (when { readings.size > 120 -> 2.5f; readings.size > 60 -> 3.5f; else -> 4.5f }).dp.toPx()
    }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 7f), 0f) }

    fun plotX(dia: Double, l: Float, r: Float) =
        (l + (dia - diaMin) / (diaMax - diaMin) * (r - l)).toFloat()
    fun plotY(sys: Double, t: Float, b: Float) =
        (b - (sys - sysMin) / (sysMax - sysMin) * (b - t)).toFloat()

    var selected by remember(readings) { mutableStateOf<BPReading?>(null) }
    val tapSlop = with(density) { 22.dp.toPx() }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(readings) {
                    detectTapGestures { tap ->
                        val l = padLeft; val t = padTop
                        val r = size.width - padRight; val b = size.height - padBottom
                        // Nearest reading to the tap, within the slop radius.
                        val nearest = readings.minByOrNull { rdg ->
                            hypot(
                                (plotX(rdg.diastolic.toDouble(), l, r) - tap.x).toDouble(),
                                (plotY(rdg.systolic.toDouble(), t, b) - tap.y).toDouble(),
                            )
                        }
                        selected = nearest?.takeIf { rdg ->
                            hypot(
                                (plotX(rdg.diastolic.toDouble(), l, r) - tap.x).toDouble(),
                                (plotY(rdg.systolic.toDouble(), t, b) - tap.y).toDouble(),
                            ) <= tapSlop
                        }
                    }
                },
        ) {
            val l = padLeft; val t = padTop
            val r = size.width - padRight; val b = size.height - padBottom

            // Y grid + systolic tick labels
            val yStep = if (sysMax - sysMin <= 80) 10 else 20
            var gy = sysMin.toInt()
            while (gy <= sysMax) {
                val y = plotY(gy.toDouble(), t, b)
                drawLine(gridColor, Offset(l, y), Offset(r, y), strokeWidth = 1f)
                val lay = textMeasurer.measure(gy.toString(), tickStyle)
                drawText(lay, topLeft = Offset(l - 4f - lay.size.width, y - lay.size.height / 2f))
                gy += yStep
            }
            // X grid + diastolic tick labels
            val xStep = if (diaMax - diaMin <= 80) 10 else 20
            var gx = diaMin.toInt()
            while (gx <= diaMax) {
                val x = plotX(gx.toDouble(), l, r)
                drawLine(gridColor, Offset(x, t), Offset(x, b), strokeWidth = 1f)
                val lay = textMeasurer.measure(gx.toString(), tickStyle)
                drawText(lay, topLeft = Offset(x - lay.size.width / 2f, b + 3f))
                gx += xStep
            }

            // Reference lines at 120 (systolic) / 80 (diastolic), when in range.
            if (sysMin <= REF_SYSTOLIC && REF_SYSTOLIC <= sysMax) {
                val y = plotY(REF_SYSTOLIC, t, b)
                drawLine(sysRefColor.copy(alpha = 0.55f), Offset(l, y), Offset(r, y),
                    strokeWidth = 2f, pathEffect = dashEffect)
                val lay = textMeasurer.measure("SYS ${REF_SYSTOLIC.toInt()}", refStyleSys)
                drawText(lay, topLeft = Offset(l + 4f, y - lay.size.height - 1f))
            }
            if (diaMin <= REF_DIASTOLIC && REF_DIASTOLIC <= diaMax) {
                val x = plotX(REF_DIASTOLIC, l, r)
                drawLine(diaRefColor.copy(alpha = 0.55f), Offset(x, t), Offset(x, b),
                    strokeWidth = 2f, pathEffect = dashEffect)
                val lay = textMeasurer.measure("DIA ${REF_DIASTOLIC.toInt()}", refStyleDia)
                drawText(lay, topLeft = Offset(x + 4f, t + 1f))
            }

            // Points, coloured by zone.
            for (rdg in readings) {
                drawCircle(
                    color = zoneColor(rdg.category),
                    radius = dotRadius,
                    center = Offset(plotX(rdg.diastolic.toDouble(), l, r),
                                    plotY(rdg.systolic.toDouble(), t, b)),
                )
            }

            // Axis lines (left + bottom), drawn over the grid.
            drawLine(axisColor, Offset(l, t), Offset(l, b), strokeWidth = 2f)
            drawLine(axisColor, Offset(l, b), Offset(r, b), strokeWidth = 2f)

            // Selected dot: highlight ring + value/date tooltip.
            selected?.let { rdg ->
                val cx = plotX(rdg.diastolic.toDouble(), l, r)
                val cy = plotY(rdg.systolic.toDouble(), t, b)
                drawCircle(tooltipFg, radius = dotRadius + 3f, center = Offset(cx, cy),
                    style = Stroke(width = 2f))
                val text = "${rdg.systolic}/${rdg.diastolic}  ·  ${TOOLTIP_DATE_FMT.format(Date(rdg.timestamp))}"
                val lay  = textMeasurer.measure(text, tooltipStyle)
                val padX = 8f; val padY = 5f
                val boxW = lay.size.width + padX * 2
                val boxH = lay.size.height + padY * 2
                var boxL = cx - boxW / 2f
                var boxT = cy - dotRadius - 6f - boxH
                boxL = boxL.coerceIn(l, r - boxW)
                if (boxT < t) boxT = cy + dotRadius + 6f      // flip below if it would clip the top
                drawRoundRect(
                    color = tooltipBg,
                    topLeft = Offset(boxL, boxT),
                    size = androidx.compose.ui.geometry.Size(boxW, boxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                )
                drawText(lay, topLeft = Offset(boxL + padX, boxT + padY))
            }
        }

        // Zone legend — only the zones actually present in this range.
        val present = remember(readings) {
            BPCategory.entries.filter { cat -> readings.any { it.category == cat } }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            present.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(zoneColor(cat)),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(cat.badgeLabelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val TOOLTIP_DATE_FMT = SimpleDateFormat("MMM d", Locale.getDefault())
