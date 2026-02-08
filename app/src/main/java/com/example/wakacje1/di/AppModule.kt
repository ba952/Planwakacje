package com.example.wakacje1.di

import com.example.wakacje1.data.assets.ActivitiesRepository as ActivitiesRepositoryImpl
import com.example.wakacje1.data.assets.DestinationRepository as DestinationRepositoryImpl
import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.remote.AuthRepository
import com.example.wakacje1.data.remote.PlansCloudRepository
import com.example.wakacje1.data.remote.WeatherRepository as WeatherRepositoryImpl
import com.example.wakacje1.domain.assets.ActivitiesRepository as ActivitiesRepositoryContract
import com.example.wakacje1.domain.assets.DestinationRepository as DestinationRepositoryContract
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.session.FirebaseSessionProvider
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
import com.example.wakacje1.domain.weather.WeatherRepository as WeatherRepositoryContract
import com.example.wakacje1.presentation.vacation.VacationExportManager
import com.example.wakacje1.presentation.vacation.VacationPersistenceManager
import com.example.wakacje1.presentation.vacation.VacationPlanEditor
import com.example.wakacje1.presentation.vacation.VacationWeatherManager
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

    // --- 2. SESSION ---
    single<SessionProvider> { FirebaseSessionProvider(firebaseAuth = get()) }

    // --- 3. REPOZYTORIA ---
    // B2: domain -> interface, data.assets -> implementacja
    single<DestinationRepositoryContract> { DestinationRepositoryImpl(androidContext()) }
    single<ActivitiesRepositoryContract> { ActivitiesRepositoryImpl(androidContext()) }

    single { AuthRepository(firebaseAuth = get()) }
    single { PlansLocalRepository(context = androidContext()) }
    single { PlansCloudRepository() }

    // B1: domain -> interface, data.remote -> implementacja
    single<WeatherRepositoryContract> { WeatherRepositoryImpl() }

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
    factory { ExportPlanPdfUseCase(stringProvider = get()) }

    factory { GeneratePlanUseCase(activitiesRepository = get(), planGenerator = get()) }
    factory { RegenerateDayUseCase(activitiesRepository = get(), planGenerator = get()) }
    factory { RollNewActivityUseCase(activitiesRepository = get(), planGenerator = get()) }

    factory { LoadWeatherUseCase(weatherRepository = get()) }
    factory { LoadForecastForTripUseCase(weatherRepository = get()) }

    factory { SuggestDestinationsUseCase(destinationRepository = get()) }
    factory { SavePlanLocallyUseCase(localRepository = get(), cloudRepository = get()) }
    factory { LoadLatestLocalPlanUseCase(localRepository = get()) }
    factory { ValidatePasswordUseCase() }

    // --- 5.5 FEATURE (P1): MANAGEROWIE/DELEGACIJE ---
    factory {
        VacationPlanEditor(
            generatePlanUseCase = get(),
            regenerateDayUseCase = get(),
            rollNewActivityUseCase = get(),
            planGenerator = get()
        )
    }

    factory {
        VacationWeatherManager(
            loadWeatherUseCase = get(),
            loadForecastForTripUseCase = get()
        )
    }

    factory {
        VacationPersistenceManager(
            sessionProvider = get(),
            savePlanLocallyUseCase = get(),
            loadLatestLocalPlanUseCase = get(),
            planGenerator = get()
        )
    }

    factory { VacationExportManager(exportPlanPdfUseCase = get()) }

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
            suggestDestinationsUseCase = get(),
            planEditor = get(),
            weatherManager = get(),
            persistenceManager = get(),
            exportManager = get()
        )
    }
}
