package com.zandaulion.bpdigitized.presentation.validation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import coil.compose.AsyncImage
import com.zandaulion.bpdigitized.Features
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.model.meanArterialPressure
import com.zandaulion.bpdigitized.domain.model.pulsePressure
import com.zandaulion.bpdigitized.presentation.extensions.labelRes
import com.zandaulion.bpdigitized.presentation.extensions.selectableBpTags
import com.zandaulion.bpdigitized.presentation.capture.ocrDumpFileFor
import com.zandaulion.bpdigitized.presentation.capture.preprocFileFor
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    val focusManager = LocalFocusManager.current
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

        // Outer column: shrinks away from the keyboard via imePadding so the
        // pinned Save button always floats just above the soft keyboard.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
        // Scrollable content fills the remaining space above the Save button.
        Column(
            modifier = Modifier
                .weight(1f)
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
                        .height(72.dp)
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

            // ── Contextual tags ───────────────────────────────────────────
            // Placed right after the values (above the derived MAP/PP card) so
            // it isn't buried at the bottom — easy to miss on the camera path
            // where the values arrive pre-filled and tagging is the main task.
            TagPicker(
                selected = state.selectedTags,
                onToggle = viewModel::toggleTag,
            )

            // ── Live MAP / PP readout (clinical build only) ───────────────
            if (Features.CLINICAL && state.isValid) {
                HemodynamicsReadout(
                    map = meanArterialPressure(state.systolic, state.diastolic),
                    pulsePressure = pulsePressure(state.systolic, state.diastolic),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )

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
        } // end inner scrollable Column

        // ── Pinned Save button ─────────────────────────────────────────────
        // Sits outside the scroll so it is always visible. imePadding() on
        // the outer Column keeps it floating just above the soft keyboard.
        HorizontalDivider()
        Button(
            onClick = viewModel::save,
            enabled = state.isValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.validation_save))
        }
        } // end outer Column (imePadding)
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
    // ── Option A: tap-to-type state ───────────────────────────────────────────
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // rememberUpdatedState ensures the commitEdit closure always reads the
    // latest `value` even when called from a stale LaunchedEffect coroutine.
    val latestValue by rememberUpdatedState(value)

    fun commitEdit() {
        if (!isEditing) return          // guard against double-fire on unmount
        val parsed = editValue.text.toIntOrNull()?.coerceIn(range) ?: latestValue
        onValueChange(parsed)
        isEditing = false
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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

                // ── Value display / inline editor ─────────────────────────────
                if (isEditing) {
                    // hasFocused guards against onFocusChanged firing with
                    // isFocused=false on initial mount (before focus is ever
                    // requested), which would immediately call commitEdit() and
                    // collapse the field before the keyboard even opens.
                    var hasFocused by remember { mutableStateOf(false) }

                    // Request focus + show keyboard after this composition frame,
                    // once the BasicTextField is attached to the layout tree.
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }

                    BasicTextField(
                        value = editValue,
                        onValueChange = { new ->
                            // Allow digits only, cap at 3 characters (max BP value is 250).
                            editValue = new.copy(
                                text = new.text.filter { it.isDigit() }.take(3),
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        ),
                        modifier = Modifier
                            .width(96.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { fs ->
                                if (fs.isFocused) {
                                    hasFocused = true
                                } else if (hasFocused) {
                                    // Commit when the user taps away (e.g. to another slider).
                                    commitEdit()
                                }
                            },
                    )
                } else {
                    // Tapping the number enters edit mode with the full value pre-selected
                    // so the user can immediately type a replacement without clearing first.
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                editValue = TextFieldValue(
                                    text = value.toString(),
                                    selection = TextRange(0, value.toString().length),
                                )
                                isEditing = true
                            },
                    )
                }
            }

            // ── Stepper row (Option B: long-press acceleration) ───────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AcceleratingIconButton(
                    onClick = { onValueChange((value - 1).coerceAtLeast(range.first)) },
                    enabled = value > range.first,
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
                AcceleratingIconButton(
                    onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
                    enabled = value < range.last,
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

// ── Accelerating step button ──────────────────────────────────────────────────
// Single tap → one step (identical to old IconButton behaviour).
// Hold → waits HOLD_DELAY_MS, then fires repeatedly, accelerating until it
// reaches the minimum interval MIN_INTERVAL_MS.

private const val HOLD_DELAY_MS   = 400L   // ms before auto-repeat kicks in
private const val INITIAL_INTERVAL = 120L  // ms between first auto-repeat steps
private const val MIN_INTERVAL_MS  = 30L   // ms floor (≈ 33 steps/sec)
private const val ACCEL_STEP_MS    = 8L    // ms shaved off per step

@Composable
private fun AcceleratingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Always call the latest onClick lambda so stale closures never fire the
    // wrong value after a recomposition mid-press.
    val latestOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(isPressed) {
        if (!isPressed) return@LaunchedEffect
        delay(HOLD_DELAY_MS)
        var interval = INITIAL_INTERVAL
        while (true) {
            latestOnClick()
            delay(interval)
            interval = (interval - ACCEL_STEP_MS).coerceAtLeast(MIN_INTERVAL_MS)
        }
    }

    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        content()
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

// ── Contextual tag picker ───────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPicker(
    selected: Set<BpTag>,
    onToggle: (BpTag) -> Unit,
) {
    // A distinct card (matching the slider/MAP cards) with an icon header, so the
    // tags read as an intentional step rather than faint chips at the bottom.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.validation_tags_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selectableBpTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selected,
                        onClick = { onToggle(tag) },
                        label = { Text(stringResource(tag.labelRes())) },
                    )
                }
            }
        }
    }
}

// ── Hemodynamics card ─────────────────────────────────────────────────────────

@Composable
private fun HemodynamicsReadout(map: Int, pulsePressure: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HemoMetric(label = stringResource(R.string.hemo_map_label), value = map)
            HemoMetric(label = stringResource(R.string.hemo_pp_label), value = pulsePressure)
        }
    }
}

@Composable
private fun HemoMetric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.hemo_value_mmhg, value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
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
