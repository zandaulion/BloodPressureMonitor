package com.zandaulion.bpdigitized.presentation.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.presentation.extensions.labelRes
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage2
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

// Loads full-frontal (head-on) so the view matches the 2D SYS (Y) × DIA (X) scatter;
// drag to rotate and reveal pulse (BPM) as the depth axis.
private const val INITIAL_YAW   = 0f
private const val INITIAL_PITCH = 0f
private const val PITCH_LIMIT   = 1.48f   // ~85° — stop short of flipping the plot
private const val ROT_SENS      = 0.011f  // radians of rotation per pixel dragged

// One-shot "peek": on first showing the scatter each launch, tilt out to this pose and ease
// back to head-on, so the depth (rotatability) is discoverable.
private const val PEEK_YAW   = 0.55f      // ~31°
private const val PEEK_PITCH = 0.33f      // ~19°

// Whether the discovery peek has already played this app launch (process-scoped; resets on
// a cold start). Kept out of composition so it survives toggling Trend↔Scatter.
private var peekedThisLaunch = false

// AHA normal-range upper limits, shown as dashed reference lines (as in the 2D scatter).
private const val REF_SYS = 120f
private const val REF_DIA = 80f

// Scatter dots are coloured by recency (oldest → newest). Position already shows where a
// reading sits (severity, reinforced by the 120/80 reference lines), so colour is free to
// encode *when* each reading was taken — a second, independent dimension.
private val RECENCY_OLD = Color(0xFF90CAF9)  // light blue — oldest
private val RECENCY_NEW = Color(0xFF0D47A1)  // deep blue — newest

/** A projected point ready to draw: its reading, screen position, post-rotation depth, colour. */
private class Proj(val reading: BPReading, val x: Float, val y: Float, val depth: Float, val color: Color)

/** Round tick values inside [lo, hi], with a "nice" step (1/2/5 × 10ⁿ) aiming for ~4 intervals. */
private fun niceTicks(lo: Float, hi: Float): List<Float> {
    val range = hi - lo
    if (range <= 0f) return listOf(lo)
    val rough = range / 4f
    val pow = 10.0.pow(floor(log10(rough.toDouble()))).toFloat()
    val n = rough / pow
    val step = (when { n < 1.5f -> 1f; n < 3f -> 2f; n < 7f -> 5f; else -> 10f }) * pow
    val out = ArrayList<Float>()
    var v = ceil(lo / step) * step
    while (v <= hi + 0.01f) { out.add(v); v += step }
    return out
}

/**
 * 3D scatter of each reading as diastolic (X) × systolic (Y) × pulse/BPM (Z), coloured by
 * recency. Rendered with a hand-rolled orthographic projection (no 3D dependency). The head-on
 * view is the familiar 2D scatter — a SYS×DIA reference grid with dashed SYS 120 / DIA 80
 * lines — and a one-finger drag tilts it to reveal pulse as the depth axis (double-tap
 * resets). Axes snap to round values; depth is reinforced with mild fog + size.
 */
@Composable
fun BPScatter3DChart(
    readings: List<BPReading>,
    modifier: Modifier = Modifier,
    resetKey: Int = 0,
) {
    // The 3rd axis is pulse, so only readings that carry a pulse can be placed.
    val points = remember(readings) { readings.filter { it.pulse != null } }
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chart_no_readings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Axis bounds — tight to the data (only a little breathing room) so the points fill the
    // plot. Computed from the filtered readings, so each range chip gets its own optimal fit.
    fun axisBounds(lo: Int, hi: Int): Pair<Float, Float> {
        val pad = ((hi - lo) * 0.06f).coerceAtLeast(2f)
        return (lo - pad) to (hi + pad)
    }
    val (sysMin, sysMax) = axisBounds(points.minOf { it.systolic }, points.maxOf { it.systolic })
    val (diaMin, diaMax) = axisBounds(points.minOf { it.diastolic }, points.maxOf { it.diastolic })
    val (pulMin, pulMax) = axisBounds(points.minOf { it.pulse!! }, points.maxOf { it.pulse!! })

    // Recency ramp: map each reading's timestamp to a colour (oldest → newest).
    val tMin = remember(points) { points.minOf { it.timestamp } }
    val tMax = remember(points) { points.maxOf { it.timestamp } }
    fun timeColor(ts: Long): Color =
        lerp(RECENCY_OLD, RECENCY_NEW, if (tMax > tMin) (ts - tMin).toFloat() / (tMax - tMin) else 1f)

    fun norm(v: Float, l: Float, h: Float) = if (h > l) (v - l) / (h - l) - 0.5f else 0f

    val gridColor  = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val axisColor  = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sysRefColor = CategoryStage2                       // red SYS 120 line
    val diaRefColor = MaterialTheme.colorScheme.primary    // teal DIA 80 line
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tickStyle   = remember(labelColor)  { TextStyle(color = labelColor,  fontSize = 9.sp) }
    val nameStyle   = remember(labelColor)  { TextStyle(color = labelColor,  fontSize = 11.sp) }
    val sysRefStyle = remember(sysRefColor) { TextStyle(color = sysRefColor, fontSize = 9.sp) }
    val diaRefStyle = remember(diaRefColor) { TextStyle(color = diaRefColor, fontSize = 9.sp) }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f) }

    var yaw   by remember { mutableFloatStateOf(INITIAL_YAW) }
    var pitch by remember { mutableFloatStateOf(INITIAL_PITCH) }

    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    // Smoothly animate back to the head-on orientation.
    fun settle(durMs: Int = 350) {
        animJob?.cancel()
        animJob = scope.launch {
            val y0 = yaw; val p0 = pitch
            animate(0f, 1f, animationSpec = tween(durMs)) { f, _ ->
                yaw = y0 + (INITIAL_YAW - y0) * f
                pitch = p0 + (INITIAL_PITCH - p0) * f
            }
        }
    }
    // "Peek": tilt out to PEEK_* and ease back to head-on, revealing the depth axis. First
    // returns to head-on if currently rotated, so it reads cleanly from any pose.
    fun peek() {
        animJob?.cancel()
        animJob = scope.launch {
            val y0 = yaw; val p0 = pitch
            if (abs(y0 - INITIAL_YAW) > 0.01f || abs(p0 - INITIAL_PITCH) > 0.01f) {
                animate(0f, 1f, animationSpec = tween(220)) { f, _ ->
                    yaw = y0 + (INITIAL_YAW - y0) * f
                    pitch = p0 + (INITIAL_PITCH - p0) * f
                }
            }
            animate(0f, 1f, animationSpec = tween(950, easing = LinearEasing)) { f, _ ->
                val env = sin(f * PI).toFloat()   // 0 → 1 → 0, a smooth out-and-back
                yaw = PEEK_YAW * env
                pitch = PEEK_PITCH * env
            }
            yaw = INITIAL_YAW; pitch = INITIAL_PITCH
        }
    }
    // First time the scatter is shown this launch, peek to reveal the 3rd dimension; later
    // Scatter-button taps (which bump resetKey) just settle back to head-on.
    LaunchedEffect(resetKey) {
        if (!peekedThisLaunch) { peekedThisLaunch = true; peek() } else settle()
    }

    val marginPx  = with(density) { 22.dp.toPx() }
    val minAxisPx = with(density) { 30.dp.toPx() }
    val baseR = with(density) {
        (when { points.size > 150 -> 1.75f; points.size > 60 -> 2.25f; else -> 2.75f }).dp.toPx()
    }

    // ── Touch-for-details ──────────────────────────────────────────────────────
    // Latest projected dot positions, captured during draw for hit-testing. A plain list
    // (not snapshot state), so updating it mid-draw doesn't trigger recomposition.
    val projected = remember { mutableListOf<Proj>() }
    var selected by remember { mutableStateOf<BPReading?>(null) }
    LaunchedEffect(points) { selected = null }   // clear selection when the range/data changes
    val hitRadiusPx    = with(density) { 24.dp.toPx() }
    val selectionColor = MaterialTheme.colorScheme.onSurface
    val ringStrokePx   = with(density) { 2.dp.toPx() }
    val ringGapPx      = with(density) { 3.dp.toPx() }
    // Localized tag labels by ordinal + a compact date format for the detail strip.
    val tagLabels = BpTag.entries.map { stringResource(it.labelRes()) }
    val detailDateFmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }

    Column(modifier = modifier) {
      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { animJob?.cancel() },   // user takes over from any peek
                        onDrag = { change, drag ->
                            change.consume()
                            yaw += drag.x * ROT_SENS
                            pitch = (pitch + drag.y * ROT_SENS).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // Tap a dot → select it (details show below); tap empty space → clear.
                        // On overlapping dots, pick the frontmost (greatest depth).
                        onTap = { pos ->
                            selected = projected
                                .filter { (Offset(it.x, it.y) - pos).getDistance() <= hitRadiusPx }
                                .maxByOrNull { it.depth }
                                ?.reading
                        },
                        onDoubleTap = { settle() },
                    )
                },
        ) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val cosY = cos(yaw); val sinY = sin(yaw)
            val cosP = cos(pitch); val sinP = sin(pitch)

            // Axis extents in pixels. At the head-on view X fills the width and Y the height,
            // so the SYS×DIA face is a rectangle (like the 2D scatter), not a square.
            val extX = w - 2f * marginPx
            val extY = h - 2f * marginPx
            val extZ = minOf(extX, extY)   // depth (BPM) extent — kept modest

            // Scale normalized coords by their axis extent, flip Z (so +BPM recedes), then
            // rotate (yaw about the vertical axis, pitch about the horizontal). Returns pixels.
            fun rot(nx: Float, ny: Float, nz: Float): Triple<Float, Float, Float> {
                val x = nx * extX
                val y = ny * extY
                val z = -nz * extZ
                val x1 = x * cosY + z * sinY
                val z1 = -x * sinY + z * cosY
                val y2 = y * cosP - z1 * sinP
                val z2 = y * sinP + z1 * cosP
                return Triple(x1, y2, z2)
            }

            // Perspective camera distance — gives a vanishing point so the depth direction
            // is unambiguous. Without it a flat (orthographic) 3D view reads either way
            // (Necker-cube illusion), which made the BPM axis appear to flip. Larger = milder.
            val camD = 1.8f * maxOf(extX, extY)
            fun pers(rz: Float) = camD / (camD - rz)

            // Shrink-to-fit factor so the rotated, perspective-projected box stays in view.
            val corners = listOf(
                Triple(-.5f,-.5f,-.5f), Triple(.5f,-.5f,-.5f), Triple(.5f,.5f,-.5f), Triple(-.5f,.5f,-.5f),
                Triple(-.5f,-.5f, .5f), Triple(.5f,-.5f, .5f), Triple(.5f,.5f, .5f), Triple(-.5f,.5f, .5f),
            )
            var maxPX = 1f; var maxPY = 1f
            for ((x, y, z) in corners) {
                val (rx, ry, rz) = rot(x, y, z)
                val p = pers(rz)
                if (abs(rx * p) > maxPX) maxPX = abs(rx * p)
                if (abs(ry * p) > maxPY) maxPY = abs(ry * p)
            }
            val fit = minOf((w / 2f - marginPx) / maxPX, (h / 2f - marginPx) / maxPY)

            fun scr(nx: Float, ny: Float, nz: Float): Offset {
                val (rx, ry, rz) = rot(nx, ny, nz)
                val p = pers(rz)
                return Offset(cx + rx * p * fit, cy - ry * p * fit)
            }
            fun outward(center: Offset, p: Offset, gap: Float): Offset {
                val dx = p.x - center.x; val dy = p.y - center.y
                val dl = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                return Offset(p.x + dx / dl * gap, p.y + dy / dl * gap)
            }

            // ── SYS × DIA reference grid, on the near wall (BPM = min) so it reads as the
            //    front face and the pulse depth recedes behind it. At the head-on view this
            //    IS the 2D scatter; it tilts as you rotate. ──
            fun wall(nd: Float, ns: Float) = scr(nd, ns, -0.5f)
            val wallCenter = scr(0f, 0f, -0.5f)
            val diaTicks = niceTicks(diaMin, diaMax)
            val sysTicks = niceTicks(sysMin, sysMax)

            for (v in diaTicks) {
                val nd = norm(v, diaMin, diaMax)
                drawLine(gridColor, wall(nd, -0.5f), wall(nd, 0.5f), strokeWidth = 1f)
            }
            for (v in sysTicks) {
                val ns = norm(v, sysMin, sysMax)
                drawLine(gridColor, wall(-0.5f, ns), wall(0.5f, ns), strokeWidth = 1f)
            }
            // Left + bottom edges emphasized as axes; right + top kept light.
            drawLine(axisColor, wall(-0.5f, -0.5f), wall(0.5f, -0.5f), strokeWidth = 2f)
            drawLine(axisColor, wall(-0.5f, -0.5f), wall(-0.5f, 0.5f), strokeWidth = 2f)
            drawLine(gridColor, wall(0.5f, -0.5f), wall(0.5f, 0.5f), strokeWidth = 1f)
            drawLine(gridColor, wall(-0.5f, 0.5f), wall(0.5f, 0.5f), strokeWidth = 1f)

            // Reference lines.
            if (diaMin <= REF_DIA && REF_DIA <= diaMax) {
                val nd = norm(REF_DIA, diaMin, diaMax)
                drawLine(diaRefColor, wall(nd, -0.5f), wall(nd, 0.5f), strokeWidth = 2f, pathEffect = dash)
                val lay = textMeasurer.measure("DIA ${REF_DIA.toInt()}", diaRefStyle)
                val p = wall(nd, 0.5f)
                drawText(lay, topLeft = Offset(p.x + 4f, p.y + 2f))
            }
            if (sysMin <= REF_SYS && REF_SYS <= sysMax) {
                val ns = norm(REF_SYS, sysMin, sysMax)
                drawLine(sysRefColor, wall(-0.5f, ns), wall(0.5f, ns), strokeWidth = 2f, pathEffect = dash)
                val lay = textMeasurer.measure("SYS ${REF_SYS.toInt()}", sysRefStyle)
                val p = wall(-0.5f, ns)
                drawText(lay, topLeft = Offset(p.x + 4f, p.y - lay.size.height - 1f))
            }

            // Axis value labels just outside the left (SYS) and bottom (DIA) edges.
            for (v in sysTicks) {
                val p = outward(wallCenter, wall(-0.5f, norm(v, sysMin, sysMax)), 14f)
                val lay = textMeasurer.measure(v.toInt().toString(), tickStyle)
                drawText(lay, topLeft = Offset(p.x - lay.size.width, p.y - lay.size.height / 2f))
            }
            for (v in diaTicks) {
                val p = outward(wallCenter, wall(norm(v, diaMin, diaMax), -0.5f), 14f)
                val lay = textMeasurer.measure(v.toInt().toString(), tickStyle)
                drawText(lay, topLeft = Offset(p.x - lay.size.width / 2f, p.y))
            }

            // ── BPM (depth) axis along the front-bottom-left edge; only when not edge-on
            //    (i.e. once rotated), so the head-on view stays clean. ──
            run {
                val o = scr(-0.5f, -0.5f, -0.5f)   // BPM min (front)
                val e = scr(-0.5f, -0.5f,  0.5f)   // BPM max (back)
                if ((e - o).getDistance() >= minAxisPx) {
                    drawLine(axisColor, o, e, strokeWidth = 2f)
                    for (v in niceTicks(pulMin, pulMax)) {
                        val p = scr(-0.5f, -0.5f, norm(v, pulMin, pulMax))
                        val q = outward(wallCenter, p, 13f)
                        val dx = q.x - p.x; val dy = q.y - p.y
                        val dl = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                        drawLine(axisColor, p, Offset(p.x + dx / dl * 5f, p.y + dy / dl * 5f), strokeWidth = 1.5f)
                        val lay = textMeasurer.measure(v.toInt().toString(), tickStyle)
                        drawText(lay, topLeft = Offset(q.x - lay.size.width / 2f, q.y - lay.size.height / 2f))
                    }
                    val lay = textMeasurer.measure("BPM", nameStyle)
                    val q = outward(wallCenter, scr(-0.5f, -0.5f, 0f), 26f)
                    drawText(lay, topLeft = Offset(q.x - lay.size.width / 2f, q.y - lay.size.height / 2f))
                }
            }

            // ── Points: behind→front with mild depth fog + size; sit on the grid head-on. ──
            val proj = points.map { r ->
                val (rx, ry, rz) = rot(
                    norm(r.diastolic.toFloat(), diaMin, diaMax),
                    norm(r.systolic.toFloat(),  sysMin, sysMax),
                    norm(r.pulse!!.toFloat(),   pulMin, pulMax),
                )
                val p = pers(rz)
                Proj(r, cx + rx * p * fit, cy - ry * p * fit, rz, timeColor(r.timestamp))
            }
            // Capture for tap hit-testing (plain list → no recomposition).
            projected.clear(); projected.addAll(proj)

            val dMin = proj.minOf { it.depth }; val dMax = proj.maxOf { it.depth }
            for (pt in proj.sortedBy { it.depth }) {
                val t = if (dMax > dMin) (pt.depth - dMin) / (dMax - dMin) else 1f   // 0 far → 1 near
                // Perspective sizes the dots (near bigger), fog fades the far ones.
                drawCircle(
                    color = pt.color.copy(alpha = 0.5f + 0.5f * t),
                    radius = baseR * pers(pt.depth),
                    center = Offset(pt.x, pt.y),
                )
            }
            // Ring the selected dot (drawn last, so it sits on top).
            val sel = selected
            if (sel != null) {
                proj.firstOrNull { it.reading === sel }?.let { pt ->
                    drawCircle(
                        color = selectionColor,
                        radius = baseR * pers(pt.depth) + ringGapPx,
                        center = Offset(pt.x, pt.y),
                        style = Stroke(width = ringStrokePx),
                    )
                }
            }
        }

        // Corner affordance — the ControlCamera glyph hints rotation; tapping it re-peeks.
        Icon(
            imageVector = Icons.Default.ControlCamera,
            contentDescription = stringResource(R.string.scatter_cd_rotate),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .clickable { peek() }
                .padding(4.dp)
                .size(20.dp),
        )
      } // Box

        // Below the chart: the tapped reading's details, or — when nothing is selected —
        // the recency colour key.
        val detail = selected
        if (detail != null) {
            val line = buildString {
                append(detailDateFmt.format(Date(detail.timestamp)))
                append("    ${detail.systolic}/${detail.diastolic}")
                detail.pulse?.let { append("  ·  $it bpm") }
                if (detail.tags.isNotEmpty()) {
                    append("  ·  ")
                    append(detail.tags.joinToString(", ") { tagLabels[it.ordinal] })
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                detail.notes?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            // Recency legend — colour encodes how recent each reading is (older → newer).
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .height(9.dp)
                        .width(72.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(RECENCY_OLD, RECENCY_NEW))),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scatter_colour_time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
