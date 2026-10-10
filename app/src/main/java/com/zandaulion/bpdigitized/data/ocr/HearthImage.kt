// SPDX-License-Identifier: AGPL-3.0-only
package com.zandaulion.bpdigitized.data.ocr

import kotlin.math.*

/** Bounded, opaque RGB pixels. No Android or browser APIs in the processing core. */
internal class HearthImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height) }

    fun crop(rect: HearthCrop): HearthImage {
        require(rect.x >= 0 && rect.y >= 0 && rect.width > 0 && rect.height > 0 &&
            rect.x + rect.width <= width && rect.y + rect.height <= height)
        val out = IntArray(rect.width * rect.height)
        for (y in 0 until rect.height) pixels.copyInto(out, y * rect.width,
            (rect.y + y) * width + rect.x, (rect.y + y) * width + rect.x + rect.width)
        return HearthImage(rect.width, rect.height, out)
    }

    fun resize(w: Int, h: Int, check: () -> Unit = {}): HearthImage {
        if (w == width && h == height) return this
        val out = IntArray(w * h)
        for (y in 0 until h) {
            check()
            val sy = ((y + .5) * height / h - .5).coerceIn(0.0, height - 1.0)
            for (x in 0 until w) {
                val sx = ((x + .5) * width / w - .5).coerceIn(0.0, width - 1.0)
                out[y * w + x] = interpolate(sx, sy)
            }
        }
        return HearthImage(w, h, out)
    }

    fun interpolate(sx: Double, sy: Double): Int {
        val left = floor(sx).toInt().coerceIn(0, width - 1)
        val top = floor(sy).toInt().coerceIn(0, height - 1)
        val right = min(width - 1, left + 1); val bottom = min(height - 1, top + 1)
        val fx = (sx - left).coerceIn(0.0, 1.0); val fy = (sy - top).coerceIn(0.0, 1.0)
        var color = -0x1000000
        for (shift in 16 downTo 0 step 8) {
            val a = ((pixels[top * width + left] ushr shift) and 255) * (1 - fx) +
                ((pixels[top * width + right] ushr shift) and 255) * fx
            val b = ((pixels[bottom * width + left] ushr shift) and 255) * (1 - fx) +
                ((pixels[bottom * width + right] ushr shift) and 255) * fx
            color = color or ((a * (1 - fy) + b * fy).roundToInt().coerceIn(0, 255) shl shift)
        }
        return color
    }

    fun gray(): IntArray = IntArray(pixels.size) { gray(pixels[it]) }

    fun normalized(fraction: Double, check: () -> Unit = {}): HearthImage {
        val gray = gray()
        val radius = max(3, (min(width, height) * fraction).roundToInt())
        var background = DoubleArray(gray.size) { gray[it].toDouble() }
        repeat(3) { background = blur(background, radius, check) }
        return HearthImage(width, height, IntArray(pixels.size) {
            val v = min(255, floor(gray[it] * 150 / max(background[it], 1.0) + .5).toInt())
            -0x1000000 or (v shl 16) or (v shl 8) or v
        })
    }

    private fun blur(input: DoubleArray, radius: Int, check: () -> Unit): DoubleArray {
        val temp = DoubleArray(input.size); val output = DoubleArray(input.size); val n = 2 * radius + 1
        for (y in 0 until height) {
            check()
            val base = y * width
            var sum = 0.0
            for (k in -radius..radius) sum += input[base + k.coerceIn(0, width - 1)]
            for (x in 0 until width) {
                temp[base + x] = sum / n
                sum += input[base + min(width - 1, x + radius + 1)] - input[base + max(0, x - radius)]
            }
        }
        for (x in 0 until width) {
            check()
            var sum = 0.0
            for (k in -radius..radius) sum += temp[k.coerceIn(0, height - 1) * width + x]
            for (y in 0 until height) {
                output[y * width + x] = sum / n
                sum += temp[min(height - 1, y + radius + 1) * width + x] - temp[max(0, y - radius) * width + x]
            }
        }
        return output
    }

    data class Letterbox(val tensor: FloatArray, val scale: Double, val left: Int, val top: Int)

    fun letterbox(size: Int, check: () -> Unit = {}): Letterbox {
        val scale = min(size.toDouble() / width, size.toDouble() / height)
        val w = max(1, (width * scale).roundToInt()); val h = max(1, (height * scale).roundToInt())
        val left = (size - w) / 2; val top = (size - h) / 2
        val resized = resize(w, h, check)
        val plane = size * size
        val tensor = FloatArray(3 * plane) { 114f / 255f }
        for (y in 0 until h) {
            check()
            for (x in 0 until w) {
                val index = (y + top) * size + x + left; val pixel = resized.pixels[y * w + x]
                tensor[index] = ((pixel ushr 16) and 255) / 255f
                tensor[plane + index] = ((pixel ushr 8) and 255) / 255f
                tensor[2 * plane + index] = (pixel and 255) / 255f
            }
        }
        return Letterbox(tensor, scale, left, top)
    }

    fun digitTensor(box: HearthBox, check: () -> Unit = {}): FloatArray {
        val x = max(0, floor(box.left).toInt()); val y = max(0, floor(box.top).toInt())
        val w = min(width, ceil(box.right).toInt()) - x; val h = min(height, ceil(box.bottom).toInt()) - y
        val gray = crop(HearthCrop(x, y, w, h)).resize(32, 48, check).gray()
        val mean = gray.sum().toDouble() / gray.size
        val std = max(12.0, sqrt(gray.sumOf { (it - mean).pow(2) } / gray.size))
        return FloatArray(gray.size) { ((gray[it] - mean) / std).toFloat() }
    }

    companion object {
        fun gray(pixel: Int) = (.299 * ((pixel ushr 16) and 255) +
            .587 * ((pixel ushr 8) and 255) + .114 * (pixel and 255)).roundToInt()
    }
}
