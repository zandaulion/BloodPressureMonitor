// SPDX-License-Identifier: AGPL-3.0-only
// Native port of Hearth / bp-digitizer-web reading and fallback rules.
package com.zandaulion.bpdigitized.data.ocr

import kotlin.math.*

internal data class HearthBox(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2
    fun scaled(scale: Double) = HearthBox(left * scale, top * scale, right * scale, bottom * scale)
    fun moved(x: Int, y: Int) = HearthBox(left + x, top + y, right + x, bottom + y)
    fun intersection(other: HearthBox) = max(0.0, min(right, other.right) - max(left, other.left)) *
        max(0.0, min(bottom, other.bottom) - max(top, other.top))
    fun iou(other: HearthBox): Double {
        val overlap = intersection(other)
        return overlap / max(width * height + other.width * other.height - overlap, 1e-9)
    }
}

internal data class HearthDetection(val label: String, val score: Double, val box: HearthBox)
internal data class HearthTriplet(val sys: Int, val dia: Int, val pulse: Int) {
    val plausible get() = sys in 50..280 && dia in 25..180 && pulse in 20..250 && sys > dia
}
internal enum class HearthStatus { RETAKE, REVIEW, CANDIDATE }
internal data class HearthResult(
    val status: HearthStatus = HearthStatus.RETAKE,
    val reading: HearthTriplet? = null,
    val score: Double? = null,
    val detections: List<HearthDetection> = emptyList(),
) {
    val plausible get() = reading?.plausible == true
}

internal data class HearthCrop(val x: Int, val y: Int, val width: Int, val height: Int)

internal object HearthReading {
    val classes = listOf("0", "1", "row", "2", "3", "4", "5", "6", "7", "8", "9")

    fun nms(detections: List<HearthDetection>): List<HearthDetection> {
        val kept = mutableListOf<HearthDetection>()
        for (d in detections.sortedByDescending { it.score }) {
            if (kept.none { old ->
                if ((d.label == "row") != (old.label == "row")) false
                else if (d.box.iou(old.box) > .45) true
                else if (d.label == "row") false
                else {
                    val smaller = min(d.box.width * d.box.height, old.box.width * old.box.height)
                    smaller > 0 && d.box.intersection(old.box) / smaller > .75
                }
            }) kept.add(d)
        }
        return kept
    }

    fun decode(data: FloatArray, shape: LongArray, scale: Double, left: Int, top: Int,
               width: Int, height: Int, minScore: Double): List<HearthDetection> {
        require(shape.size == 3 && shape[0] == 1L && shape[1] == 15L)
        val count = shape[2].toInt()
        require(count > 0 && data.size == 15 * count)
        val detections = mutableListOf<HearthDetection>()
        for (i in 0 until count) {
            var cid = 0
            var score = Double.NEGATIVE_INFINITY
            for (c in classes.indices) {
                val candidate = data[(c + 4) * count + i].toDouble()
                if (candidate > score) { cid = c; score = candidate }
            }
            if (!score.isFinite() || score < minScore) continue
            val cx = data[i].toDouble(); val cy = data[count + i].toDouble()
            val w = data[2 * count + i].toDouble(); val h = data[3 * count + i].toDouble()
            val b = HearthBox(
                ((cx - w / 2 - left) / scale).coerceIn(0.0, width.toDouble()),
                ((cy - h / 2 - top) / scale).coerceIn(0.0, height.toDouble()),
                ((cx + w / 2 - left) / scale).coerceIn(0.0, width.toDouble()),
                ((cy + h / 2 - top) / scale).coerceIn(0.0, height.toDouble()),
            )
            if (listOf(b.left, b.top, b.right, b.bottom).all { it.isFinite() } && b.width > 0 && b.height > 0) {
                detections.add(HearthDetection(classes[cid], score, b))
            }
        }
        return nms(detections)
    }

    private data class Row(val value: Int, val box: HearthBox, val digits: List<HearthDetection>, val score: Double)

    fun assemble(input: List<HearthDetection>, minScore: Double = .2, acceptScore: Double = .25): HearthResult {
        val detections = nms(input.filter { it.score.isFinite() && it.score >= minScore })
        val rows = detections.filter { it.label == "row" }.sortedByDescending { it.score }.take(8)
        val digits = detections.filter { it.label != "row" }
        val candidates = rows.mapNotNull { row ->
            val b = row.box
            val group = digits.filter { d ->
                d.box.right >= b.left - .2 * b.height && d.box.left <= b.right + .2 * b.height &&
                    d.box.cy >= b.top - .08 * b.height && d.box.cy <= b.bottom + .08 * b.height
            }.sortedBy { it.box.cx }
            if (group.size !in 2..3 || group.any { it.label.length != 1 || it.label[0] !in '0'..'9' }) null
            else {
                val text = group.joinToString("") { it.label }
                if (text.startsWith('0')) null else Row(text.toInt(), b, group, min(row.score, group.minOf { it.score }))
            }
        }
        val valid = mutableListOf<List<Row>>()
        for (i in candidates.indices) for (j in i + 1 until candidates.size) for (k in j + 1 until candidates.size) {
            val stack = listOf(candidates[i], candidates[j], candidates[k]).sortedBy { it.box.cy }
            val (s, d, p) = stack
            if (s.box.iou(d.box) > .15 || s.box.iou(p.box) > .15 || d.box.iou(p.box) > .15) continue
            val all = stack.flatMap { it.digits }
            // A digit cannot participate in two fields (identity, as in the JS Set).
            if (all.indices.any { a -> (a + 1 until all.size).any { b -> all[a] === all[b] } }) continue
            if (!(s.box.cy < d.box.cy && d.box.cy < p.box.cy)) continue
            val span = p.box.cy - s.box.cy
            if (min(d.box.cy - s.box.cy, p.box.cy - d.box.cy) < .12 * span) continue
            if (abs(s.box.cx - d.box.cx) > max(s.box.width, d.box.width)) continue
            if (abs(p.box.cx - d.box.cx) > max(s.box.width, d.box.width)) continue
            if (min(s.box.height, d.box.height) / max(s.box.height, d.box.height) < .45) continue
            valid.add(stack)
        }
        if (valid.size != 1) return HearthResult(detections = detections)
        val stack = valid.single()
        val reading = HearthTriplet(stack[0].value, stack[1].value, stack[2].value)
        val score = stack.minOf { it.score }
        return HearthResult(if (reading.plausible && score >= acceptScore) HearthStatus.CANDIDATE else HearthStatus.REVIEW,
            reading, score, detections)
    }

    fun portraitCrops(width: Int, height: Int): List<HearthCrop> {
        if (height < 1.2 * width) return emptyList()
        return listOf(doubleArrayOf(.15, .25, .7, .6), doubleArrayOf(.25, .32, .6, .5)).map {
            HearthCrop((it[0] * width).roundToInt(), (it[1] * height).roundToInt(),
                (it[2] * width).roundToInt(), (it[3] * height).roundToInt())
        }
    }

    fun rowRegion(detections: List<HearthDetection>, width: Int, height: Int): HearthCrop? {
        val rows = detections.filter { it.label == "row" && it.score >= .25 }.sortedBy { it.box.cy }.map { it.box }
        if (rows.size !in 2..3) return null
        if (min(rows[0].height, rows[1].height) / max(rows[0].height, rows[1].height) < .45) return null
        if (rows.maxOf { it.cx } - rows.minOf { it.cx } > rows.maxOf { it.width }) return null
        if (rows.zipWithNext().any { (a, b) -> b.cy - a.cy < .5 * min(a.height, b.height) }) return null
        val x1 = rows.minOf { it.left }; val x2 = rows.maxOf { it.right }
        val y1 = rows.minOf { it.top }; val y2 = rows.maxOf { it.bottom }
        val rw = x2 - x1; val rh = (rows[0].height + rows[1].height) / 2
        val left = max(0, floor(x1 - .2 * rw).toInt()); val top = max(0, floor(y1 - .25 * rh).toInt())
        val right = min(width, ceil(x2 + .2 * rw).toInt())
        val bottom = min(height, ceil(max(y2 + .3 * rh, y1 + 3.3 * rh)).toInt())
        return if (right - left >= 40 && bottom - top >= 60) HearthCrop(left, top, right - left, bottom - top) else null
    }

    /** Full readings are preserved. All recovered readings still require native confirmation. */
    fun consensus(full: HearthResult, views: List<HearthResult>, mode: String): HearthResult {
        if (full.plausible) return full
        val plausible = views.filter { it.plausible && it.status != HearthStatus.RETAKE }
        if (mode == "rectified") {
            if (plausible.size < 2) return full
        } else {
            if (views.size != 2 || plausible.size != 2) return full
            if (mode == "portrait" && plausible.any { it.status != HearthStatus.CANDIDATE }) return full
        }
        if (plausible.map { it.reading }.distinct().size != 1) return full
        val review = mode == "rectified" || plausible.any { it.status == HearthStatus.REVIEW }
        return plausible.first().copy(status = if (review) HearthStatus.REVIEW else HearthStatus.CANDIDATE,
            score = plausible.minOf { it.score ?: 0.0 })
    }
}
