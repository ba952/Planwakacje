package com.example.wakacje1.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wakacje1.ui.screens.DestinationSelectionScreen
import com.example.wakacje1.ui.screens.LoginScreen
import com.example.wakacje1.ui.screens.MyPlansScreen
import com.example.wakacje1.ui.screens.PlanScreen
import com.example.wakacje1.ui.screens.PreferencesScreen
import com.example.wakacje1.ui.screens.RegisterScreen
import com.example.wakacje1.ui.viewmodel.AuthViewModel
import com.example.wakacje1.ui.viewmodel.MyPlansViewModel
import com.example.wakacje1.ui.viewmodel.VacationViewModel

sealed class Dest(val route: String) {
    data object Splash : Dest("splash")
    data object Login : Dest("login")
    data object Register : Dest("register")
    data object MyPlans : Dest("my_plans")
    data object Preferences : Dest("preferences")
    data object Destinations : Dest("destinations")
    data object Plan : Dest("plan")
}

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {

    val vacationVm: VacationViewModel = viewModel()
    val authVm: AuthViewModel = viewModel()
    val plansVm: MyPlansViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Dest.Splash.route
    ) {

        composable(Dest.Splash.route) {
            val user = authVm.user
            LaunchedEffect(user) {
                if (user == null) {
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.Splash.route) { inclusive = true }
                    }
                } else {
                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.Splash.route) { inclusive = true }
                    }
                }
            }
        }

        composable(Dest.Login.route) {
            LoginScreen(
                authVm = authVm,
                onDone = {
                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.Login.route) { inclusive = true }
                    }
                },
                onGoRegister = {
                    navController.navigate(Dest.Register.route)
                }
            )
        }

        composable(Dest.Register.route) {
            RegisterScreen(
                authVm = authVm,
                onDone = {
                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.Register.route) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Dest.MyPlans.route) {
            // opcjonalne zabezpieczenie: jak user zrobi się null (np. sesja padnie), wróć do logowania
            val user = authVm.user
            LaunchedEffect(user) {
                if (user == null) {
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                    }
                }
            }

            MyPlansScreen(
                authVm = authVm,
                plansVm = plansVm,
                vacationVm = vacationVm,
                onNewPlan = { navController.navigate(Dest.Preferences.route) },
                onOpenPlan = { navController.navigate(Dest.Plan.route) },
                onLoggedOut = {
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Dest.Preferences.route) {
            PreferencesScreen(
                viewModel = vacationVm,
                onNext = {
                    vacationVm.prepareDestinationSuggestions()
                    navController.navigate(Dest.Destinations.route)
                }
            )
        }

        composable(Dest.Destinations.route) {
            DestinationSelectionScreen(
                viewModel = vacationVm,
                onDestinationChosen = { navController.navigate(Dest.Plan.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Dest.Plan.route) {
            PlanScreen(
                viewModel = vacationVm,
                uid = authVm.user?.uid,
                onBack = { navController.popBackStack() },
                onGoMyPlans = {
                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
