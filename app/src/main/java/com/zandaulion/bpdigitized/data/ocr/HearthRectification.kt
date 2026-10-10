// SPDX-License-Identifier: AGPL-3.0-only
// Port of bp-digitizer-web's experimental display-rectification.js.
package com.zandaulion.bpdigitized.data.ocr

import kotlin.math.*

internal data class HearthPoint(val x: Double, val y: Double)
internal object HearthRectification {
    fun orderQuad(points: List<HearthPoint>): List<HearthPoint> {
        require(points.size == 4 && points.all { it.x.isFinite() && it.y.isFinite() })
        val bySum = points.sortedBy { it.x + it.y }
        val tl = bySum.first(); val br = bySum.last()
        val middle = points.filter { it !== tl && it !== br }.sortedByDescending { it.x - it.y }
        require(middle.size == 2)
        return listOf(tl, middle[0], br, middle[1])
    }

    private fun distance(a: HearthPoint, b: HearthPoint) = hypot(a.x - b.x, a.y - b.y)
    private fun cross(o: HearthPoint, a: HearthPoint, b: HearthPoint) =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    private fun area(points: List<HearthPoint>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]; val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2
    }
    private fun bounds(points: List<HearthPoint>) = HearthBox(points.minOf { it.x }, points.minOf { it.y },
        points.maxOf { it.x }, points.maxOf { it.y })
    private fun contains(point: HearthPoint, quad: List<HearthPoint>): Boolean {
        var direction = 0.0
        for (i in quad.indices) {
            val c = cross(quad[i], quad[(i + 1) % 4], point)
            if (abs(c) < 1e-6) continue
            if (direction == 0.0) direction = sign(c) else if (sign(c) != direction) return false
        }
        return true
    }

    private fun equalize(input: IntArray): IntArray {
        val histogram = IntArray(256); input.forEach { histogram[it]++ }
        val map = IntArray(256)
        var cumulative = 0; var first = 0
        for (i in 0..255) {
            cumulative += histogram[i]
            if (first == 0 && cumulative != 0) first = cumulative
            map[i] = (255.0 * max(0, cumulative - first) / max(1, input.size - first)).roundToInt()
        }
        return IntArray(input.size) { map[input[it]] }
    }

    private fun gaussian(input: IntArray, w: Int, h: Int, check: () -> Unit): IntArray {
        val kernel = intArrayOf(1, 4, 6, 4, 1); val temp = FloatArray(input.size); val out = IntArray(input.size)
        for (y in 0 until h) {
            check()
            for (x in 0 until w) {
                var sum = 0
                for (k in -2..2) sum += kernel[k + 2] * input[y * w + (x + k).coerceIn(0, w - 1)]
                temp[y * w + x] = sum / 16f
            }
        }
        for (y in 0 until h) {
            check()
            for (x in 0 until w) {
                var sum = 0.0
                for (k in -2..2) sum += kernel[k + 2] * temp[(y + k).coerceIn(0, h - 1) * w + x].toDouble()
                out[y * w + x] = (sum / 16).roundToInt()
            }
        }
        return out
    }

    private fun edges(input: IntArray, w: Int, h: Int, check: () -> Unit): IntArray {
        val gradient = FloatArray(input.size); val direction = IntArray(input.size)
        for (y in 1 until h - 1) {
            check()
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -input[i - w - 1] + input[i - w + 1] - 2 * input[i - 1] + 2 * input[i + 1] - input[i + w - 1] + input[i + w + 1]
                val gy = -input[i - w - 1] - 2 * input[i - w] - input[i - w + 1] + input[i + w - 1] + 2 * input[i + w] + input[i + w + 1]
                gradient[i] = hypot(gx.toDouble(), gy.toDouble()).toFloat()
                val angle = (atan2(gy.toDouble(), gx.toDouble()) * 180 / PI + 180) % 180
                direction[i] = if (angle < 22.5 || angle >= 157.5) 0 else if (angle < 67.5) 1 else if (angle < 112.5) 2 else 3
            }
        }
        val thin = FloatArray(input.size)
        for (y in 1 until h - 1) {
            check()
            for (x in 1 until w - 1) {
                val i = y * w + x
                val offset = when (direction[i]) { 0 -> 1; 1 -> w - 1; 2 -> w; else -> w + 1 }
                if (gradient[i] >= gradient[i - offset] && gradient[i] >= gradient[i + offset]) thin[i] = gradient[i]
            }
        }
        val mask = IntArray(input.size); val queue = IntArray(input.size)
        var head = 0; var tail = 0
        for (i in thin.indices) if (thin[i] >= 110) { mask[i] = 1; queue[tail++] = i }
        while (head < tail) {
            if (head % 1024 == 0) check()
            val i = queue[head++]; val y = i / w; val x = i % w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val next = ny * w + nx
                if (mask[next] == 0 && thin[next] >= 35) { mask[next] = 1; queue[tail++] = next }
            }
        }
        return closeMask(mask, w, h, check)
    }

    private fun closeMask(mask: IntArray, w: Int, h: Int, check: () -> Unit): IntArray {
        // Four separable replicated-border passes: horizontal/vertical dilation then erosion.
        fun pass(input: IntArray, horizontal: Boolean, erosion: Boolean): IntArray {
            val output = IntArray(input.size)
            val lines = if (horizontal) h else w; val size = if (horizontal) w else h
            for (line in 0 until lines) {
                check()
                fun index(v: Int) = if (horizontal) line * w + v.coerceIn(0, w - 1) else v.coerceIn(0, h - 1) * w + line
                var count = 0
                for (k in -3..3) count += input[index(k)]
                for (v in 0 until size) {
                    output[index(v)] = if (if (erosion) count == 7 else count > 0) 1 else 0
                    count += input[index(v + 4)] - input[index(v - 3)]
                }
            }
            return output
        }
        return pass(pass(pass(pass(mask, true, false), false, false), true, true), false, true)
    }

    private fun hull(points: List<HearthPoint>): List<HearthPoint> {
        val unique = points.distinct().sortedWith(compareBy<HearthPoint> { it.x }.thenBy { it.y })
        if (unique.size <= 4) return unique
        fun chain(points: List<HearthPoint>): MutableList<HearthPoint> {
            val out = mutableListOf<HearthPoint>()
            for (p in points) {
                while (out.size >= 2 && cross(out[out.size - 2], out.last(), p) <= 0) out.removeAt(out.lastIndex)
                out.add(p)
            }
            out.removeAt(out.lastIndex)
            return out
        }
        return chain(unique) + chain(unique.asReversed())
    }

    private fun largestQuad(hull: List<HearthPoint>): List<HearthPoint>? {
        if (hull.size < 4) return null
        val points = if (hull.size <= 24) hull else List(24) { hull[it * hull.size / 24] }
        var best: List<HearthPoint>? = null; var bestArea = 0.0
        for (a in 0 until points.size - 3) for (b in a + 1 until points.size - 2)
            for (c in b + 1 until points.size - 1) for (d in c + 1 until points.size) {
                val quad = listOf(points[a], points[b], points[c], points[d]); val area = area(quad)
                if (area > bestArea) { best = quad; bestArea = area }
            }
        return best?.let { orderQuad(it) }
    }

    fun detect(image: HearthImage, detections: List<HearthDetection> = emptyList(), check: () -> Unit = {}): List<List<HearthPoint>> {
        val w = image.width; val h = image.height
        if (w < 80 || h < 80) return emptyList()
        val mask = edges(gaussian(equalize(image.gray()), w, h, check), w, h, check)
        val visited = BooleanArray(mask.size); val queue = IntArray(mask.size)
        val centers = detections.filter { it.label == "row" && it.score >= .2 }.map { HearthPoint(it.box.cx, it.box.cy) }
        val scored = mutableListOf<Pair<Double, List<HearthPoint>>>()
        for (start in mask.indices) {
            if (start % 1024 == 0) check()
            if (mask[start] == 0 || visited[start]) continue
            var head = 0; var tail = 0; var pixels = 0
            val boundary = mutableListOf<HearthPoint>()
            queue[tail++] = start; visited[start] = true
            while (head < tail) {
                if (head % 1024 == 0) check()
                val index = queue[head++]; val y = index / w; val x = index % w
                pixels++
                var edge = false
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) { edge = true; continue }
                    val next = ny * w + nx
                    if (mask[next] == 0) edge = true
                    else if (!visited[next]) { visited[next] = true; queue[tail++] = next }
                }
                if (edge) boundary.add(HearthPoint(x.toDouble(), y.toDouble()))
            }
            if (pixels < 40) continue
            val quad = largestQuad(hull(boundary)) ?: continue
            if (quad.distinct().size != 4) continue
            val fraction = area(quad) / (w * h)
            if (fraction < .025 || fraction > .9) continue
            val qw = max(distance(quad[0], quad[1]), distance(quad[3], quad[2]))
            val qh = max(distance(quad[0], quad[3]), distance(quad[1], quad[2]))
            if (qw / max(1.0, qh) !in .45..3.2 || qw < 60 || qh < 60) continue
            val contained = centers.count { contains(it, quad) }
            val centerDistance = hypot(quad.sumOf { it.x / 4 } - w / 2.0, quad.sumOf { it.y / 4 } - h / 2.0) / max(w, h)
            scored.add((3 * contained + 1.5 * fraction - .4 * centerDistance) to quad)
        }
        val selected = mutableListOf<List<HearthPoint>>()
        for ((_, points) in scored.sortedByDescending { it.first }) {
            if (selected.any { bounds(points).iou(bounds(it)) > .9 }) continue
            selected.add(points)
            if (selected.size == 3) break
        }
        return selected
    }

    fun warp(image: HearthImage, rawPoints: List<HearthPoint>, check: () -> Unit = {}): HearthImage? {
        val points = orderQuad(rawPoints)
        var w = max(distance(points[0], points[1]), distance(points[3], points[2])).roundToInt()
        var h = max(distance(points[0], points[3]), distance(points[1], points[2])).roundToInt()
        if (w < 80 || h < 80) return null
        val scale = min(1.0, 1024.0 / max(w, h))
        w = max(80, (w * scale).roundToInt()); h = max(80, (h * scale).roundToInt())
        val destination = listOf(HearthPoint(0.0, 0.0), HearthPoint(w - 1.0, 0.0), HearthPoint(w - 1.0, h - 1.0), HearthPoint(0.0, h - 1.0))
        val matrix = Array(8) { DoubleArray(9) }
        for (i in 0..3) {
            val (x, y) = destination[i]; val (u, v) = points[i]
            matrix[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            matrix[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        val transform = solve(matrix) ?: return null
        val out = IntArray(w * h)
        for (y in 0 until h) {
            check()
            for (x in 0 until w) {
                val denominator = transform[6] * x + transform[7] * y + 1
                val sx = (transform[0] * x + transform[1] * y + transform[2]) / denominator
                val sy = (transform[3] * x + transform[4] * y + transform[5]) / denominator
                if (!sx.isFinite() || !sy.isFinite()) return null
                out[y * w + x] = image.interpolate(sx, sy)
            }
        }
        return HearthImage(w, h, out)
    }

    private fun solve(matrix: Array<DoubleArray>): DoubleArray? {
        val n = matrix.size
        for (column in 0 until n) {
            var pivot = column
            for (row in column + 1 until n) if (abs(matrix[row][column]) > abs(matrix[pivot][column])) pivot = row
            if (abs(matrix[pivot][column]) < 1e-9) return null
            val temp = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = temp
            val divisor = matrix[column][column]
            for (j in column..n) matrix[column][j] /= divisor
            for (row in 0 until n) {
                if (row == column) continue
                val factor = matrix[row][column]
                for (j in column..n) matrix[row][j] -= factor * matrix[column][j]
            }
        }
        return DoubleArray(n) { matrix[it][n] }
    }

    fun quads(image: HearthImage, detections: List<HearthDetection>, check: () -> Unit): List<List<HearthPoint>> {
        val scale = min(1.0, 768.0 / max(image.width, image.height))
        val analysis = image.resize(max(1, (image.width * scale).roundToInt()), max(1, (image.height * scale).roundToInt()), check)
        return detect(analysis, detections.map { it.copy(box = it.box.scaled(scale)) }, check)
            .map { points -> points.map { HearthPoint(it.x / scale, it.y / scale) } }
    }
}
