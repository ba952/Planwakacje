package com.example.wakacje1.di

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.remote.AuthRepository
import com.example.wakacje1.data.remote.PlansCloudRepository
import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.data.session.FirebaseSessionProvider
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.session.SessionProvider
import com.example.wakacje1.domain.usecase.ExportPlanPdfUseCase
import com.example.wakacje1.domain.usecase.GeneratePlanUseCase
import com.example.wakacje1.domain.usecase.LoadForecastForTripUseCase
import com.example.wakacje1.domain.usecase.LoadLatestLocalPlanUseCase
import com.example.wakacje1.domain.usecase.LoadWeatherUseCase
import com.example.wakacje1.domain.usecase.RegenerateDayUseCase
import com.example.wakacje1.domain.usecase.RollNewActivityUseCase
import com.example.wakacje1.domain.usecase.SavePlanLocallyUseCase
import com.example.wakacje1.domain.usecase.SuggestDestinationsUseCase
import com.example.wakacje1.domain.usecase.ValidatePasswordUseCase
import com.example.wakacje1.domain.util.StringProvider
import com.example.wakacje1.presentation.viewmodel.AuthViewModel
import com.example.wakacje1.presentation.viewmodel.MyPlansViewModel
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import com.google.firebase.auth.FirebaseAuth
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // --- 1. ZALEŻNOŚCI ZEWNĘTRZNE ---
    single { FirebaseAuth.getInstance() }

    // --- 2. SESSION (abstrakcja) ---
    single<SessionProvider> { FirebaseSessionProvider(firebaseAuth = get()) }

    // --- 3. REPOZYTORIA (Data Layer) ---
    single { DestinationRepository(androidContext()) }
    single { ActivitiesRepository(androidContext()) }
    single { AuthRepository(firebaseAuth = get()) }
    single { PlansLocalRepository(context = androidContext()) }
    single { PlansCloudRepository() }

    // ✅ TU POPRAWKA: bez api/apiKey w konstruktorze
    single { WeatherRepository() }

    // --- 4. HELPERY / UTILS ---
    single<StringProvider> {
        object : StringProvider {
            private val context = androidContext()
            override fun getString(resId: Int, vararg args: Any): String {
                return if (args.isNotEmpty()) context.getString(resId, *args) else context.getString(resId)
            }
        }
    }

    // --- 5. DOMENA / USE CASES ---
    factory { PlanGenerator(stringProvider = get()) }
    factory { ExportPlanPdfUseCase() }

    factory { GeneratePlanUseCase(activitiesRepository = get(), planGenerator = get()) }
    factory { RegenerateDayUseCase(activitiesRepository = get(), planGenerator = get()) }
    factory { RollNewActivityUseCase(activitiesRepository = get(), planGenerator = get()) }

    factory { LoadWeatherUseCase(weatherRepository = get()) }
    factory { LoadForecastForTripUseCase(weatherRepository = get()) }

    factory { SuggestDestinationsUseCase(destinationRepository = get()) }
    factory { SavePlanLocallyUseCase(localRepository = get(), cloudRepository = get()) }
    factory { LoadLatestLocalPlanUseCase(localRepository = get()) }
    factory { ValidatePasswordUseCase() }

    // --- 6. VIEW MODELS ---
    viewModel {
        AuthViewModel(
            authRepository = get(),
            validatePasswordUseCase = get()
        )
    }

    viewModel { MyPlansViewModel(localRepository = get(), cloudRepository = get()) }

    viewModel {
        VacationViewModel(
            sessionProvider = get(),
            savePlanLocallyUseCase = get(),
            loadLatestLocalPlanUseCase = get(),
            suggestDestinationsUseCase = get(),
            generatePlanUseCase = get(),
            regenerateDayUseCase = get(),
            rollNewActivityUseCase = get(),
            exportPlanPdfUseCase = get(),
            planGenerator = get(),
            loadWeatherUseCase = get(),
            loadForecastForTripUseCase = get()
        )
    }
}
