package com.brainplaner.phone.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brainplaner.phone.LocalStore
import com.brainplaner.phone.ui.home.DailyCheckInScreen
import com.brainplaner.phone.ui.home.HomeScreen
import com.brainplaner.phone.ui.home.HomeViewModel
import com.brainplaner.phone.ui.reflection.ReflectionScreen
import com.brainplaner.phone.ui.reflection.ReflectionViewModel
import com.brainplaner.phone.ui.warmup.CognitiveWarmupScreen

@Composable
fun AppNavigation(
    userId: String,
    apiUrl: String,
    userToken: String,
    getActiveSessionId: () -> String?,
    onStartSession: suspend (plannedMinutes: Int) -> Result<String>,
    onStopSession: suspend () -> Result<String>,
    onLogout: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val application = LocalContext.current.applicationContext as Application

    // Create ViewModel eagerly so cloud fetch starts during warm-up
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.appFactory(application, userId, apiUrl, userToken)
    )

    NavHost(navController = navController, startDestination = Screen.CognitiveWarmup.route) {
        composable(Screen.CognitiveWarmup.route) {
            // If warm-up is disabled, skip straight to check-in
            if (!LocalStore.isWarmupEnabled(application)) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.DailyCheckIn.route) {
                        popUpTo(Screen.CognitiveWarmup.route) { inclusive = true }
                    }
                }
                return@composable
            }
            CognitiveWarmupScreen(
                baselineMs = LocalStore.getWarmupBaseline(application),
                onComplete = { medianMs ->
                    LocalStore.saveWarmupResult(application, medianMs)
                    navController.navigate(Screen.DailyCheckIn.route) {
                        popUpTo(Screen.CognitiveWarmup.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.DailyCheckIn.route) {
                        popUpTo(Screen.CognitiveWarmup.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.DailyCheckIn.route) {
            DailyCheckInScreen(
                viewModel = homeViewModel,
                onContinue = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.DailyCheckIn.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onStartSession = onStartSession,
                onStopSession = {
                    val sessionId = getActiveSessionId()
                    val result = onStopSession()
                    if (result.isSuccess && sessionId != null) {
                        navController.navigate(Screen.Reflection.route(sessionId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                    result
                },
                onResetCheckIn = {
                    navController.navigate(Screen.DailyCheckIn.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onLogout = onLogout,
            )
        }
        composable(
            route = Screen.Reflection.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val vm: ReflectionViewModel = viewModel(
                factory = ReflectionViewModel.factory(application, sessionId, userId, apiUrl, userToken)
            )
            ReflectionScreen(
                viewModel = vm,
                onDone = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
    }
}