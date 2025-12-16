package com.example.wakacje1.ui.theme

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wakacje1.ui.screens.DestinationSelectionScreen
import com.example.wakacje1.ui.screens.LoginScreen
import com.example.wakacje1.ui.screens.PlanScreen
import com.example.wakacje1.ui.screens.PreferencesScreen

sealed class Dest(val route: String) {
    data object Login : Dest("login")
    data object Preferences : Dest("preferences")
    data object Destinations : Dest("destinations")
    data object Plan : Dest("plan")
}

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    val vm: VacationViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Dest.Login.route
    ) {
        composable(Dest.Login.route) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Dest.Preferences.route) {
                        popUpTo(Dest.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Dest.Preferences.route) {
            PreferencesScreen(
                viewModel = vm,
                onNext = {
                    // przygotuj 3 propozycje i przejdź dalej
                    vm.prepareDestinationSuggestions()
                    navController.navigate(Dest.Destinations.route)
                }
            )
        }

        composable(Dest.Destinations.route) {
            DestinationSelectionScreen(
                viewModel = vm,
                onDestinationChosen = {
                    navController.navigate(Dest.Plan.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Dest.Plan.route) {
            PlanScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
