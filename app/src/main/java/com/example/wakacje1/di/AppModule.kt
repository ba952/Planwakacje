package com.example.wakacje1.di

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.remote.AuthRepository
import com.example.wakacje1.data.remote.PlansCloudRepository
import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.usecase.GeneratePlanUseCase
import com.example.wakacje1.domain.usecase.LoadForecastForTripUseCase
import com.example.wakacje1.domain.usecase.LoadLatestLocalPlanUseCase
import com.example.wakacje1.domain.usecase.LoadWeatherUseCase
import com.example.wakacje1.domain.usecase.RegenerateDayUseCase
import com.example.wakacje1.domain.usecase.RollNewActivityUseCase
import com.example.wakacje1.domain.usecase.SavePlanLocallyUseCase
import com.example.wakacje1.domain.usecase.SuggestDestinationsUseCase
import com.example.wakacje1.presentation.viewmodel.AuthViewModel
import com.example.wakacje1.presentation.viewmodel.MyPlansViewModel
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import com.google.firebase.auth.FirebaseAuth
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // 1. ZEWNĘTRZNE
    single { FirebaseAuth.getInstance() }

    // 2. REPOZYTORIA
    single { DestinationRepository(androidContext()) }
    single { ActivitiesRepository(androidContext()) }
    single { AuthRepository(firebaseAuth = get()) }
    single { PlansLocalRepository(context = androidContext()) }
    single { PlansCloudRepository() }
    single { WeatherRepository() } // Klasa repozytorium

    // 3. LOGIKA BIZNESOWA (UseCases)
    factory { PlanGenerator() }

    factory { GeneratePlanUseCase(activitiesRepository = get(), planGenerator = get()) }
    factory { RegenerateDayUseCase(activitiesRepository = get(), planGenerator = get()) }
    factory { RollNewActivityUseCase(activitiesRepository = get(), planGenerator = get()) }

    // UseCase do prognozy (długoterminowej)
    factory { LoadForecastForTripUseCase(weatherRepository = get()) }

    // NOWOŚĆ: UseCase do pogody bieżącej
    factory { LoadWeatherUseCase(weatherRepository = get()) }

    factory { SuggestDestinationsUseCase(destinationRepository = get()) }
    factory { SavePlanLocallyUseCase(localRepository = get(), cloudRepository = get()) }
    factory { LoadLatestLocalPlanUseCase(localRepository = get()) }

    // 4. VIEW MODELS
    viewModel { AuthViewModel(authRepository = get()) }
    viewModel { MyPlansViewModel(localRepository = get(), cloudRepository = get()) }

    viewModel {
        VacationViewModel(
            destinationRepository = get(),
            savePlanLocallyUseCase = get(),
            loadLatestLocalPlanUseCase = get(),
            suggestDestinationsUseCase = get(),
            generatePlanUseCase = get(),
            regenerateDayUseCase = get(),
            rollNewActivityUseCase = get(),
            planGenerator = get(),
            // ZAMIANA: Zamiast weatherRepository -> LoadWeatherUseCase
            loadWeatherUseCase = get(),
            loadForecastForTripUseCase = get()
        )
    }
}