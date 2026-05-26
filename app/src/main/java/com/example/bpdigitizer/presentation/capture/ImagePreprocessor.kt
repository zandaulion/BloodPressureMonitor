package com.example.bpdigitizer.presentation.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Prepares a captured photo for ML Kit by:
 *   1. Loading and applying EXIF rotation.
 *   2. Downscaling to a manageable size.
 *   3. Converting to grayscale.
 *   4. Otsu-binarizing (digit pixels -> 0, background -> 255).
 *   5. Dilating the black pixels with a small kernel to bridge the gaps
 *      between 7-segment LCD strokes, so ML Kit's CNN sees connected glyphs
 *      that look more like printed text.
 *
 * The processed bitmap is also saved beside the original as `.preproc.jpg`
 * so the user can share it for diagnostics.
 */
internal object ImagePreprocessor {

    private const val MAX_DIM = 1280
    private const val DILATION_RADIUS = 1

    data class Result(val bitmap: Bitmap, val file: File)

    fun preprocess(originalFile: File): Result {
        val oriented = loadOriented(originalFile)
        val scaled = downscale(oriented, MAX_DIM)
        val gray = toGrayscale(scaled)
        val smoothed = boxBlur(gray)
        val threshold = otsuThreshold(smoothed.pixels)
        val binary = binarize(smoothed, threshold)
        val dilated = dilateBlack(binary, DILATION_RADIUS)
        val output = toBitmap(dilated)

        val preprocFile = preprocFileFor(originalFile)
        FileOutputStream(preprocFile).use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
        return Result(output, preprocFile)
    }

    private fun loadOriented(file: File): Bitmap {
        val raw = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Failed to decode bitmap from ${file.absolutePath}")
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return if (matrix.isIdentity) {
            raw
        } else {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }
    }

    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun toGrayscale(bitmap: Bitmap): GrayImage {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val y = (r * 76 + g * 150 + b * 30) shr 8
            out[i] = y.toByte()
        }
        return GrayImage(w, h, out)
    }

    /**
     * 3x3 box blur on the grayscale image. Smooths LCD "ghost" segments
     * (faint shadows of unlit digit strokes) so they don't survive Otsu
     * thresholding as noise specks that dilation would then amplify.
     */
    private fun boxBlur(input: GrayImage): GrayImage {
        val w = input.width
        val h = input.height
        val src = input.pixels
        val dst = ByteArray(w * h)
        for (y in 0 until h) {
            val yStart = (y - 1).coerceAtLeast(0)
            val yEnd = (y + 1).coerceAtMost(h - 1)
            for (x in 0 until w) {
                val xStart = (x - 1).coerceAtLeast(0)
                val xEnd = (x + 1).coerceAtMost(w - 1)
                var sum = 0
                var count = 0
                for (ny in yStart..yEnd) {
                    val rowOff = ny * w
                    for (nx in xStart..xEnd) {
                        sum += src[rowOff + nx].toInt() and 0xFF
                        count++
                    }
                }
                dst[y * w + x] = (sum / count).toByte()
            }
        }
        return GrayImage(w, h, dst)
    }

    private fun otsuThreshold(pixels: ByteArray): Int {
        val histogram = IntArray(256)
        for (b in pixels) histogram[b.toInt() and 0xFF]++
        val total = pixels.size
        var sum = 0.0
        for (i in 0..255) sum += (i * histogram[i])
        var sumB = 0.0
        var wB = 0
        var maxVar = 0.0
        var threshold = 0
        for (i in 0..255) {
            wB += histogram[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i * histogram[i]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = i
            }
        }
        return threshold
    }

    private fun binarize(gray: GrayImage, threshold: Int): GrayImage {
        val out = ByteArray(gray.pixels.size)
        for (i in gray.pixels.indices) {
            val v = gray.pixels[i].toInt() and 0xFF
            out[i] = if (v < threshold) 0.toByte() else 255.toByte()
        }
        return GrayImage(gray.width, gray.height, out)
    }

    private fun dilateBlack(input: GrayImage, radius: Int): GrayImage {
        val w = input.width
        val h = input.height
        val src = input.pixels
        val dst = ByteArray(w * h)
        for (y in 0 until h) {
            val yStart = (y - radius).coerceAtLeast(0)
            val yEnd = (y + radius).coerceAtMost(h - 1)
            for (x in 0 until w) {
                val xStart = (x - radius).coerceAtLeast(0)
                val xEnd = (x + radius).coerceAtMost(w - 1)
                var foreground = false
                outer@ for (ny in yStart..yEnd) {
                    val rowOff = ny * w
                    for (nx in xStart..xEnd) {
                        if (src[rowOff + nx].toInt() == 0) {
                            foreground = true
                            break@outer
                        }
                    }
                }
                dst[y * w + x] = if (foreground) 0.toByte() else 255.toByte()
            }
        }
        return GrayImage(w, h, dst)
    }

    private fun toBitmap(gray: GrayImage): Bitmap {
        val w = gray.width
        val h = gray.height
        val argb = IntArray(w * h)
        for (i in gray.pixels.indices) {
            val v = gray.pixels[i].toInt() and 0xFF
            argb[i] = Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
    }

    private data class GrayImage(val width: Int, val height: Int, val pixels: ByteArray)
}

internal fun preprocFileFor(imagePath: String): File {
    val imageFile = File(imagePath)
    return File(imageFile.parentFile, imageFile.nameWithoutExtension + ".preproc.jpg")
}

internal fun preprocFileFor(imageFile: File): File = preprocFileFor(imageFile.absolutePath)
