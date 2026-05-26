package com.example.bpdigitizer.presentation.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.bpdigitizer.data.ocr.GeminiOcrRecognizer
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

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
    private val gemini: GeminiOcrRecognizer,
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

                // Resize to 1024 px max-dim; Gemini handles the rest.
                val bitmap = withContext(Dispatchers.Default) {
                    ImagePreprocessor.preprocessLight(File(path), maxDim = 1024)
                }

                val result = gemini.recognize(bitmap)

                Log.d(TAG, "════ FINAL  sys=${result.systolic}  dia=${result.diastolic}  pulse=${result.pulse}")
                _events.send(
                    CaptureEvent.ParseComplete(
                        sys       = result.systolic,
                        dia       = result.diastolic,
                        pulse     = result.pulse,
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
