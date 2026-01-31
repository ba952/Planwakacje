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
    // ZMIANA: Przyjmujemy vacationViewModel z MainActivity
    // (tam został wstrzyknięty przez "by viewModel()").
    // Dzięki temu mamy jedną instancję "żyjącą" tak długo jak MainActivity.
    vacationViewModel: VacationViewModel,
    startUid: String? // Parametr opcjonalny, zgodny z wywołaniem w MainActivity
) {

    // ZMIANA: Używamy koinViewModel() zamiast viewModel().
    // Standardowe viewModel() wywaliłoby błąd, bo nie wie jak dostarczyć Repozytoria do konstruktora.
    // Koin to potrafi dzięki AppModule.
    val authVm: AuthViewModel = koinViewModel()
    val plansVm: MyPlansViewModel = koinViewModel()

    // Jeśli startUid nie jest null, można by tu np. ustawić usera w authVm,
    // ale AuthViewModel i tak inicjalizuje się z AuthRepository, więc zna stan usera.

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
                    // Jeśli jesteśmy zalogowani, startujemy synchronizację
                    user.uid.let { uid -> plansVm.start(uid) }

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
                    // Po udanym logowaniu user != null
                    authVm.user?.uid?.let { uid -> plansVm.start(uid) }

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
                    authVm.user?.uid?.let { uid -> plansVm.start(uid) }

                    navController.navigate(Dest.MyPlans.route) {
                        popUpTo(Dest.Register.route) { inclusive = true }
                    }
                },
                onGoLogin = { navController.popBackStack() }
            )
        }

        composable(Dest.MyPlans.route) {
            // jak user zrobi się null (np. wylogowanie w innym oknie / sesja padnie), wróć do logowania
            val user = authVm.user
            LaunchedEffect(user) {
                if (user == null) {
                    plansVm.stop() // Zatrzymaj nasłuchiwanie
                    navController.navigate(Dest.Login.route) {
                        popUpTo(Dest.MyPlans.route) { inclusive = true }
                    }
                }
            }

            // Upewnij się, że nasłuchiwanie jest włączone (jeśli np. wracamy z backstacka)
            LaunchedEffect(Unit) {
                user?.uid?.let { plansVm.start(it) }
            }

            MyPlansScreen(
                authVm = authVm,
                plansVm = plansVm,
                vacationVm = vacationViewModel, // Przekazujemy ten z parametru
                onNewPlan = {
                    // Resetujemy stan kreatora przed nowym planem
                    // (można dodać metodę reset() w ViewModelu, tu updatePreferences czyści część stanu)
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
                uid = authVm.user?.uid,
                onBack = { navController.popBackStack() },
                onGoMyPlans = {
                    // nie czyścimy agresywnie stosu; wracamy jeśli się da
                    val popped = navController.popBackStack(Dest.MyPlans.route, inclusive = false)
                    if (!popped) {
                        navController.navigate(Dest.MyPlans.route) { launchSingleTop = true }
                    }
                }
            )
        }
    }
}