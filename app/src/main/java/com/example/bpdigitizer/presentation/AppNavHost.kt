package com.example.bpdigitizer.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bpdigitizer.presentation.capture.CaptureScreen
import com.example.bpdigitizer.presentation.dashboard.DashboardScreen
import com.example.bpdigitizer.presentation.help.HelpScreen
import com.example.bpdigitizer.presentation.profile.ProfileScreen
import com.example.bpdigitizer.presentation.reminders.RemindersScreen
import com.example.bpdigitizer.presentation.validation.ValidationScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val DASHBOARD = "dashboard"
    const val CAPTURE = "capture"
    const val PROFILE = "profile"
    const val REMINDERS = "reminders"
    const val HELP = "help"
    const val VALIDATION = "validation?sys={sys}&dia={dia}&pulse={pulse}&img={img}"

    fun validation(sys: Int?, dia: Int?, pulse: Int?, imgPath: String?): String {
        val encodedImg = imgPath?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }.orEmpty()
        return "validation?sys=${sys ?: ""}&dia=${dia ?: ""}&pulse=${pulse ?: ""}&img=$encodedImg"
    }

    fun manualValidation(): String = validation(null, null, null, null)
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onAddViaCamera = { nav.navigate(Routes.CAPTURE) },
                onAddManually  = { nav.navigate(Routes.manualValidation()) },
                onProfile      = { nav.navigate(Routes.PROFILE) },
                onReminders    = { nav.navigate(Routes.REMINDERS) },
                onHelp         = { nav.navigate(Routes.HELP) },
            )
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.REMINDERS) {
            RemindersScreen(onBack = { nav.popBackStack() })
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
        composable(
            route = Routes.VALIDATION,
            arguments = listOf(
                navArgument("sys") { type = NavType.StringType; defaultValue = "" },
                navArgument("dia") { type = NavType.StringType; defaultValue = "" },
                navArgument("pulse") { type = NavType.StringType; defaultValue = "" },
                navArgument("img") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ValidationScreen(
                onSaved = { nav.popBackStack(Routes.DASHBOARD, inclusive = false) },
                onRetake = {
                    nav.popBackStack(Routes.CAPTURE, inclusive = false)
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
