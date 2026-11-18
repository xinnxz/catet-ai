package com.catetai.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.catetai.app.presentation.detail.DetailScreen
import com.catetai.app.presentation.home.HomeScreen
import com.catetai.app.presentation.record.RecordScreen
import com.catetai.app.presentation.settings.SettingsScreen

@Composable
fun CatetAiNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToRecord = {
                    navController.navigate(Screen.Record.route)
                },
                onNavigateToDetail = { recordingId ->
                    navController.navigate(Screen.Detail.createRoute(recordingId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Record.route) {
            RecordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("recordingId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getString("recordingId").orEmpty()
            DetailScreen(
                recordingId = recordingId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
