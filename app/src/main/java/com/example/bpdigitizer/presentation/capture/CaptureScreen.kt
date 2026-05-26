package com.example.bpdigitizer.presentation.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun CaptureScreen(
    onCaptured: (sys: Int?, dia: Int?, pulse: Int?, imagePath: String?) -> Unit,
    onBack: () -> Unit,
) {
    // TODO: CameraX PreviewView + ImageAnalysis.Analyzer + ML Kit
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Capture — CameraX + ML Kit coming next")
    }
}
