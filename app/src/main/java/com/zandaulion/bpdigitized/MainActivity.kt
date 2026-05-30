package com.zandaulion.bpdigitized

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zandaulion.bpdigitized.presentation.AppNavHost
import com.zandaulion.bpdigitized.presentation.locale.LocaleHelper
import com.zandaulion.bpdigitized.presentation.locale.LocalePrefs
import com.zandaulion.bpdigitized.presentation.theme.BpDigitizerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val code = LocalePrefs.getLocale(newBase)
        super.attachBaseContext(LocaleHelper.applyLocale(newBase, code))
    }

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
