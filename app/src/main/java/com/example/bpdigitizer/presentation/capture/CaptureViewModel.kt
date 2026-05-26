package com.example.bpdigitizer.presentation.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bpdigitizer.domain.usecase.ParseOcrTextUseCase
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private val recognizer: TextRecognizer,
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
                val image = InputImage.fromFilePath(context, uri)
                val text = recognizer.process(image).await()
                runCatching { ocrDumpFileFor(path).writeText(dumpMlKitText(text)) }
                val parsed = parseOcr(text)
                _events.send(
                    CaptureEvent.ParseComplete(
                        sys = parsed.systolic,
                        dia = parsed.diastolic,
                        pulse = parsed.pulse,
                        imagePath = path,
                    ),
                )
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "OCR failed") }
            } finally {
                _state.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
}

internal fun ocrDumpFileFor(imagePath: String): File {
    val imageFile = File(imagePath)
    return File(imageFile.parentFile, imageFile.nameWithoutExtension + ".ocr.txt")
}
