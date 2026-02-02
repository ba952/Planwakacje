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
    data object Destinations : Dest("destinations")
    data object Plan : Dest("plan")
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    vacationViewModel: VacationViewModel
) {
    val authVm: AuthViewModel = koinViewModel()
    val plansVm: MyPlansViewModel = koinViewModel()

    val authState by authVm.state.collectAsState()
    val uid = authState.uid

    NavHost(
        navController = navController,
        startDestination = Dest.Splash.route
    ) {

        composable(Dest.Splash.route) {
            // Jednorazowe przekierowanie zależnie od stanu zalogowania.
            LaunchedEffect(uid) {
                if (uid == null) {
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
                onGoRegister = { navController.navigate(Dest.Register.route) }
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
                onGoLogin = { navController.popBackStack() }
            )
        }

        composable(Dest.MyPlans.route) {
            // Guard tylko nawigacyjny: jeśli user się wyloguje, cofamy do loginu.
            // NIE dotykamy plansVm.stop() - od tego jest MyPlansScreen (DisposableEffect).
            LaunchedEffect(uid) {
                if (uid == null) {
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                    }
                }
            }

            MyPlansScreen(
                authVm = authVm,
                plansVm = plansVm,
                vacationVm = vacationViewModel,
                onNewPlan = { navController.navigate(Dest.Preferences.route) },
                onOpenPlan = { navController.navigate(Dest.Plan.route) },
                onLoggedOut = {
                    // Wystarczy signOut + nawigacja.
                    // plansVm.stop() wykona się przy wyjściu z MyPlansScreen (onDispose).
                    authVm.signOut()
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Dest.Preferences.route) {
            PreferencesScreen(
                viewModel = vacationViewModel,
                onNext = {
                    vacationViewModel.prepareDestinationSuggestions()
                    navController.navigate(Dest.Destinations.route)
                },
                onGoMyPlans = {
                    navController.navigate(Dest.MyPlans.route) {
                        launchSingleTop = true
                        popUpTo(Dest.MyPlans.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Dest.Destinations.route) {
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
                onGoMyPlans = {
                    val popped = navController.popBackStack(Dest.MyPlans.route, inclusive = false)
                    if (!popped) {
                        navController.navigate(Dest.MyPlans.route) { launchSingleTop = true }
                    }
                }
            )
        }
    }
}
