package com.catetai.app.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Record : Screen("record")
    data object Detail : Screen("detail/{recordingId}") {
        fun createRoute(recordingId: String) = "detail/$recordingId"
    }
    data object Settings : Screen("settings")
}
