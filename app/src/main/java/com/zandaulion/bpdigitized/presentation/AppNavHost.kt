package com.zandaulion.bpdigitized.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zandaulion.bpdigitized.presentation.capture.CaptureScreen
import com.zandaulion.bpdigitized.presentation.dashboard.DashboardScreen
import com.zandaulion.bpdigitized.presentation.help.HelpScreen
import com.zandaulion.bpdigitized.presentation.onboarding.OnboardingScreen
import com.zandaulion.bpdigitized.presentation.onboarding.OnboardingViewModel
import com.zandaulion.bpdigitized.presentation.profile.ProfileScreen
import com.zandaulion.bpdigitized.presentation.reminders.RemindersScreen
import com.zandaulion.bpdigitized.presentation.validation.ValidationScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val ONBOARDING = "onboarding"
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
fun AppNavHost(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val hasSeenOnboarding by onboardingViewModel.hasSeenOnboarding.collectAsStateWithLifecycle()

    // Wait until the persisted flag is loaded before choosing where to start,
    // so the intro is shown exactly once (on first launch).
    val seen = hasSeenOnboarding ?: return

    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (seen) Routes.DASHBOARD else Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    onboardingViewModel.markSeen()
                    nav.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
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
                    // CAPTURE was already popped when we navigated here (popUpTo
                    // Dashboard), so we can't pop back to it. Navigate to a fresh
                    // CAPTURE instead, clearing VALIDATION from the stack.
                    nav.navigate(Routes.CAPTURE) {
                        popUpTo(Routes.DASHBOARD)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
