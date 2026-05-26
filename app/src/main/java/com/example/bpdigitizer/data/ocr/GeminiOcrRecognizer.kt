package com.example.bpdigitizer.data.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.bpdigitizer.BuildConfig
import com.example.bpdigitizer.domain.repository.ApiKeyRepository
import com.example.bpdigitizer.domain.usecase.ParsedReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG      = "BpOcr.Gemini"
private const val MODEL    = "gemini-2.5-flash"
private const val ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

private val PROMPT = """
    This is a photograph of a blood pressure monitor display.
    Extract the three numbers shown on the display:
    - Systolic pressure (top, typically the largest number)
    - Diastolic pressure (middle)
    - Pulse / heart rate (bottom, often shown with a heart symbol)
    Return ONLY a JSON object with integer values — no markdown, no extra text:
    {"systolic": <int>, "diastolic": <int>, "pulse": <int>}
    If you cannot read a value with confidence, omit that key from the JSON.
""".trimIndent()

/**
 * Sends a captured BP-monitor image to the Gemini 2.0 Flash vision API and
 * returns a structured [ParsedReading].  One network call replaces the entire
 * multi-scale Tesseract cascade.
 *
 * Requires `GEMINI_API_KEY` in `local.properties` (never committed to VCS).
 */
@Singleton
class GeminiOcrRecognizer @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
) {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Recognise BP values in [bitmap].  Resizes to JPEG at [maxDim] before
     * sending to stay well within the Gemini inline-data size limit.
     */
    suspend fun recognize(bitmap: Bitmap, maxDim: Int = 1024): ParsedReading =
        withContext(Dispatchers.IO) {
            // Prefer the key stored securely on-device; fall back to the
            // build-time key from local.properties (dev convenience only).
            val apiKey = apiKeyRepository.getKey()
                ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
                ?: run {
                    Log.w(TAG, "No Gemini API key configured — skipping")
                    return@withContext ParsedReading(null, null, null)
                }

            val imageB64 = bitmapToBase64(bitmap, maxDim)
            Log.d(TAG, "Sending image (~${imageB64.length / 1024} KB base64)")

            val requestJson = buildRequestJson(imageB64)
            val responseBody = post("$ENDPOINT?key=$apiKey", requestJson)
                ?: return@withContext ParsedReading(null, null, null)

            Log.d(TAG, "Raw response: ${responseBody.take(600)}")
            extractParsedReading(responseBody)
        }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private fun post(url: String, body: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput        = true
            conn.connectTimeout  = 15_000
            conn.readTimeout     = 30_000

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "(no body)"
                Log.e(TAG, "HTTP $code — $err")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    // ── Request building ──────────────────────────────────────────────────────

    private fun buildRequestJson(imageB64: String): String {
        // Escape prompt for JSON embedding
        val safePrompt = PROMPT
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """{"contents":[{"parts":[{"inlineData":{"mimeType":"image/jpeg","data":"$imageB64"}},{"text":"$safePrompt"}]}]}"""
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private fun extractParsedReading(responseBody: String): ParsedReading {
        return try {
            val root     = lenientJson.parseToJsonElement(responseBody) as? JsonObject
                ?: return ParsedReading(null, null, null)
            val text     = extractTextFromResponse(root)
                ?: return ParsedReading(null, null, null)

            Log.d(TAG, "Gemini text: $text")

            // Strip optional markdown code fence ```json … ```
            val cleaned  = text.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            // Find the first {...} JSON object in the text
            val jsonStr  = JSON_OBJECT_REGEX.find(cleaned)?.value ?: cleaned
            val obj      = lenientJson.parseToJsonElement(jsonStr) as? JsonObject
                ?: return ParsedReading(null, null, null)

            val sys   = (obj["systolic"]  as? JsonPrimitive)?.intOrNull
            val dia   = (obj["diastolic"] as? JsonPrimitive)?.intOrNull
            val pulse = (obj["pulse"]     as? JsonPrimitive)?.intOrNull

            Log.d(TAG, "Parsed → sys=$sys  dia=$dia  pulse=$pulse")
            ParsedReading(sys, dia, pulse)
        } catch (e: Exception) {
            Log.e(TAG, "Response parse error", e)
            ParsedReading(null, null, null)
        }
    }

    private fun extractTextFromResponse(root: JsonObject): String? {
        val candidates = root["candidates"] as? JsonArray ?: return null
        val first      = candidates.getOrNull(0) as? JsonObject ?: return null
        val content    = first["content"] as? JsonObject ?: return null
        val parts      = content["parts"] as? JsonArray ?: return null
        val part0      = parts.getOrNull(0) as? JsonObject ?: return null
        return (part0["text"] as? JsonPrimitive)?.content
    }

    // ── Image encoding ────────────────────────────────────────────────────────

    private fun bitmapToBase64(bitmap: Bitmap, maxDim: Int): String {
        val scaled = scaleBitmap(bitmap, maxDim)
        val out    = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width; val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    companion object {
        private val JSON_OBJECT_REGEX = Regex("\\{[\\s\\S]*\\}")
    }
}
