package com.zandaulion.bpdigitized.presentation.capture

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.zandaulion.bpdigitized.R
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CaptureScreen(
    onCaptured: (sys: Int?, dia: Int?, pulse: Int?, imagePath: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    LaunchedEffect(viewModel.events, lifecycleOwner) {
        viewModel.events.flowWithLifecycle(lifecycleOwner.lifecycle).collect { event ->
            when (event) {
                is CaptureEvent.ParseComplete ->
                    onCaptured(event.sys, event.dia, event.pulse, event.imagePath)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            cameraPermission.status.isGranted -> CameraContent(
                isProcessing = state.isProcessing,
                error = state.error,
                onDismissError = viewModel::clearError,
                onBack = onBack,
                onImageCaptured = viewModel::processCapturedImage,
                onCancel = viewModel::cancelProcessing,
            )
            cameraPermission.status.shouldShowRationale -> PermissionRationale(
                onRequest = cameraPermission::launchPermissionRequest,
                onBack = onBack,
            )
            else -> PermissionRationale(
                onRequest = cameraPermission::launchPermissionRequest,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CameraContent(
    isProcessing: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onImageCaptured: (Uri, String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            bindToLifecycle(lifecycleOwner)
        }
    }

    // The still just captured, frozen on screen while Gemini reads it so the
    // user sees exactly the shot being analysed rather than a live, drifting
    // preview. Cleared when processing ends (cancel/error returns to live feed).
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(isProcessing) {
        if (!isProcessing) capturedUri = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    controller = cameraController
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Wireframe guide overlay — encourages the user to align the monitor screen.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.4f)
                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = Color.White,
            )
        }

        FloatingActionButton(
            onClick = {
                if (!isProcessing) {
                    takePicture(
                        controller = cameraController,
                        context = context,
                        onSuccess = { uri, path ->
                            capturedUri = uri
                            onImageCaptured(uri, path)
                        },
                        onError = { /* surface via state in a later pass */ },
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .size(72.dp),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.capture_cd_capture))
        }

        if (isProcessing) {
            // Freeze the captured frame over the live preview, then dim it with
            // the processing overlay's scrim.
            capturedUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ProcessingOverlay(onCancel = onCancel)
        }

        error?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.capture_ocr_error, errorMsg),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            LaunchedEffect(errorMsg) {
                kotlinx.coroutines.delay(3000)
                onDismissError()
            }
        }
    }
}

// ── Processing overlay ────────────────────────────────────────────────────────
// Shown while Gemini processes the image. Keeps the user informed and gives
// them an escape hatch so they never feel trapped waiting.

private val statusMessages = listOf(
    R.string.capture_status_sending,    // 0 – 2 s
    R.string.capture_status_reading,    // 2 – 5 s
    R.string.capture_status_extracting, // 5 – 8 s
    R.string.capture_status_almost,     // 8 s+ (holds until done)
)

@Composable
private fun ProcessingOverlay(onCancel: () -> Unit) {
    // Option C: animate from 0 → 80 % over 8 s then hold.
    // The composable is only present while isProcessing = true, so
    // LaunchedEffect(Unit) is equivalent to "run once when processing starts".
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0.8f,
            animationSpec = tween(durationMillis = 8_000, easing = LinearEasing),
        )
        // Hold at 80 % — the overlay will be removed by the parent when done.
    }

    // Option B: cycle through status messages on a fixed schedule.
    var msgIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(2_000); msgIdx = 1
        delay(3_000); msgIdx = 2
        delay(3_000); msgIdx = 3
        // "Almost there…" holds until the composable is removed.
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(statusMessages[msgIdx]),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Option A: cancel button so the user is never trapped.
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.capture_permission_rationale),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.capture_grant_permission))
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

private fun takePicture(
    controller: LifecycleCameraController,
    context: Context,
    onSuccess: (Uri, String) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val outputFile = File(context.cacheDir, "bp_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    controller.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                onSuccess(Uri.fromFile(outputFile), outputFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        },
    )
}
