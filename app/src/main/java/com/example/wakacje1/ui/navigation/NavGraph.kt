package com.example.wakacje1.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.koin.androidx.compose.koinViewModel // ZMIANA: Import dla Koin

/**
 * Definicja tras (routes) dostępnych w aplikacji przy użyciu bezpiecznych obiektów danych.
 */
sealed class Dest(val route: String) {
    data object Splash : Dest("splash")
    data object Login : Dest("login")
    data object Register : Dest("register")
    data object MyPlans : Dest("my_plans")
    data object Preferences : Dest("preferences")
    data object Destinations : Dest("destinations")
    data object Plan : Dest("plan")
}

/**
 * Główny graf nawigacyjny aplikacji.
 * Zarządza przejściami między ekranami i wstrzykiwaniem odpowiednich ViewModeli.
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    // VacationViewModel jest przekazywany z MainActivity, aby zachować stan
    // podczas całego procesu tworzenia planu (Wizard flow).
    vacationViewModel: VacationViewModel,
    startUid: String?
) {

    // Pobieranie ViewModeli zarządzanych przez Koin.
    // Pozwala to na automatyczne wstrzyknięcie wymaganych repozytoriów.
    val authVm: AuthViewModel = koinViewModel()
    val plansVm: MyPlansViewModel = koinViewModel()

    NavHost(
        navController = navController,
        startDestination = Dest.Splash.route
    ) {

        // Ekran powitalny decydujący o przekierowaniu na logowanie lub listę planów
        composable(Dest.Splash.route) {
            val user = authVm.user
            LaunchedEffect(user) {
                if (user == null) {
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.Splash.route) { inclusive = true }
                    }
                } else {
                    // Automatyczny start synchronizacji planów po wykryciu aktywnej sesji
                    user.uid.let { uid -> plansVm.start(uid) }

                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.Splash.route) { inclusive = true }
                    }
                }
            }
        }

        // Ekran logowania - po sukcesie inicjuje nasłuchiwanie planów w chmurze
        composable(Dest.Login.route) {
            LoginScreen(
                authVm = authVm,
                onDone = {
                    authVm.user?.uid?.let { uid -> plansVm.start(uid) }

                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.Login.route) { inclusive = true }
                    }
                },
                onGoRegister = { navController.navigate(Dest.Register.route) }
            )
        }

        // Ekran rejestracji nowego użytkownika
        composable(Dest.Register.route) {
            RegisterScreen(
                authVm = authVm,
                onDone = {
                    authVm.user?.uid?.let { uid -> plansVm.start(uid) }

                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.Register.route) { inclusive = true }
                    }
                },
                onGoLogin = { navController.popBackStack() }
            )
        }

        // Główny ekran użytkownika z listą zapisanych planów
        composable(Dest.MyPlans.route) {
            val user = authVm.user
            // Reagowanie na nagłe wygaśnięcie sesji lub wylogowanie
            LaunchedEffect(user) {
                if (user == null) {
                    plansVm.stop()
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                    }
                }
            }

            // Zapewnienie ciągłości nasłuchiwania zmian przy powrocie na ten ekran
            LaunchedEffect(Unit) {
                user?.uid?.let { plansVm.start(it) }
            }

            MyPlansScreen(
                authVm = authVm,
                plansVm = plansVm,
                vacationVm = vacationViewModel,
                onNewPlan = {
                    navController.navigate(Dest.Preferences.route)
                },
                onOpenPlan = { navController.navigate(Dest.Plan.route) },
                onLoggedOut = {
                    plansVm.stop()
                    authVm.signOut()
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                    }
                }
            )
        }

        // Krok 1 kreatora: Konfiguracja preferencji wyjazdu
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

        // Krok 2 kreatora: Wybór sugerowanej destynacji
        composable(Dest.Destinations.route) {
            DestinationSelectionScreen(
                viewModel = vacationViewModel,
                onDestinationChosen = { navController.navigate(Dest.Plan.route) },
                onBack = { navController.popBackStack() }
            )
        }

        // Krok 3 kreatora: Podgląd i edycja wygenerowanego planu
        composable(Dest.Plan.route) {
            PlanScreen(
                viewModel = vacationViewModel,
                uid = authVm.user?.uid,
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