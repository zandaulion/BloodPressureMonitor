package com.zandaulion.mlkitbenchmark

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TAG = "MlKitBpBenchmark"
private val FIELDS = listOf("sys", "dia", "pulse")

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setPadding(40, 40, 40, 40)
            text = "Preparing ML Kit benchmark…"
        }
        setContentView(status)
        executor.execute(::runBenchmark)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun show(message: String) = runOnUiThread { status.text = message }

    private fun runBenchmark() {
        val root = File(getExternalFilesDir(null), "benchmark")
        val manifestFile = File(root, "manifest.json")
        if (!manifestFile.isFile) {
            show("Missing ${manifestFile.absolutePath}")
            return
        }
        val manifest = JSONObject(manifestFile.readText())
        val readable = manifest.getJSONArray("readable")
        val refusals = manifest.getJSONArray("refusal")
        val variants = listOf(
            Variant("raw") { file -> Prepared(InputImage.fromFilePath(this, android.net.Uri.fromFile(file))) },
            Variant("full-grayscale-1024") { file ->
                val bitmap = grayscale(resize(loadOriented(file), 1024))
                Prepared(InputImage.fromBitmap(bitmap, 0), bitmap)
            },
            Variant("row-crop-raw", "rowCrop") { file ->
                Prepared(InputImage.fromFilePath(this, android.net.Uri.fromFile(file)))
            },
            Variant("row-crop-grayscale", "rowCrop") { file ->
                val bitmap = grayscale(resize(loadOriented(file), 1280))
                Prepared(InputImage.fromBitmap(bitmap, 0), bitmap)
            },
            Variant("row-crop-otsu", "rowCrop") { file ->
                val bitmap = otsuDilate(grayscale(resize(loadOriented(file), 1280)), 0)
                Prepared(InputImage.fromBitmap(bitmap, 0), bitmap)
            },
            Variant("row-crop-otsu-dilate", "rowCrop") { file ->
                val bitmap = otsuDilate(grayscale(resize(loadOriented(file), 1280)), 1)
                Prepared(InputImage.fromBitmap(bitmap, 0), bitmap)
            },
            Variant("rectified-raw", "rectified") { file ->
                Prepared(InputImage.fromFilePath(this, android.net.Uri.fromFile(file)))
            },
            Variant("rectified-grayscale", "rectified") { file ->
                val bitmap = grayscale(resize(loadOriented(file), 1280))
                Prepared(InputImage.fromBitmap(bitmap, 0), bitmap)
            },
        )
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val variantReports = linkedMapOf<String, VariantReport>()
            for (variant in variants) {
                val records = JSONArray()
                val total = readable.length() + refusals.length()
                var position = 0
                for (role in listOf("readable", "refusal")) {
                    val entries = if (role == "readable") readable else refusals
                    for (index in 0 until entries.length()) {
                        val item = entries.getJSONObject(index)
                        position++
                        show("${variant.name}: $position/$total")
                        val source = variant.source(item, root)
                        if (source == null) {
                            records.put(unavailableRecord(item, role))
                            continue
                        }
                        val (imageFile, preprocessMs) = source
                        val started = SystemClock.elapsedRealtimeNanos()
                        val prepared = variant.prepare(imageFile)
                        val text = try {
                            Tasks.await(recognizer.process(prepared.image))
                        } finally {
                            prepared.bitmap?.recycle()
                        }
                        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                        val prediction = parse(text)
                        records.put(record(
                            item, role, prediction, elapsedMs, preprocessMs, text.text
                        ))
                        if (position % 10 == 0) {
                            Log.i(TAG, "${variant.name}: $position/$total")
                        }
                    }
                }
                variantReports[variant.name] = summarize(variant.name, records)
                Log.i(TAG, "SUMMARY ${variantReports[variant.name]!!.summary}")
            }
            val consensus = consensusReport(variantReports)
            val output = JSONObject()
                .put("benchmark", "external-v2 consumed regression suite")
                .put("independentHeldOutClaim", false)
                .put("selectionAllowed", false)
                .put("device", JSONObject()
                    .put("manufacturer", android.os.Build.MANUFACTURER)
                    .put("model", android.os.Build.MODEL)
                    .put("sdk", android.os.Build.VERSION.SDK_INT))
                .put("variants", JSONObject().apply {
                    for ((name, report) in variantReports) {
                        put(name, JSONObject().put("summary", report.summary).put("records", report.records))
                    }
                    put("strict-consensus", JSONObject()
                        .put("summary", consensus.summary)
                        .put("records", consensus.records))
                })
            val outputFile = File(root, "mlkit-report.json")
            outputFile.writeText(output.toString(2) + "\n")
            Log.i(TAG, "BENCHMARK_DONE ${outputFile.absolutePath}")
            show("Done\n${outputFile.absolutePath}\n\n${consensus.summary}")
        } catch (error: Throwable) {
            Log.e(TAG, "Benchmark failed", error)
            show("Failed: ${error.message}")
        } finally {
            recognizer.close()
        }
    }
}

private data class Reading(val sys: Int?, val dia: Int?, val pulse: Int?) {
    fun complete() = sys != null && dia != null && pulse != null
    fun appAccepted() = sys != null && dia != null
    fun key() = if (complete()) "$sys/$dia/$pulse" else null
}

private data class Prepared(val image: InputImage, val bitmap: Bitmap? = null)
private data class Source(val file: File, val preprocessMs: Double)
private data class Variant(
    val name: String,
    val viewKey: String? = null,
    val prepare: (File) -> Prepared,
) {
    fun source(item: JSONObject, root: File): Source? {
        if (viewKey == null) return Source(File(root, item.getString("file")), 0.0)
        val views = item.optJSONObject("views") ?: return null
        val view = views.optJSONObject(viewKey) ?: return null
        return Source(File(root, view.getString("file")), view.optDouble("preprocessMs", 0.0))
    }
}
private data class VariantReport(val summary: JSONObject, val records: JSONArray)
private data class NumberToken(val value: Int, val top: Int, val height: Int)

private fun parse(text: Text): Reading {
    val tokens = mutableListOf<NumberToken>()
    for (block in text.textBlocks) for (line in block.lines) for (element in line.elements) {
        val box = element.boundingBox ?: continue
        Regex("\\d{2,3}").findAll(element.text).forEach { match ->
            val value = match.value.toInt()
            if (value in 20..280) tokens += NumberToken(value, box.top, box.height())
        }
    }
    if (tokens.isEmpty()) return Reading(null, null, null)
    val averageHeight = tokens.map { it.height }.average()
    val threshold = (averageHeight * 0.6).roundToInt().coerceAtLeast(8)
    val rows = mutableListOf<MutableList<NumberToken>>()
    for (token in tokens.sortedBy { it.top }) {
        val current = rows.lastOrNull()
        if (current != null && token.top - current.first().top < threshold) current += token
        else rows += mutableListOf(token)
    }
    val values = rows.map { row -> row.maxOf { it.value } }
    val positional = Reading(values.getOrNull(0), values.getOrNull(1), values.getOrNull(2))
    if (positional.sys != null && positional.dia != null && positional.sys > positional.dia) {
        return positional
    }
    val descending = tokens.map { it.value }.distinct().sortedDescending()
    return Reading(descending.getOrNull(0), descending.getOrNull(1), descending.getOrNull(2))
}

private fun record(
    item: JSONObject,
    role: String,
    prediction: Reading,
    elapsedMs: Double,
    preprocessMs: Double,
    rawText: String,
): JSONObject {
    val output = JSONObject()
        .put("id", item.getString("id"))
        .put("role", role)
        .put("prediction", readingJson(prediction))
        .put("appAccepted", prediction.appAccepted())
        .put("complete", prediction.complete())
        .put("available", true)
        .put("elapsedMs", elapsedMs)
        .put("preprocessMs", preprocessMs)
        .put("rawText", rawText)
    if (role == "readable") {
        val expected = Reading(item.getInt("sys"), item.getInt("dia"), item.getInt("pulse"))
        output.put("expected", readingJson(expected))
        output.put("exactTriplet", prediction == expected)
        output.put("fields", JSONObject()
            .put("sys", prediction.sys == expected.sys)
            .put("dia", prediction.dia == expected.dia)
            .put("pulse", prediction.pulse == expected.pulse))
    }
    return output
}

private fun unavailableRecord(item: JSONObject, role: String): JSONObject {
    val output = JSONObject()
        .put("id", item.getString("id"))
        .put("role", role)
        .put("prediction", JSONObject.NULL)
        .put("appAccepted", false)
        .put("complete", false)
        .put("available", false)
        .put("elapsedMs", 0.0)
        .put("preprocessMs", 0.0)
        .put("rawText", "")
    if (role == "readable") {
        output.put("expected", JSONObject()
            .put("sys", item.getInt("sys"))
            .put("dia", item.getInt("dia"))
            .put("pulse", item.getInt("pulse")))
            .put("exactTriplet", false)
            .put("fields", JSONObject()
                .put("sys", false)
                .put("dia", false)
                .put("pulse", false))
    }
    return output
}

private fun readingJson(reading: Reading): Any {
    if (reading.sys == null && reading.dia == null && reading.pulse == null) return JSONObject.NULL
    return JSONObject()
        .put("sys", reading.sys ?: JSONObject.NULL)
        .put("dia", reading.dia ?: JSONObject.NULL)
        .put("pulse", reading.pulse ?: JSONObject.NULL)
}

private fun summarize(name: String, records: JSONArray): VariantReport {
    val readable = (0 until records.length()).map { records.getJSONObject(it) }
        .filter { it.getString("role") == "readable" }
    val refusal = (0 until records.length()).map { records.getJSONObject(it) }
        .filter { it.getString("role") == "refusal" }
    val accepted = readable.filter { it.getBoolean("appAccepted") }
    val complete = readable.filter { it.getBoolean("complete") }
    val exact = readable.count { it.getBoolean("exactTriplet") }
    val exactAccepted = accepted.count { it.getBoolean("exactTriplet") }
    val exactComplete = complete.count { it.getBoolean("exactTriplet") }
    val available = readable.filter { it.optBoolean("available", true) }
    val latencies = available.map { it.getDouble("elapsedMs") }.sorted()
    val preprocessTimes = available.map { it.optDouble("preprocessMs", 0.0) }.sorted()
    val fields = JSONObject()
    for (field in FIELDS) {
        fields.put(field, readable.count { it.getJSONObject("fields").getBoolean(field) })
    }
    val summary = JSONObject()
        .put("name", name)
        .put("readableImages", readable.size)
        .put("availableImages", available.size)
        .put("exactTriplets", exact)
        .put("exactAccuracy", exact.toDouble() / readable.size)
        .put("appAccepted", accepted.size)
        .put("appAcceptedPrecision", ratio(exactAccepted, accepted.size))
        .put("completeOutputs", complete.size)
        .put("completeOutputPrecision", ratio(exactComplete, complete.size))
        .put("fieldsCorrect", fields)
        .put("safeRefusals", refusal.count { !it.getBoolean("appAccepted") })
        .put("strictNoReading", refusal.count { it.isNull("prediction") })
        .put("medianMs", percentile(latencies, 0.5))
        .put("p95Ms", percentile(latencies, 0.95))
        .put("medianSourcePreprocessMs", percentile(preprocessTimes, 0.5))
        .put("p95SourcePreprocessMs", percentile(preprocessTimes, 0.95))
    return VariantReport(summary, records)
}

private fun consensusReport(reports: Map<String, VariantReport>): VariantReport {
    val names = reports.keys.toList()
    val first = reports.getValue(names.first()).records
    val output = JSONArray()
    for (index in 0 until first.length()) {
        val source = first.getJSONObject(index)
        val variants = names.map { reports.getValue(it).records.getJSONObject(index) }
        val completeKeys = variants.mapNotNull { item ->
            if (!item.getBoolean("complete")) null else {
                val prediction = item.getJSONObject("prediction")
                "${prediction.getInt("sys")}/${prediction.getInt("dia")}/${prediction.getInt("pulse")}" 
            }
        }
        val counts = completeKeys.groupingBy { it }.eachCount()
        val selected = if (counts.size == 1 && counts.values.first() >= 2) {
            variants.first { item ->
                if (!item.getBoolean("complete")) false else {
                    val prediction = item.getJSONObject("prediction")
                    "${prediction.getInt("sys")}/${prediction.getInt("dia")}/${prediction.getInt("pulse")}" == counts.keys.first()
                }
            }.getJSONObject("prediction")
        } else null
        val prediction = if (selected == null) Reading(null, null, null) else Reading(
            selected.getInt("sys"), selected.getInt("dia"), selected.getInt("pulse")
        )
        val item = JSONObject()
            .put("id", source.getString("id"))
            .put("role", source.getString("role"))
            .put("prediction", readingJson(prediction))
            .put("appAccepted", prediction.appAccepted())
            .put("complete", prediction.complete())
            .put("available", variants.any { it.optBoolean("available", true) })
            .put("elapsedMs", variants.sumOf { it.getDouble("elapsedMs") })
            .put("preprocessMs", variants.maxOf { it.optDouble("preprocessMs", 0.0) })
        if (source.getString("role") == "readable") {
            val expectedJson = source.getJSONObject("expected")
            val expected = Reading(
                expectedJson.getInt("sys"), expectedJson.getInt("dia"), expectedJson.getInt("pulse")
            )
            item.put("expected", expectedJson)
                .put("exactTriplet", prediction == expected)
                .put("fields", JSONObject()
                    .put("sys", prediction.sys == expected.sys)
                    .put("dia", prediction.dia == expected.dia)
                    .put("pulse", prediction.pulse == expected.pulse))
        }
        output.put(item)
    }
    return summarize("strict-consensus", output)
}

private fun ratio(numerator: Int, denominator: Int): Any =
    if (denominator == 0) JSONObject.NULL else numerator.toDouble() / denominator

private fun percentile(values: List<Double>, fraction: Double): Any {
    if (values.isEmpty()) return JSONObject.NULL
    return values[(ceil(fraction * values.size).toInt() - 1).coerceIn(values.indices)]
}

private fun loadOriented(file: File): Bitmap {
    val raw = BitmapFactory.decodeFile(file.absolutePath) ?: error("Cannot decode ${file.name}")
    val orientation = ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
    )
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    if (matrix.isIdentity) return raw
    val oriented = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    raw.recycle()
    return oriented
}

private fun resize(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longest
    val resized = Bitmap.createScaledBitmap(
        bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true
    )
    bitmap.recycle()
    return resized
}

private fun grayscale(bitmap: Bitmap): Bitmap {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        val gray = (((pixel shr 16) and 255) * 76 + ((pixel shr 8) and 255) * 150 +
            (pixel and 255) * 30) shr 8
        pixels[index] = Color.rgb(gray, gray, gray)
    }
    val output = Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    bitmap.recycle()
    return output
}

private fun otsuDilate(bitmap: Bitmap, radius: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val source = IntArray(width * height)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    val histogram = IntArray(256)
    for (pixel in source) histogram[pixel and 255]++
    var totalSum = 0.0
    for (value in 0..255) totalSum += value * histogram[value]
    var backgroundWeight = 0
    var backgroundSum = 0.0
    var bestVariance = -1.0
    var threshold = 0
    for (value in 0..255) {
        backgroundWeight += histogram[value]
        if (backgroundWeight == 0) continue
        val foregroundWeight = source.size - backgroundWeight
        if (foregroundWeight == 0) break
        backgroundSum += value * histogram[value]
        val backgroundMean = backgroundSum / backgroundWeight
        val foregroundMean = (totalSum - backgroundSum) / foregroundWeight
        val variance = backgroundWeight.toDouble() * foregroundWeight *
            (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
        if (variance > bestVariance) {
            bestVariance = variance
            threshold = value
        }
    }
    var binary = BooleanArray(source.size) { index -> (source[index] and 255) < threshold }
    val centralBlack = (height / 4 until 3 * height / 4).sumOf { y ->
        (width / 4 until 3 * width / 4).count { x -> binary[y * width + x] }
    }
    val centralTotal = (width / 2) * (height / 2)
    if (centralBlack > centralTotal * 0.55) binary = BooleanArray(binary.size) { !binary[it] }
    val output = IntArray(source.size)
    for (y in 0 until height) for (x in 0 until width) {
        var black = false
        loop@ for (dy in -radius..radius) for (dx in -radius..radius) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height && binary[ny * width + nx]) {
                black = true
                break@loop
            }
        }
        output[y * width + x] = if (black) Color.BLACK else Color.WHITE
    }
    bitmap.recycle()
    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
}
