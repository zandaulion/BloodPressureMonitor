package com.example.bpdigitizer.presentation.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bpdigitizer.data.ocr.TesseractRecognizer
import com.example.bpdigitizer.domain.usecase.ParseOcrTextUseCase
import com.example.bpdigitizer.domain.usecase.ParsedReading
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "BpOcr.ViewModel"

data class CaptureState(
    val isProcessing: Boolean = false,
    val error: String? = null,
)

sealed interface CaptureEvent {
    data class ParseComplete(
        val sys: Int?,
        val dia: Int?,
        val pulse: Int?,
        val imagePath: String,
    ) : CaptureEvent
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tesseract: TesseractRecognizer,
    private val parseOcr: ParseOcrTextUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun processCapturedImage(uri: Uri, path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            try {
                Log.d(TAG, "════ processCapturedImage  path=$path")

                // ── Multi-scale Tesseract cascade ─────────────────────────────
                // Tesseract with a digit-only whitelist interprets every glyph
                // as a numeral.  We try several preprocessing strategies and
                // scales and stop as soon as we get both SYS and DIA.
                //
                // For each attempt we record the raw Tesseract string to the
                // .ocr.txt sidecar file so the user can share it for support.

                data class Attempt(
                    val label: String,
                    val maxDim: Int,
                    val binarise: Boolean,
                    val psm: Int = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                )

                val attempts = listOf(
                    Attempt("gray-800-single",  maxDim = 800,  binarise = false),
                    Attempt("gray-600-single",  maxDim = 600,  binarise = false),
                    Attempt("gray-400-single",  maxDim = 400,  binarise = false),
                    Attempt("gray-800-auto",    maxDim = 800,  binarise = false,
                        psm = TessBaseAPI.PageSegMode.PSM_AUTO),
                    Attempt("bin-1280-single",  maxDim = 1280, binarise = true),
                    Attempt("bin-800-single",   maxDim = 800,  binarise = true),
                )

                val dumpFile = ocrDumpFileFor(path)
                dumpFile.delete()                          // fresh log each run

                var parsed = ParsedReading(null, null, null)

                for (attempt in attempts) {
                    if (parsed.systolic != null && parsed.diastolic != null) break

                    val bitmap = withContext(Dispatchers.Default) {
                        if (attempt.binarise) {
                            ImagePreprocessor.preprocess(File(path)).bitmap
                        } else {
                            ImagePreprocessor.preprocessLight(File(path), attempt.maxDim)
                        }
                    }

                    val rawText = withContext(Dispatchers.Default) {
                        tesseract.recognize(bitmap, attempt.psm)
                    }
                    Log.d(TAG, "  [${attempt.label}]  raw=\"${rawText.replace('\n','↵')}\"")
                    runCatching {
                        dumpFile.appendText("=== ${attempt.label} ===\n$rawText\n\n")
                    }

                    val result = parseOcr(rawText)
                    Log.d(TAG, "  [${attempt.label}]  sys=${result.systolic}  dia=${result.diastolic}  pulse=${result.pulse}")

                    // Merge: accept newly found values into the running result.
                    // Guard: never let sys be filled with a value already
                    //        confirmed as dia or pulse by an earlier pass.
                    val sysCand = result.systolic
                    val mergedSys = parsed.systolic ?: run {
                        if (sysCand != null
                            && sysCand != parsed.diastolic
                            && sysCand != parsed.pulse
                        ) sysCand else null
                    }
                    val mergedDia   = parsed.diastolic ?: result.diastolic
                    val mergedPulse = parsed.pulse     ?: result.pulse
                    if (mergedSys != parsed.systolic || mergedDia != parsed.diastolic || mergedPulse != parsed.pulse) {
                        parsed = ParsedReading(mergedSys, mergedDia, mergedPulse)
                        Log.d(TAG, "  → merged  sys=${parsed.systolic}  dia=${parsed.diastolic}  pulse=${parsed.pulse}")
                    }
                }

                Log.d(TAG, "════ FINAL  sys=${parsed.systolic}  dia=${parsed.diastolic}  pulse=${parsed.pulse}")
                _events.send(
                    CaptureEvent.ParseComplete(
                        sys       = parsed.systolic,
                        dia       = parsed.diastolic,
                        pulse     = parsed.pulse,
                        imagePath = path,
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "processCapturedImage FAILED", e)
                _state.update { it.copy(error = e.message ?: "OCR failed") }
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

internal fun ocrDumpFileFor(imagePath: String): File {
    val imageFile = File(imagePath)
    return File(imageFile.parentFile, imageFile.nameWithoutExtension + ".ocr.txt")
}
