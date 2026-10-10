package com.zandaulion.bpdigitized.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.usecase.ParsedReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Native CPU OCR with APK-bundled models. No browser, service, download or network path. */
class HearthOcrRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "HearthOcr") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val closed = AtomicBoolean(false)
    // Accessed only on the executor, including disposal after any in-flight native call.
    private var engine: HearthEngine? = null
    private var config: HearthConfig? = null

    suspend fun recognize(bitmap: Bitmap): ParsedReading {
        check(!closed.get()) { "Reader is closed" }
        try {
            return withTimeout(60_000) {
                withContext(dispatcher) {
                    val coroutine = currentCoroutineContext()
                    val check = {
                        coroutine.ensureActive()
                        if (closed.get()) throw CancellationException("Reader disposed")
                    }
                    check()
                    require(maxOf(bitmap.width, bitmap.height) <= 1920) { "Photo exceeds the OCR resolution limit" }
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val image = HearthImage(bitmap.width, bitmap.height, pixels)
                    if (engine == null) initialize(check)
                    val result = HearthPipeline.read(image, config!!, check) { engine!!.infer(it, check) }
                    check()
                    val reading = result.reading?.takeIf { result.plausible && result.status != HearthStatus.RETAKE }
                    ParsedReading(reading?.sys, reading?.dia, reading?.pulse)
                }
            }
        } catch (error: TimeoutCancellationException) {
            throw IllegalStateException(context.getString(R.string.capture_error_local_reader), error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: LinkageError) {
            throw IllegalStateException(context.getString(R.string.capture_error_local_reader), error)
        } catch (error: Exception) {
            throw IllegalStateException(context.getString(R.string.capture_error_local_reader), error)
        }
    }

    private fun initialize(check: () -> Unit) {
        fun json(name: String) = context.assets.open("hearth/models/$name").bufferedReader().use { JSONObject(it.readText()) }
        val settings = json("config.json")
        require((0 until settings.getJSONArray("classNames").length()).map { settings.getJSONArray("classNames").getString(it) } == HearthReading.classes)
        val newConfig = HearthConfig(
            size = settings.getInt("size"), minScore = settings.getDouble("minScore"),
            acceptScore = settings.getDouble("acceptScore"), digitMinScore = settings.getDouble("digitMinScore"),
            portraitFallback = settings.getJSONObject("portraitCropFallback").getBoolean("enabled"),
            adaptiveFallback = settings.getJSONObject("adaptiveCropFallback").getBoolean("enabled"),
            rectificationFallback = settings.getJSONObject("displayRectificationFallback").getBoolean("enabled"),
        )
        val manifest = json("manifest.json").getJSONObject("files")
        val newEngine = HearthEngine(loadModel = { name ->
            check()
            val bytes = context.assets.open("hearth/models/$name").use { it.readBytes() }
            val expected = manifest.getJSONObject(name)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            val key = if (name == "bp-detector.onnx") "detectorSha256" else "digitsSha256"
            require(bytes.size == expected.getInt("bytes") && hash == expected.getString("sha256") && hash == settings.getString(key)) {
                "Bundled OCR model failed its integrity check"
            }
            bytes
        }, config = newConfig)
        engine = newEngine
        config = newConfig
    }

    /** Does not block the UI or close a native session during a run. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute {
            try { engine?.close() } finally { engine = null; config = null }
        }
        dispatcher.close() // Orderly shutdown: the queued cleanup still runs.
    }
}
