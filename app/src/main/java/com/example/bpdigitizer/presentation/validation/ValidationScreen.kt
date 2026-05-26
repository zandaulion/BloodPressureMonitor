package com.example.bpdigitizer.presentation.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.content.Intent
import coil.compose.AsyncImage
import com.example.bpdigitizer.presentation.capture.ocrDumpFileFor
import com.example.bpdigitizer.presentation.capture.preprocFileFor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidationScreen(
    onSaved: () -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit,
    viewModel: ValidationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.events, lifecycleOwner) {
        viewModel.events.flowWithLifecycle(lifecycleOwner.lifecycle).collect { event ->
            when (event) {
                ValidationEvent.Saved -> onSaved()
            }
        }
    }

    var fullscreenOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.imagePath != null) "Confirm Reading" else "New Reading") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = "Captured reading",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                        .clickable { fullscreenOpen = true },
                )
                Text(
                    text = "Tap the image to enlarge",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.systolic,
                    onValueChange = viewModel::updateSystolic,
                    label = { Text("Systolic") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.diastolic,
                    onValueChange = viewModel::updateDiastolic,
                    label = { Text("Diastolic") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.pulse,
                    onValueChange = viewModel::updatePulse,
                    label = { Text("Pulse") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.systolic.isNotEmpty() && state.diastolic.isNotEmpty() && !state.isValid) {
                Text(
                    text = "Systolic must be greater than Diastolic",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text("Notes (optional)") },
                placeholder = { Text("e.g. after exercise, morning before coffee") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Reading")
            }

            if (state.imagePath != null) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retake")
                }

                val context = LocalContext.current
                val ocrDumpFile = remember(state.imagePath) {
                    ocrDumpFileFor(state.imagePath!!).takeIf { it.exists() }
                }
                if (ocrDumpFile != null) {
                    OutlinedButton(
                        onClick = { shareOcrDump(context, ocrDumpFile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Share raw OCR (debug)")
                    }
                }
                val preprocFile = remember(state.imagePath) {
                    preprocFileFor(state.imagePath!!).takeIf { it.exists() }
                }
                if (preprocFile != null) {
                    OutlinedButton(
                        onClick = { sharePreprocImage(context, preprocFile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Share preprocessed image (debug)")
                    }
                }
            }
        }
    }

    if (fullscreenOpen) {
        val path = state.imagePath
        if (path != null) {
            FullscreenImageDialog(path = path, onDismiss = { fullscreenOpen = false })
        }
    }
}

private fun shareOcrDump(context: android.content.Context, file: File) {
    shareDiagnosticFile(context, file, mime = "text/plain", subject = "BP Digitizer raw OCR output")
}

private fun sharePreprocImage(context: android.content.Context, file: File) {
    shareDiagnosticFile(context, file, mime = "image/jpeg", subject = "BP Digitizer preprocessed image")
}

private fun shareDiagnosticFile(
    context: android.content.Context,
    file: File,
    mime: String,
    subject: String,
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

@Composable
private fun FullscreenImageDialog(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = "Captured reading (fullscreen)",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
