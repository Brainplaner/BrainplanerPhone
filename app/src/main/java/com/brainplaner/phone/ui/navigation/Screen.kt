package com.brainplaner.phone.ui.navigation

sealed class Screen(val route: String) {
    object DailyCheckIn : Screen("daily_check_in")
    object CognitiveWarmup : Screen("cognitive_warmup")
    object Home : Screen("home")
    object BudgetDetail : Screen("budget_detail")
    object Settings : Screen("settings")
    object Session : Screen("session")
    object Reflection : Screen("reflection/{sessionId}") {
        fun route(sessionId: String) = "reflection/$sessionId"
    }
}
