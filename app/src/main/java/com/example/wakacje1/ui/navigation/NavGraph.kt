package com.example.wakacje1.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wakacje1.presentation.viewmodel.AuthViewModel
import com.example.wakacje1.presentation.viewmodel.MyPlansViewModel
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import com.example.wakacje1.ui.screens.DestinationSelectionScreen
import com.example.wakacje1.ui.screens.LoginScreen
import com.example.wakacje1.ui.screens.MyPlansScreen
import com.example.wakacje1.ui.screens.PlanScreen
import com.example.wakacje1.ui.screens.PreferencesScreen
import com.example.wakacje1.ui.screens.RegisterScreen
import org.koin.androidx.compose.koinViewModel

sealed class Dest(val route: String) {
    data object Splash : Dest("splash")
    data object Login : Dest("login")
    data object Register : Dest("register")
    data object MyPlans : Dest("my_plans")
    data object Preferences : Dest("preferences")
    data object DestinationSelection : Dest("destinations")
    data object Plan : Dest("plan")
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    vacationViewModel: VacationViewModel
) {
    val authVm: AuthViewModel = koinViewModel()

    NavHost(
        navController = navController,
        startDestination = Dest.Splash.route
    ) {

        composable(Dest.Splash.route) {
            val state by authVm.state.collectAsState()
            val uid = state.uid

            LaunchedEffect(uid) {
                val target = if (uid == null) Dest.Login.route else Dest.MyPlans.route
                navController.navigate(target) {
                    popUpTo(Dest.Splash.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        composable(Dest.Login.route) {
            LoginScreen(
                authVm = authVm,
                onDone = {
                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onGoRegister = {
                    navController.navigate(Dest.Register.route) {
                        popUpTo(Dest.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onContinueAsGuest = {
                    navController.navigate(Dest.Preferences.route) {
                        popUpTo(Dest.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Dest.Register.route) {
            RegisterScreen(
                authVm = authVm,
                onDone = {
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.Register.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onGoLogin = {
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.Register.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Dest.MyPlans.route) {
            val state by authVm.state.collectAsState()
            val uid = state.uid

            if (uid == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                return@composable
            }

            val plansVm: MyPlansViewModel = koinViewModel()

            // ✅ POPRAWKA: zgodnie z Twoją aktualną sygnaturą MyPlansScreen
            MyPlansScreen(
                authVm = authVm,
                plansVm = plansVm,
                vacationVm = vacationViewModel,
                onNewPlan = { navController.navigate(Dest.Preferences.route) },
                onOpenPlan = { navController.navigate(Dest.Plan.route) },
                onLoggedOut = {
                    authVm.signOut()
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Dest.Preferences.route) {
            PreferencesScreen(
                viewModel = vacationViewModel,
                onNext = {
                    vacationViewModel.prepareDestinationSuggestions()
                    navController.navigate(Dest.DestinationSelection.route)
                },
                onGoMyPlans = { navController.navigate(Dest.MyPlans.route) }
            )
        }

        composable(Dest.DestinationSelection.route) {
            DestinationSelectionScreen(
                viewModel = vacationViewModel,
                onDestinationChosen = { navController.navigate(Dest.Plan.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Dest.Plan.route) {
            PlanScreen(
                viewModel = vacationViewModel,
                onBack = { navController.popBackStack() },
                onGoMyPlans = { navController.navigate(Dest.MyPlans.route) }
            )
        }
    }
}
