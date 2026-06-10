package com.zandaulion.bpdigitized.data.ocr

import android.graphics.Bitmap
import android.util.Log
import com.zandaulion.bpdigitized.domain.usecase.ParsedReading
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BpOcr.Gemini"

/**
 * The Gemini model and Vertex AI location are resolved from Remote Config so a
 * retired model (or an endpoint change) can be handled from the Firebase
 * console without shipping an app update. The defaults keep scans working
 * offline / before the first fetch completes.
 *
 * Location defaults to "global": Google's newest Gemini models are often only
 * served from the global endpoint, not regional ones like us-central1.
 */
object GeminiModelConfig {
    const val RC_KEY  = "gemini_model"
    const val DEFAULT = "gemini-2.5-flash"

    const val LOCATION_RC_KEY  = "gemini_location"
    const val LOCATION_DEFAULT = "global"
}

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
 * Sends a captured BP-monitor image to Firebase AI Logic (Vertex AI backend)
 * and returns a structured [ParsedReading].
 */
@Singleton
class GeminiOcrRecognizer @Inject constructor() {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    // Cached per model name + location; rebuilt if Remote Config changes mid-session.
    @Volatile
    private var cachedModel: Pair<String, GenerativeModel>? = null

    private fun generativeModel(): GenerativeModel {
        val name = Firebase.remoteConfig.getString(GeminiModelConfig.RC_KEY)
            .ifBlank { GeminiModelConfig.DEFAULT }
        val location = Firebase.remoteConfig.getString(GeminiModelConfig.LOCATION_RC_KEY)
            .ifBlank { GeminiModelConfig.LOCATION_DEFAULT }
        val key = "$name@$location"
        Log.i(TAG, "Using Gemini model: $key")
        cachedModel?.let { (cachedKey, model) -> if (cachedKey == key) return model }
        return Firebase.ai(backend = GenerativeBackend.vertexAI(location = location))
            .generativeModel(name)
            .also { cachedModel = key to it }
    }

    /**
     * Recognise BP values in [bitmap]. Resizes to maxDim before sending.
     */
    suspend fun recognize(bitmap: Bitmap, maxDim: Int = 1024): ParsedReading =
        withContext(Dispatchers.IO) {
            try {
                val scaledImage = scaleBitmap(bitmap, maxDim)
                Log.d(TAG, "Sending image to Gemini")

                val response = generativeModel().generateContent(
                    content {
                        image(scaledImage)
                        text(PROMPT)
                    }
                )

                val responseText = response.text
                if (responseText == null) {
                    Log.e(TAG, "Empty response from Gemini")
                    return@withContext ParsedReading(null, null, null)
                }

                Log.d(TAG, "Gemini raw response: ${responseText.take(200)}")
                extractParsedReading(responseText)
            } catch (e: Exception) {
                Log.e(TAG, "Gemini request failed", e)
                ParsedReading(null, null, null)
            }
        }

    // ── Response parsing ──────────────────────────────────────────────────────

    private fun extractParsedReading(text: String): ParsedReading {
        return try {
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

    // ── Image encoding ────────────────────────────────────────────────────────

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
