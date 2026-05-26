package com.example.bpdigitizer.presentation.validation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ValidationScreen(
    onSaved: () -> Unit,
) {
    // TODO: thumbnail + 3 OutlinedTextField + Save button + validation rules
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Validation — text fields + save coming next")
    }
}
