package com.zandaulion.bpdigitized

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zandaulion.bpdigitized.presentation.AppNavHost
import com.zandaulion.bpdigitized.presentation.theme.BpDigitizerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BpDigitizerTheme {
                AppNavHost()
            }
        }
    }
}
