package com.example.bpdigitizer.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddViaCamera: () -> Unit,
    onAddManually: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Blood Pressure") }) },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallFloatingActionButton(onClick = onAddManually) {
                    Icon(Icons.Default.Edit, contentDescription = "Add reading manually")
                }
                FloatingActionButton(onClick = onAddViaCamera) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan reading with camera")
                }
            }
        },
    ) { inner ->
        Box(
            modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Dashboard — chart + history coming next")
        }
    }
}
