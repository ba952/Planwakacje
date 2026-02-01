package com.example.wakacje1.di

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.remote.AuthRepository
import com.example.wakacje1.data.remote.PlansCloudRepository
import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.domain.engine.PlanGenerator
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

/**
 * Główny moduł Dependency Injection (DI) oparty na bibliotece Koin.
 * Definiuje sposób tworzenia i dostarczania zależności w całej aplikacji.
 * Podział na sekcje ułatwia zarządzanie grafem zależności.
 */
val appModule = module {

    // --- 1. ZALEŻNOŚCI ZEWNĘTRZNE (Third-party) ---
    // Instancje klas z bibliotek zewnętrznych (np. Firebase), których nie modyfikujemy.
    single { FirebaseAuth.getInstance() }

    // --- 2. REPOZYTORIA (Data Layer) ---
    // Definiowane jako 'single' (Singleton), ponieważ przechowują stan (cache)
    // lub są kosztowne w tworzeniu (połączenia sieciowe/baza danych).
    single { DestinationRepository(androidContext()) }
    single { ActivitiesRepository(androidContext()) }
    single { AuthRepository(firebaseAuth = get()) }
    single { PlansLocalRepository(context = androidContext()) }
    single { PlansCloudRepository() }
    single { WeatherRepository() }

    // --- 3. HELPERY / UTILS ---
    // Implementacja interfejsu StringProvider, który pozwala warstwie domeny (czystej Kotlin)
    // na dostęp do zasobów Androida (R.string) bez bezpośredniej zależności od Contextu.
    single<StringProvider> {
        object : StringProvider {
            private val context = androidContext()

            override fun getString(resId: Int, vararg args: Any): String {
                return if (args.isNotEmpty()) {
                    context.getString(resId, *args)
                } else {
                    context.getString(resId)
                }
            }
        }
    }

    // --- 4. DOMENA / USE CASES (Business Logic) ---
    // Definiowane jako 'factory', co oznacza, że przy każdym wstrzyknięciu tworzona jest nowa instancja.
    // Jest to bezpieczne, ponieważ UseCase'y są zazwyczaj bezstanowe (stateless).

    // Silnik generowania planu (Core Engine)
    factory { PlanGenerator(stringProvider = get()) }

    // Eksporter PDF
    factory { ExportPlanPdfUseCase() }

    // Przypadki użycia związane z planowaniem
    factory { GeneratePlanUseCase(activitiesRepository = get(), planGenerator = get()) }
    factory { RegenerateDayUseCase(activitiesRepository = get(), planGenerator = get()) }
    factory { RollNewActivityUseCase(activitiesRepository = get(), planGenerator = get()) }

    // Przypadki użycia związane z pogodą
    factory { LoadWeatherUseCase(weatherRepository = get()) }
    factory { LoadForecastForTripUseCase(weatherRepository = get()) }

    // Pozostałe przypadki użycia (Sugestie, Zapis, Walidacja)
    factory { SuggestDestinationsUseCase(destinationRepository = get()) }
    factory { SavePlanLocallyUseCase(localRepository = get(), cloudRepository = get()) }
    factory { LoadLatestLocalPlanUseCase(localRepository = get()) }
    factory { ValidatePasswordUseCase() }

    // --- 5. VIEW MODELS (Presentation Layer) ---
    // Koin 'viewModel' dba o integrację z Android Architecture Components (ViewModelProvider),
    // dzięki czemu ViewModele przetrwają zmiany konfiguracji (obrót ekranu).

    viewModel {
        AuthViewModel(
            authRepository = get(),
            validatePasswordUseCase = get()
        )
    }

    viewModel { MyPlansViewModel(localRepository = get(), cloudRepository = get()) }

    // Główny ViewModel aplikacji - agreguje większość logiki biznesowej
    viewModel {
        VacationViewModel(
            destinationRepository = get(),
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