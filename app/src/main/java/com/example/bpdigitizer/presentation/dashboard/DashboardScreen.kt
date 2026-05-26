package com.example.bpdigitizer.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddReading: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Blood Pressure") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddReading) {
                Icon(Icons.Default.Add, contentDescription = "Add reading")
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
