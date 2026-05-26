package com.example.bpdigitizer.presentation.capture

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            bindToLifecycle(lifecycleOwner)
        }
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
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        FloatingActionButton(
            onClick = {
                if (!isProcessing) {
                    takePicture(
                        controller = cameraController,
                        context = context,
                        onSuccess = onImageCaptured,
                        onError = { /* surface via state in a later pass */ },
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(72.dp),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Capture")
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Reading numbers...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }

        error?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
            ) {
                Text(
                    text = "OCR error: $it",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(3000)
                onDismissError()
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
            text = "Camera access is required to scan readings.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("Grant camera permission")
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Back")
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
