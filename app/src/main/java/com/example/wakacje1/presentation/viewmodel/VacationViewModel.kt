package com.example.wakacje1.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.R
import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
import com.example.wakacje1.domain.usecase.GeneratePlanUseCase
import com.example.wakacje1.domain.usecase.LoadForecastForTripUseCase
import com.example.wakacje1.domain.usecase.LoadLatestLocalPlanUseCase
import com.example.wakacje1.domain.usecase.LoadWeatherUseCase
import com.example.wakacje1.domain.usecase.RegenerateDayUseCase
import com.example.wakacje1.domain.usecase.RollNewActivityUseCase
import com.example.wakacje1.domain.usecase.SavePlanLocallyUseCase
import com.example.wakacje1.domain.usecase.SuggestDestinationsUseCase
import com.example.wakacje1.domain.usecase.TransportPass
import com.example.wakacje1.presentation.common.AppError
import com.example.wakacje1.presentation.common.ErrorMapper
import com.example.wakacje1.presentation.common.UiEvent
import com.example.wakacje1.presentation.common.UiText
import com.example.wakacje1.util.DateUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VacationViewModel(
    private val destinationRepository: DestinationRepository,
    private val savePlanLocallyUseCase: SavePlanLocallyUseCase,
    private val loadLatestLocalPlanUseCase: LoadLatestLocalPlanUseCase,
    private val suggestDestinationsUseCase: SuggestDestinationsUseCase,
    // UseCases Logiki Planu
    private val generatePlanUseCase: GeneratePlanUseCase,
    private val regenerateDayUseCase: RegenerateDayUseCase,
    private val rollNewActivityUseCase: RollNewActivityUseCase,
    // Engine
    private val planGenerator: PlanGenerator,
    // UseCases Pogodowe
    private val loadWeatherUseCase: LoadWeatherUseCase,
    private val loadForecastForTripUseCase: LoadForecastForTripUseCase
) : ViewModel() {

    // --- STATE FLOW ---
    private val _uiState = MutableStateFlow(VacationUiState())
    val uiState: StateFlow<VacationUiState> = _uiState.asStateFlow()

    // --- Events ---
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    // --- Stan wewnętrzny ---
    private var internalPlanDays: MutableList<InternalDayPlan> = mutableListOf()
    private var currentPlanId: String? = null
    private var currentCreatedAtMillis: Long? = null

    // --- Helpers ---
    private fun postMessage(msg: UiText) {
        viewModelScope.launch { _events.emit(UiEvent.Message(msg)) }
    }

    // POPRAWIONA FUNKCJA postError
    private fun postError(t: Throwable, fallback: UiText) {
        // 1. Używamy ErrorMapper, żeby rozpoznać typ błędu (np. brak sieci, timeout).
        // Przekazujemy pusty string jako fallback do mappera, bo i tak go nadpiszemy poniżej.
        val mappedError = ErrorMapper.map(t, "")

        // 2. Jeśli mapper zwrócił "Unknown" (nie rozpoznał typu),
        // to wstawiamy nasz konkretny komunikat (fallback z argumentu) zamiast ogólnego tekstu.
        val finalError = if (mappedError is AppError.Unknown) {
            AppError.Unknown(fallback = fallback, tech = t.message)
        } else {
            // Jeśli to konkretny błąd (np. Network), zostawiamy go jak jest
            mappedError
        }

        // 3. Emitujemy event z obiektem AppError
        viewModelScope.launch {
            _events.emit(UiEvent.Error(finalError))
        }
    }

    fun clearUiMessage() {
        _uiState.update { it.copy(uiMessage = null) }
    }

    // --- Główne Metody ---

    fun updatePreferences(prefs: Preferences) {
        internalPlanDays.clear()
        currentPlanId = null
        currentCreatedAtMillis = null

        _uiState.update {
            VacationUiState(
                preferences = prefs,
                isLoading = false
            )
        }
    }

    fun prepareDestinationSuggestions() {
        val prefs = _uiState.value.preferences ?: run {
            _uiState.update { it.copy(destinationSuggestions = emptyList()) }
            return
        }

        val result = suggestDestinationsUseCase.execute(prefs)

        _uiState.update {
            it.copy(
                destinationSuggestions = result.suggestions,
                lastTransportPass = result.transportPass
            )
        }
    }

    fun chooseDestination(destination: Destination) {
        internalPlanDays.clear()
        currentPlanId = null
        currentCreatedAtMillis = null

        _uiState.update {
            it.copy(
                chosenDestination = destination,
                plan = emptyList(),
                dayWeatherByDate = emptyMap(),
                forecastNotice = null,
                canEditPlan = true
            )
        }

        if (destination.apiQuery.isNotBlank()) {
            loadWeatherForCity(destination.apiQuery)
            loadForecastForTrip()
        }
    }

    fun generatePlan() {
        val currentState = _uiState.value
        val prefs = currentState.preferences ?: return
        val dest = currentState.chosenDestination ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val newInternal = generatePlanUseCase.execute(
                    prefs = prefs,
                    dest = dest,
                    isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                )
                internalPlanDays = newInternal

                val uiPlan = rebuildUiPlan()
                _uiState.update {
                    it.copy(
                        plan = uiPlan,
                        canEditPlan = true,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                postError(e, UiText.StringResource(R.string.error_generating_plan))
            }
        }
    }

    fun regenerateDay(dayIndex: Int) {
        if (!_uiState.value.canEditPlan) return
        val currentState = _uiState.value
        val prefs = currentState.preferences ?: return
        val dest = currentState.chosenDestination ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                regenerateDayUseCase.execute(
                    dayIndex = dayIndex,
                    prefs = prefs,
                    dest = dest,
                    internal = internalPlanDays,
                    isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                )
                val uiPlan = rebuildUiPlan()
                _uiState.update { it.copy(plan = uiPlan, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                postError(e, UiText.StringResource(R.string.error_regenerating_day))
            }
        }
    }

    fun rollNewActivity(dayIndex: Int, slot: DaySlot) {
        if (!_uiState.value.canEditPlan) return
        val currentState = _uiState.value
        val prefs = currentState.preferences ?: return
        val dest = currentState.chosenDestination ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                rollNewActivityUseCase.execute(
                    dayIndex = dayIndex,
                    slot = slot,
                    prefs = prefs,
                    dest = dest,
                    internal = internalPlanDays,
                    isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                )
                val uiPlan = rebuildUiPlan()
                _uiState.update { it.copy(plan = uiPlan, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                postError(e, UiText.StringResource(R.string.error_rolling_activity))
            }
        }
    }

    fun setCustomActivity(dayIndex: Int, slot: DaySlot, title: String, description: String) {
        if (!_uiState.value.canEditPlan) return
        planGenerator.setCustomSlot(dayIndex, slot, title, description, internalPlanDays)
        val uiPlan = rebuildUiPlan()
        _uiState.update { it.copy(plan = uiPlan) }
    }

    // --- Weather Logic ---

    fun loadWeatherForCity(cityQuery: String, force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(weather = WeatherUiState(loading = true)) }
            val result = loadWeatherUseCase.execute(cityQuery, force)
            _uiState.update { it.copy(weather = result) }
        }
    }

    fun loadForecastForTrip(force: Boolean = false) {
        val currentState = _uiState.value
        val prefs = currentState.preferences ?: return
        val dest = currentState.chosenDestination ?: return

        viewModelScope.launch {
            val result = loadForecastForTripUseCase.execute(prefs, dest, force)
            _uiState.update {
                it.copy(
                    dayWeatherByDate = result.byDate,
                    forecastNotice = result.notice
                )
            }
        }
    }

    private fun isBadWeatherForDayIndex(dayIndex: Int): Boolean {
        val prefs = _uiState.value.preferences ?: return false
        val startMillis = prefs.startDateMillis ?: return false

        val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
        val dayMillis = DateUtils.dayMillisForIndex(startNorm, dayIndex)

        return _uiState.value.dayWeatherByDate[dayMillis]?.isBadWeather ?: false
    }

    // --- Storage & Utils ---

    fun savePlanLocally(uid: String? = null) {
        val realUid = uid ?: FirebaseAuth.getInstance().currentUser?.uid
        if (realUid.isNullOrBlank()) {
            postMessage(UiText.StringResource(R.string.msg_no_user))
            return
        }

        val currentState = _uiState.value
        val prefs = currentState.preferences ?: return
        val dest = currentState.chosenDestination ?: return
        if (internalPlanDays.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val res = savePlanLocallyUseCase.execute(realUid, prefs, dest, internalPlanDays, currentPlanId, currentCreatedAtMillis)
                currentPlanId = res.planId
                currentCreatedAtMillis = res.createdAtMillis
                postMessage(UiText.StringResource(R.string.msg_saved))
            } catch (e: Exception) {
                postError(e, UiText.StringResource(R.string.error_saving))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadPlanLocally(uid: String? = null) {
        val realUid = uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val stored = withContext(Dispatchers.IO) { loadLatestLocalPlanUseCase.execute(realUid) }
                if (stored == null) {
                    postMessage(UiText.StringResource(R.string.msg_no_plans))
                    return@launch
                }
                applyStoredPlan(stored)
                postMessage(UiText.StringResource(R.string.msg_loaded))
            } catch (e: Exception) {
                postError(e, UiText.StringResource(R.string.error_loading))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun applyStoredPlan(stored: StoredPlan) {
        internalPlanDays = stored.internalDays.map { it.toInternalDayPlan() }.toMutableList()
        currentPlanId = stored.id
        currentCreatedAtMillis = stored.createdAtMillis

        val uiPlan = planGenerator.rebuildDayPlans(internalPlanDays, stored.destination.displayName)

        _uiState.update {
            VacationUiState(
                preferences = stored.preferences?.toPreferences(),
                chosenDestination = stored.destination.toDestination(),
                plan = uiPlan,
                weather = WeatherUiState(),
                canEditPlan = true,
                isLoading = false
            )
        }
    }

    fun exportCurrentPlanToPdf() {
        val currentState = _uiState.value
        val destName = currentState.chosenDestination?.displayName
        val tripStart = currentState.preferences?.startDateMillis

        if (destName.isNullOrBlank() || internalPlanDays.isEmpty()) {
            postMessage(UiText.StringResource(R.string.msg_no_plan_to_export))
            return
        }
        viewModelScope.launch {
            _events.emit(UiEvent.ExportPdf(destName, tripStart, internalPlanDays.toList()))
        }
    }

    // --- Getters dla UI ---

    fun getInternalDayOrNull(dayIndex: Int) = internalPlanDays.getOrNull(dayIndex)

    fun getSlotOrNull(dayIndex: Int, slot: DaySlot): SlotPlan? {
        val d = internalPlanDays.getOrNull(dayIndex) ?: return null
        return when (slot) {
            DaySlot.MORNING -> d.morning
            DaySlot.MIDDAY -> d.midday
            DaySlot.EVENING -> d.evening
        }
    }

    fun getDayWeatherForIndexGetter(dayIndex: Int): com.example.wakacje1.presentation.viewmodel.DayWeatherUi? {
        val prefs = _uiState.value.preferences ?: return null
        val startMillis = prefs.startDateMillis ?: return null
        val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
        val key = DateUtils.dayMillisForIndex(startNorm, dayIndex)
        return _uiState.value.dayWeatherByDate[key]
    }

    fun getBudgetPerDayWithTransport(d: Destination): Int {
        val prefs = _uiState.value.preferences ?: return 0
        val days = prefs.days.coerceAtLeast(1)
        val t = getTransportCostUsedForSuggestions(d)
        val remaining = prefs.budget - t
        return if (remaining <= 0) 0 else remaining / days
    }

    fun getTransportCostUsedForSuggestions(d: Destination): Int {
        return when (_uiState.value.lastTransportPass) {
            TransportPass.T_MAX -> d.transportCostRoundTripPlnMax
            TransportPass.T_AVG -> d.transportCostRoundTripPlnAvg
            TransportPass.T_MIN -> d.transportCostRoundTripPlnMin
        }
    }

    fun getTransportScenarioLabel(): UiText {
        return when (_uiState.value.lastTransportPass) {
            TransportPass.T_MAX -> UiText.StringResource(R.string.transport_label_max)
            TransportPass.T_AVG -> UiText.StringResource(R.string.transport_label_avg)
            TransportPass.T_MIN -> UiText.StringResource(R.string.transport_label_min)
        }
    }

    fun moveDayUp(index: Int) {
        if (!_uiState.value.canEditPlan || index <= 0 || index >= internalPlanDays.size) return
        val tmp = internalPlanDays[index - 1]
        internalPlanDays[index - 1] = internalPlanDays[index]
        internalPlanDays[index] = tmp

        val uiPlan = rebuildUiPlan()
        _uiState.update { it.copy(plan = uiPlan) }
    }

    fun moveDayDown(index: Int) {
        if (!_uiState.value.canEditPlan || index < 0 || index >= internalPlanDays.size - 1) return
        val tmp = internalPlanDays[index + 1]
        internalPlanDays[index + 1] = internalPlanDays[index]
        internalPlanDays[index] = tmp

        val uiPlan = rebuildUiPlan()
        _uiState.update { it.copy(plan = uiPlan) }
    }

    private fun rebuildUiPlan(): List<DayPlan> {
        val dest = _uiState.value.chosenDestination ?: return emptyList()
        return planGenerator.rebuildDayPlans(internalPlanDays, dest.displayName)
    }
}