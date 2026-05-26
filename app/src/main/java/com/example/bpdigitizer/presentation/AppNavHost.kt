package com.example.bpdigitizer.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bpdigitizer.presentation.capture.CaptureScreen
import com.example.bpdigitizer.presentation.dashboard.DashboardScreen
import com.example.bpdigitizer.presentation.validation.ValidationScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val CAPTURE = "capture"
    const val VALIDATION = "validation?sys={sys}&dia={dia}&pulse={pulse}&img={img}"
    fun validation(sys: Int?, dia: Int?, pulse: Int?, imgPath: String?) =
        "validation?sys=${sys ?: ""}&dia=${dia ?: ""}&pulse=${pulse ?: ""}&img=${imgPath ?: ""}"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(onAddReading = { nav.navigate(Routes.CAPTURE) })
        }
        composable(Routes.CAPTURE) {
            CaptureScreen(
                onCaptured = { sys, dia, pulse, img ->
                    nav.navigate(Routes.validation(sys, dia, pulse, img)) {
                        popUpTo(Routes.DASHBOARD)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.VALIDATION) {
            ValidationScreen(onSaved = { nav.popBackStack(Routes.DASHBOARD, inclusive = false) })
        }
    }
}
