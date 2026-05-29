package com.zandaulion.bpdigitized.presentation.validation

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import coil.compose.AsyncImage
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.presentation.capture.ocrDumpFileFor
import com.zandaulion.bpdigitized.presentation.capture.preprocFileFor
import java.io.File
import kotlin.math.roundToInt

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
                title = {
                    Text(
                        stringResource(
                            if (state.imagePath != null) R.string.validation_title_confirm
                            else R.string.validation_title_new,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { inner ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── OCR image preview ──────────────────────────────────────────
            state.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = stringResource(R.string.validation_cd_captured_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                        .clickable { fullscreenOpen = true },
                )
                Text(
                    text = stringResource(R.string.validation_hint_enlarge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Value sliders ─────────────────────────────────────────────
            val labelSys = stringResource(R.string.validation_label_sys)
            val labelDia = stringResource(R.string.validation_label_dia)
            val labelPul = stringResource(R.string.validation_label_pul)

            BpValueSlider(
                label = labelSys,
                subtitle = stringResource(R.string.validation_subtitle_sys),
                value = state.systolic,
                onValueChange = viewModel::updateSystolic,
                range = ValidationViewModel.SYS_RANGE,
            )
            BpValueSlider(
                label = labelDia,
                subtitle = stringResource(R.string.validation_subtitle_dia),
                value = state.diastolic,
                onValueChange = viewModel::updateDiastolic,
                range = ValidationViewModel.DIA_RANGE,
            )
            BpValueSlider(
                label = labelPul,
                subtitle = stringResource(R.string.validation_subtitle_pul),
                value = state.pulse,
                onValueChange = viewModel::updatePulse,
                range = ValidationViewModel.PUL_RANGE,
            )

            // ── Validation error ──────────────────────────────────────────
            if (!state.isValid) {
                Text(
                    text = stringResource(R.string.validation_error_sys_dia),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // ── Notes ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text(stringResource(R.string.validation_notes_label)) },
                placeholder = { Text(stringResource(R.string.validation_notes_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = viewModel::save,
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.validation_save))
            }

            // ── OCR-only actions ──────────────────────────────────────────
            if (state.imagePath != null) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.validation_retake))
                }

                val context = LocalContext.current
                val ocrDumpFile = remember(state.imagePath) {
                    ocrDumpFileFor(state.imagePath!!).takeIf { it.exists() }
                }
                if (ocrDumpFile != null) {
                    OutlinedButton(
                        onClick = {
                            shareDiagnosticFile(
                                context, ocrDumpFile, "text/plain",
                                context.getString(R.string.validation_debug_ocr),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.validation_debug_ocr))
                    }
                }
                val preprocFile = remember(state.imagePath) {
                    preprocFileFor(state.imagePath!!).takeIf { it.exists() }
                }
                if (preprocFile != null) {
                    OutlinedButton(
                        onClick = {
                            shareDiagnosticFile(
                                context, preprocFile, "image/jpeg",
                                context.getString(R.string.validation_debug_preproc),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.validation_debug_preproc))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (fullscreenOpen) {
        val path = state.imagePath
        if (path != null) {
            FullscreenImageDialog(path = path, onDismiss = { fullscreenOpen = false })
        }
    }
}

// ── Slider component ───────────────────────────────────────────────────────────

@Composable
private fun BpValueSlider(
    label: String,
    subtitle: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = { if (value > range.first) onValueChange(value - 1) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.validation_cd_decrease, label),
                    )
                }
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValueChange(it.roundToInt()) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (value < range.last) onValueChange(value + 1) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.validation_cd_increase, label),
                    )
                }
            }

            Text(
                text = "${range.first} – ${range.last}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// ── Fullscreen image ───────────────────────────────────────────────────────────

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
                contentDescription = stringResource(R.string.validation_cd_captured_fullscreen),
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
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = Color.White,
                )
            }
        }
    }
}

// ── Share helpers ──────────────────────────────────────────────────────────────

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
