package com.zandaulion.bpdigitized.presentation.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.data.ocr.HearthOcrRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val recognizer: HearthOcrRecognizer,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var processingJob: Job? = null

    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        // Immediately clear the processing flag so the UI responds at once;
        // the finally block in processCapturedImage will also fire but that's fine.
        _state.update { it.copy(isProcessing = false, error = null) }
    }

    fun processCapturedImage(uri: Uri, path: String) {
        if (processingJob?.isActive == true) return
        processingJob = viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    ImagePreprocessor.preprocessForHearth(File(path))
                }
                val result = try {
                    recognizer.recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }

                // A scan is only usable when Hearth returns a complete plausible triplet.
                // If not (e.g. a blurry or unreadable photo), surface a clear
                // "couldn't read" message and stay on the camera, rather than navigating to
                // Validation — which would silently pre-fill the last reading / 120-80
                // defaults and look like a fabricated result.
                if (result.systolic == null || result.diastolic == null || result.pulse == null) {
                    Log.d(TAG, "════ UNREADABLE — staying on camera")
                    _state.update { it.copy(error = context.getString(R.string.capture_error_unreadable)) }
                    return@launch
                }

                _events.send(
                    CaptureEvent.ParseComplete(
                        sys       = result.systolic,
                        dia       = result.diastolic,
                        pulse     = result.pulse,
                        imagePath = path,
                    ),
                )
            } catch (e: CancellationException) {
                throw e     // let coroutine cancellation propagate normally
            } catch (e: Exception) {
                Log.e(TAG, "processCapturedImage FAILED", e)
                _state.update { it.copy(error = e.message ?: "OCR failed") }
            } finally {
                if (processingJob === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    _state.update { it.copy(isProcessing = false) }
                }
            }
        }
    }

    override fun onCleared() {
        recognizer.close()
        super.onCleared()
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

internal fun ocrDumpFileFor(imagePath: String): File {
    val imageFile = File(imagePath)
    return File(imageFile.parentFile, imageFile.nameWithoutExtension + ".ocr.txt")
}
