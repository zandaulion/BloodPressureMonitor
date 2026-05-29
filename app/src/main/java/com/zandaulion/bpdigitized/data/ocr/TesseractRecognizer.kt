package com.zandaulion.bpdigitized.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

private const val TAG = "BpOcr.Tesseract"
/**
 * `ssd_int.traineddata` — community model trained specifically on
 * seven-segment LCD digits (integers only).  Far more accurate on BP-monitor
 * displays than the general-purpose `eng.traineddata`, which was trained on
 * document fonts and routinely confuses 7-segment "0" / "8" / "B".
 *
 * Source: github.com/Shreeshrii/tessdata_ssd
 */
private const val LANG = "ssd_int"

/**
 * Thin wrapper around Tesseract for digit-only recognition.
 *
 * Uses a 7-segment-trained model with a digit-only character whitelist so
 * Tesseract interprets every glyph as a numeral.  Suitable for the
 * 7-segment LCD displays found on home BP monitors.
 *
 * The tessdata asset is copied to internal storage on first construction so
 * Tesseract can find it at its expected path.
 */
class TesseractRecognizer(context: Context) {

    /** Root directory passed to [TessBaseAPI.init]; must contain `tessdata/`. */
    private val dataDir: File = File(context.filesDir, "tessroot").also { root ->
        val tessdata = File(root, "tessdata")
        tessdata.mkdirs()
        val dest = File(tessdata, "$LANG.traineddata")
        if (!dest.exists() || dest.length() < 1_000_000L) {
            context.assets.open("tessdata/$LANG.traineddata").use { src ->
                FileOutputStream(dest).use { dst -> src.copyTo(dst) }
            }
            Log.d(TAG, "Copied $LANG.traineddata → ${dest.absolutePath} (${dest.length()} B)")
        }
    }

    /**
     * Recognises text in [bitmap] and returns the raw string.
     *
     * A new [TessBaseAPI] instance is created per call so this function is
     * safe to call from any coroutine context without external synchronisation.
     *
     * @param psm Tesseract page-segmentation mode.  [TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK]
     *   works well for a column of digits; [TessBaseAPI.PageSegMode.PSM_AUTO] is tried as a
     *   fallback when the caller passes multiple attempts.
     */
    fun recognize(
        bitmap: Bitmap,
        psm: Int = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
    ): String {
        val api = TessBaseAPI()
        return try {
            if (!api.init(dataDir.absolutePath, LANG)) {
                Log.e(TAG, "TessBaseAPI.init() failed")
                return ""
            }
            api.pageSegMode = psm
            // Restrict output to digits and whitespace only — forces Tesseract
            // to interpret every glyph as a numeral regardless of font style.
            api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789")
            api.setImage(bitmap)
            val result = api.utF8Text?.trim() ?: ""
            Log.d(TAG, "psm=$psm  raw=\"${result.replace('\n', '↵')}\"")
            result
        } catch (e: Exception) {
            Log.e(TAG, "recognize() threw", e)
            ""
        } finally {
            api.recycle()
        }
    }
}
