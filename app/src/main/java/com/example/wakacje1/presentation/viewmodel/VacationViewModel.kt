package com.example.wakacje1.presentation.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
import com.example.wakacje1.domain.usecase.ExportPlanPdfUseCase
import com.example.wakacje1.domain.usecase.SavePlanLocallyUseCase
import com.example.wakacje1.presentation.common.ErrorMapper
import com.example.wakacje1.presentation.common.UiEvent
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

data class WeatherUiState(
    val loading: Boolean = false,
    val city: String? = null,
    val temperature: Double? = null,
    val description: String? = null,
    val error: String? = null
)

data class DayWeatherUi(
    val dateMillis: Long,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    val isBadWeather: Boolean
)

class VacationViewModel(application: Application) : AndroidViewModel(application) {

    private val destinationRepository = DestinationRepository(application)
    private val activitiesRepository = ActivitiesRepository(application)

    private val savePlanLocallyUseCase = SavePlanLocallyUseCase()
    private val exportPlanPdfUseCase = ExportPlanPdfUseCase()

    // --- events (pod snackbar/toast) ---
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private fun postMessage(msg: String) {
        viewModelScope.launch { _events.emit(UiEvent.Message(msg)) }
    }

    private fun postError(t: Throwable, fallback: String) {
        viewModelScope.launch { _events.emit(UiEvent.Error(ErrorMapper.map(t, fallback))) }
    }

    var preferences by mutableStateOf<Preferences?>(null)
        private set

    var destinationSuggestions by mutableStateOf<List<Destination>>(emptyList())
        private set

    var chosenDestination by mutableStateOf<Destination?>(null)
        private set

    var plan by mutableStateOf<List<DayPlan>>(emptyList())
        private set

    private var internalPlanDays: MutableList<InternalDayPlan> = mutableListOf()

    var canEditPlan by mutableStateOf(true)
        private set

    var weather by mutableStateOf(WeatherUiState())
        private set

    var dayWeatherByDate by mutableStateOf<Map<Long, DayWeatherUi>>(emptyMap())
        private set

    var isBlockingUi by mutableStateOf(false)
        private set

    var uiMessage by mutableStateOf<String?>(null)
        private set

    var forecastNotice by mutableStateOf<String?>(null)
        private set

    private var currentPlanId: String? = null
    private var currentCreatedAtMillis: Long? = null

    private enum class TransportPass { T_MAX, T_AVG, T_MIN }
    private var lastTransportPass by mutableStateOf(TransportPass.T_MAX)

    fun clearUiMessage() {
        uiMessage = null
    }
    fun getTransportCostUsedForSuggestions(d: Destination): Int {
        return when (lastTransportPass) {
            TransportPass.T_MAX -> d.transportCostRoundTripPlnMax
            TransportPass.T_AVG -> d.transportCostRoundTripPlnAvg
            TransportPass.T_MIN -> d.transportCostRoundTripPlnMin
        }
    }

    fun getTransportScenarioLabel(): String {
        return when (lastTransportPass) {
            TransportPass.T_MAX -> "Tmax (konserwatywnie)"
            TransportPass.T_AVG -> "Tavg (średnio)"
            TransportPass.T_MIN -> "Tmin (optymistycznie)"
        }
    }

    fun updatePreferences(prefs: Preferences) {
        preferences = prefs
        destinationSuggestions = emptyList()
        chosenDestination = null
        plan = emptyList()
        internalPlanDays = mutableListOf()
        canEditPlan = true
        weather = WeatherUiState()
        dayWeatherByDate = emptyMap()
        forecastNotice = null
        uiMessage = null
        currentPlanId = null
        currentCreatedAtMillis = null
        lastTransportPass = TransportPass.T_MAX
    }

    /**
     * NOWY DOBÓR DESTYNACJI:
     * - budżet liczony jako (budżet - transportRoundTrip)/dni
     * - 3 przebiegi: Tmax → Tavg → Tmin
     * - hard-filtry region/klimat; styl wpływa na ranking
     * - ranking bez "wag": sortowanie krotką (dopasowanie → trafienie w typical → tańszy transport)
     */
    fun prepareDestinationSuggestions() {
        val prefs = preferences ?: run {
            destinationSuggestions = emptyList()
            return
        }

        val all = destinationRepository.getAllDestinations()
        val days = prefs.days.coerceAtLeast(1)

        fun transportFor(d: Destination, pass: TransportPass): Int = when (pass) {
            TransportPass.T_MAX -> d.transportCostRoundTripPlnMax
            TransportPass.T_AVG -> d.transportCostRoundTripPlnAvg
            TransportPass.T_MIN -> d.transportCostRoundTripPlnMin
        }

        fun budgetPerDayFor(d: Destination, pass: TransportPass): Int {
            val remaining = prefs.budget - transportFor(d, pass)
            if (remaining <= 0) return 0
            return remaining / days
        }

        fun styleMatch(d: Destination): Boolean =
            d.tags.any { it.equals(prefs.style, ignoreCase = true) } ||
                    d.tags.any { it.equals("Mix", ignoreCase = true) && prefs.style.equals("Mix", true) }

        fun matchTuple(d: Destination): Triple<Int, Int, Int> {
            val regionOk = d.region.equals(prefs.region, true)
            val climateOk = d.climate.equals(prefs.climate, true)
            val styleOk = styleMatch(d)
            // 3..0 – im wyżej tym lepiej
            val lvl = (if (regionOk) 1 else 0) + (if (climateOk) 1 else 0) + (if (styleOk) 1 else 0)
            return Triple(lvl, if (regionOk) 1 else 0, if (climateOk) 1 else 0)
        }

        data class Candidate(val d: Destination, val pass: TransportPass, val bpd: Int)

        fun computeForPass(pass: TransportPass): List<Destination> {
            val strict = all.filter { it.region.equals(prefs.region, true) && it.climate.equals(prefs.climate, true) }
            val relaxed1 = all.filter { it.region.equals(prefs.region, true) } // luzujemy klimat
            val relaxed2 = all // luzujemy wszystko (awaryjnie)

            fun rank(list: List<Destination>): List<Destination> {
                val cands = list.map { d -> Candidate(d, pass, budgetPerDayFor(d, pass)) }

                // najpierw "stać mnie" (minBudgetPerDay), ale jak będzie za mało wyników,
                // dopełnimy najtańszymi minBudgetPerDay
                val ok = cands.filter { it.bpd >= it.d.minBudgetPerDay }
                val fill = cands.filter { it.bpd < it.d.minBudgetPerDay }
                    .sortedBy { it.d.minBudgetPerDay } // dopełniamy możliwie "najbliżej"

                val merged = (ok + fill).distinctBy { it.d.id }

                return merged
                    .sortedWith(
                        compareByDescending<Candidate> { matchTuple(it.d).first } // ile dopasowań
                            .thenBy { abs(it.bpd - it.d.typicalBudgetPerDay) }   // trafienie w typical
                            .thenBy { it.d.transportCostRoundTripPlnAvg }        // tańszy transport
                            .thenBy { it.d.displayName }
                    )
                    .map { it.d }
                    .take(3)
            }

            // hard -> luzowanie, aż uzbieramy 3
            val r1 = rank(strict)
            if (r1.size >= 3) return r1

            val r2 = (r1 + rank(relaxed1)).distinctBy { it.id }.take(3)
            if (r2.size >= 3) return r2

            return (r2 + rank(relaxed2)).distinctBy { it.id }.take(3)
        }

        // 3 przebiegi: Tmax -> Tavg -> Tmin
        val p1 = computeForPass(TransportPass.T_MAX)
        if (p1.size >= 3) {
            lastTransportPass = TransportPass.T_MAX
            destinationSuggestions = p1
            return
        }

        val p2 = computeForPass(TransportPass.T_AVG)
        if (p2.size >= 3) {
            lastTransportPass = TransportPass.T_AVG
            destinationSuggestions = p2
            return
        }

        val p3 = computeForPass(TransportPass.T_MIN)
        lastTransportPass = TransportPass.T_MIN
        destinationSuggestions = p3
    }

    /**
     * Przydaje się w UI, żeby sprawdzać budżet "uczciwie" (z transportem).
     */
    fun getBudgetPerDayWithTransport(d: Destination): Int {
        val prefs = preferences ?: return 0
        val days = prefs.days.coerceAtLeast(1)
        val t = when (lastTransportPass) {
            TransportPass.T_MAX -> d.transportCostRoundTripPlnMax
            TransportPass.T_AVG -> d.transportCostRoundTripPlnAvg
            TransportPass.T_MIN -> d.transportCostRoundTripPlnMin
        }
        val remaining = prefs.budget - t
        if (remaining <= 0) return 0
        return remaining / days
    }

    fun chooseDestination(destination: Destination) {
        chosenDestination = destination
        plan = emptyList()
        internalPlanDays = mutableListOf()
        canEditPlan = true
        dayWeatherByDate = emptyMap()
        forecastNotice = null
        currentPlanId = null
        currentCreatedAtMillis = null

        if (destination.apiQuery.isNotBlank()) {
            loadWeatherForCity(destination.apiQuery)
            loadForecastForTrip()
        }
    }

    fun loadWeatherForCity(cityQuery: String, force: Boolean = false) {
        viewModelScope.launch {
            weather = WeatherUiState(loading = true)
            try {
                val r = WeatherRepository.getWeatherForCity(cityQuery, forceRefresh = force)
                weather = WeatherUiState(
                    loading = false,
                    city = r.city,
                    temperature = r.temperature,
                    description = r.description
                )
            } catch (e: Exception) {
                weather = WeatherUiState(
                    loading = false,
                    error = ErrorMapper.userMessage(e, "Nie udało się pobrać pogody.")
                )
            }
        }
    }

    fun loadForecastForTrip(force: Boolean = false) {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return
        val startMillis = prefs.startDateMillis ?: return
        val days = prefs.days.coerceAtLeast(1)

        viewModelScope.launch {
            try {
                val forecast = WeatherRepository.getForecastForCity(dest.apiQuery, forceRefresh = force)
                val byDate = forecast.associateBy { it.dateMillis }

                val oneDay = 24L * 60 * 60 * 1000L
                val startNorm = normalizeToLocalMidnight(startMillis)

                val map = mutableMapOf<Long, DayWeatherUi>()
                var covered = 0

                for (i in 0 until days) {
                    val dayMillis = startNorm + oneDay * i
                    val f = byDate[dayMillis]
                    if (f != null) covered++

                    map[dayMillis] = DayWeatherUi(
                        dateMillis = dayMillis,
                        tempMin = f?.tempMin,
                        tempMax = f?.tempMax,
                        description = f?.description,
                        isBadWeather = f?.isBadWeather ?: false
                    )
                }

                dayWeatherByDate = map

                forecastNotice = when {
                    covered == 0 -> "Prognoza dzienna jest niedostępna dla tego terminu (API ma ograniczony horyzont)."
                    covered < days -> "Prognoza dzienna dostępna tylko dla części wyjazdu: $covered/$days dni (ograniczenie API)."
                    else -> null
                }
            } catch (_: Exception) {
                dayWeatherByDate = emptyMap()
                forecastNotice = "Nie udało się pobrać prognozy dziennej."
            }
        }
    }

    private fun isBadWeatherForDayIndex(dayIndex: Int): Boolean {
        val prefs = preferences ?: return false
        val startMillis = prefs.startDateMillis ?: return false

        val oneDay = 24L * 60 * 60 * 1000L
        val startNorm = normalizeToLocalMidnight(startMillis)
        val dayMillis = startNorm + oneDay * dayIndex

        return dayWeatherByDate[dayMillis]?.isBadWeather ?: false
    }

    fun getInternalDayOrNull(dayIndex: Int): InternalDayPlan? =
        internalPlanDays.getOrNull(dayIndex)

    fun getSlotOrNull(dayIndex: Int, slot: DaySlot): SlotPlan? {
        val d = internalPlanDays.getOrNull(dayIndex) ?: return null
        return when (slot) {
            DaySlot.MORNING -> d.morning
            DaySlot.MIDDAY -> d.midday
            DaySlot.EVENING -> d.evening
        }
    }

    fun getDayWeatherForIndex(dayIndex: Int): DayWeatherUi? {
        val prefs = preferences ?: return null
        val startMillis = prefs.startDateMillis ?: return null
        val startNorm = normalizeToLocalMidnight(startMillis)
        val oneDay = 24L * 60 * 60 * 1000L
        val key = startNorm + oneDay * dayIndex
        return dayWeatherByDate[key]
    }

    fun generatePlan() {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val allActivities = withContext(Dispatchers.IO) { activitiesRepository.getAllActivities() }

                val newInternal = withContext(Dispatchers.Default) {
                    PlanGenerator.generateInternalPlan(
                        prefs = prefs,
                        dest = dest,
                        allActivities = allActivities,
                        isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                    )
                }

                internalPlanDays = newInternal
                canEditPlan = true
                plan = rebuildUiPlan()
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun regenerateDay(dayIndex: Int) {
        if (!canEditPlan) return
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val allActivities = withContext(Dispatchers.IO) { activitiesRepository.getAllActivities() }

                withContext(Dispatchers.Default) {
                    PlanGenerator.regenerateWholeDay(
                        dayIndex = dayIndex,
                        prefs = prefs,
                        dest = dest,
                        allActivities = allActivities,
                        internal = internalPlanDays,
                        isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                    )
                }

                plan = rebuildUiPlan()
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun moveDayUp(index: Int) {
        if (!canEditPlan) return
        if (index <= 0 || index >= internalPlanDays.size) return
        val tmp = internalPlanDays[index - 1]
        internalPlanDays[index - 1] = internalPlanDays[index]
        internalPlanDays[index] = tmp
        plan = rebuildUiPlan()
    }

    fun moveDayDown(index: Int) {
        if (!canEditPlan) return
        if (index < 0 || index >= internalPlanDays.size - 1) return
        val tmp = internalPlanDays[index + 1]
        internalPlanDays[index + 1] = internalPlanDays[index]
        internalPlanDays[index] = tmp
        plan = rebuildUiPlan()
    }

    fun rollNewActivity(dayIndex: Int, slot: DaySlot) {
        if (!canEditPlan) return
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val allActivities = withContext(Dispatchers.IO) { activitiesRepository.getAllActivities() }

                withContext(Dispatchers.Default) {
                    PlanGenerator.rollNewSlot(
                        dayIndex = dayIndex,
                        slot = slot,
                        prefs = prefs,
                        dest = dest,
                        allActivities = allActivities,
                        internal = internalPlanDays,
                        isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                    )
                }

                plan = rebuildUiPlan()
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun setCustomActivity(dayIndex: Int, slot: DaySlot, title: String, description: String) {
        if (!canEditPlan) return
        PlanGenerator.setCustomSlot(
            dayIndex = dayIndex,
            slot = slot,
            title = title,
            description = description,
            internal = internalPlanDays
        )
        plan = rebuildUiPlan()
    }

    fun savePlanLocally(uid: String? = null) {
        val ctx = getApplication<Application>()
        val realUid = uid ?: FirebaseAuth.getInstance().currentUser?.uid
        if (realUid.isNullOrBlank()) {
            uiMessage = "Brak zalogowanego użytkownika."
            postMessage("Brak zalogowanego użytkownika.")
            return
        }

        val prefs = preferences ?: run { uiMessage = "Brak preferencji."; postMessage("Brak preferencji."); return }
        val dest = chosenDestination ?: run { uiMessage = "Brak wybranego miejsca."; postMessage("Brak wybranego miejsca."); return }
        if (internalPlanDays.isEmpty()) { uiMessage = "Brak planu do zapisu."; postMessage("Brak planu do zapisu."); return }

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val res = savePlanLocallyUseCase.execute(
                    ctx = ctx,
                    uid = realUid,
                    prefs = prefs,
                    dest = dest,
                    internalDays = internalPlanDays,
                    currentPlanId = currentPlanId,
                    currentCreatedAtMillis = currentCreatedAtMillis
                )

                currentPlanId = res.planId
                currentCreatedAtMillis = res.createdAtMillis

                uiMessage = if (res.cloudError == null) {
                    "Zapisano (lokalnie + chmura)."
                } else {
                    "Zapisano lokalnie. Chmura: ${ErrorMapper.userMessage(res.cloudError, "Błąd synchronizacji.")}"
                }

                postMessage(uiMessage ?: "Zapisano.")
                res.cloudError?.let { postError(it, "Nie udało się zsynchronizować z chmurą.") }

            } catch (e: Exception) {
                uiMessage = "Błąd zapisu: ${ErrorMapper.userMessage(e, "Błąd zapisu.")}"
                postError(e, "Nie udało się zapisać planu.")
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun loadPlanLocally(uid: String? = null) {
        val ctx = getApplication<Application>()
        val realUid = uid ?: FirebaseAuth.getInstance().currentUser?.uid
        if (realUid.isNullOrBlank()) {
            uiMessage = "Brak zalogowanego użytkownika."
            postMessage("Brak zalogowanego użytkownika.")
            return
        }

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val stored = withContext(Dispatchers.IO) {
                    com.example.wakacje1.data.local.PlanStorage.loadLatestPlan(ctx, realUid)
                }

                if (stored == null) {
                    uiMessage = "Brak zapisanych planów lokalnie."
                    postMessage("Brak zapisanych planów.")
                    return@launch
                }

                applyStoredPlan(stored)
                uiMessage = "Wczytano ostatni plan lokalny."
                postMessage("Wczytano plan.")
            } catch (e: Exception) {
                uiMessage = "Błąd wczytania: ${ErrorMapper.userMessage(e, "Błąd wczytania.")}"
                postError(e, "Nie udało się wczytać planu.")
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun applyStoredPlan(stored: StoredPlan) {
        preferences = stored.preferences?.toPreferences()
        chosenDestination = stored.destination.toDestination()
        internalPlanDays = stored.internalDays.map { it.toInternalDayPlan() }.toMutableList()

        currentPlanId = stored.id
        currentCreatedAtMillis = stored.createdAtMillis

        canEditPlan = true
        plan = PlanGenerator.rebuildDayPlans(internalPlanDays, chosenDestination?.displayName ?: "")

        weather = WeatherUiState()
        dayWeatherByDate = emptyMap()
        forecastNotice = null
        uiMessage = "Wczytano plan."
    }

    fun exportCurrentPlanToPdf(activity: Activity) {
        val destName = chosenDestination?.displayName
        val tripStart = preferences?.startDateMillis
        val currentPlan = plan

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val msg = exportPlanPdfUseCase.execute(
                    activity = activity,
                    destinationName = destName,
                    tripStartDateMillis = tripStart,
                    plan = currentPlan
                )
                uiMessage = msg
                postMessage(msg)
            } catch (e: Exception) {
                uiMessage = "Błąd PDF: ${ErrorMapper.userMessage(e, "Nie udało się utworzyć PDF.")}"
                postError(e, "Nie udało się utworzyć PDF.")
            } finally {
                isBlockingUi = false
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }


    private fun rebuildUiPlan(): List<DayPlan> {
        val dest = chosenDestination ?: return emptyList()
        return PlanGenerator.rebuildDayPlans(internalPlanDays, dest.displayName)
    }

    private fun normalizeToLocalMidnight(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
