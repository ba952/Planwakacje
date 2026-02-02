package com.example.wakacje1.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.R
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
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
import com.example.wakacje1.domain.usecase.TransportPass
import com.example.wakacje1.presentation.common.AppError
import com.example.wakacje1.presentation.common.ErrorMapper
import com.example.wakacje1.presentation.common.UiEvent
import com.example.wakacje1.presentation.common.UiText
import com.example.wakacje1.util.DateUtils
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
    private val sessionProvider: SessionProvider,
    private val savePlanLocallyUseCase: SavePlanLocallyUseCase,
    private val loadLatestLocalPlanUseCase: LoadLatestLocalPlanUseCase,
    private val suggestDestinationsUseCase: SuggestDestinationsUseCase,
    private val generatePlanUseCase: GeneratePlanUseCase,
    private val regenerateDayUseCase: RegenerateDayUseCase,
    private val rollNewActivityUseCase: RollNewActivityUseCase,
    private val exportPlanPdfUseCase: ExportPlanPdfUseCase,
    private val planGenerator: PlanGenerator,
    private val loadWeatherUseCase: LoadWeatherUseCase,
    private val loadForecastForTripUseCase: LoadForecastForTripUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VacationUiState())
    val uiState: StateFlow<VacationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var internalPlanDays: MutableList<InternalDayPlan> = mutableListOf()
    private var currentPlanId: String? = null
    private var currentCreatedAtMillis: Long? = null

    private fun postMessage(msg: UiText) {
        viewModelScope.launch { _events.emit(UiEvent.Message(msg)) }
    }

    private fun postError(t: Throwable, fallback: UiText) {
        val mappedError = ErrorMapper.map(t)
        val finalError = if (mappedError is AppError.Unknown) {
            AppError.Unknown(fallback = fallback, tech = t.message)
        } else {
            mappedError
        }
        viewModelScope.launch { _events.emit(UiEvent.Error(finalError)) }
    }

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
                _uiState.update { it.copy(plan = rebuildUiPlan(), canEditPlan = true, isLoading = false) }
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
                _uiState.update { it.copy(plan = rebuildUiPlan(), isLoading = false) }
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
                _uiState.update { it.copy(plan = rebuildUiPlan(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                postError(e, UiText.StringResource(R.string.error_rolling_activity))
            }
        }
    }

    fun setCustomActivity(dayIndex: Int, slot: DaySlot, title: String, description: String) {
        if (!_uiState.value.canEditPlan) return
        planGenerator.setCustomSlot(dayIndex, slot, title, description, internalPlanDays)
        _uiState.update { it.copy(plan = rebuildUiPlan()) }
    }

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
            _uiState.update { it.copy(dayWeatherByDate = result.byDate, forecastNotice = result.notice) }
        }
    }

    private fun isBadWeatherForDayIndex(dayIndex: Int): Boolean {
        val prefs = _uiState.value.preferences ?: return false
        val startMillis = prefs.startDateMillis ?: return false
        val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
        val dayMillis = DateUtils.dayMillisForIndex(startNorm, dayIndex)
        return _uiState.value.dayWeatherByDate[dayMillis]?.isBadWeather ?: false
    }

    /**
     * Zapis offline-first:
     * - lokalnie zawsze (albo błąd IO)
     * - chmura best-effort -> komunikat "brak internetu, zapisano na urządzeniu"
     */
    fun savePlanLocally(uid: String? = null) {
        val realUid = uid ?: sessionProvider.currentUid()
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
                val res = savePlanLocallyUseCase.execute(
                    realUid, prefs, dest, internalPlanDays, currentPlanId, currentCreatedAtMillis
                )
                currentPlanId = res.planId
                currentCreatedAtMillis = res.createdAtMillis

                // kosmetyka: zapis lokalny OK, chmura padła -> pokaż specjalny błąd/komunikat
                if (res.cloudError != null) {
                    val err = ErrorMapper.mapCloudSaveFailedButLocalOk(res.cloudError)
                    _events.emit(UiEvent.Error(err))
                } else {
                    postMessage(UiText.StringResource(R.string.msg_saved))
                }

            } catch (e: Exception) {
                postError(e, UiText.StringResource(R.string.error_saving))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadPlanLocally(uid: String? = null) {
        val realUid = uid ?: sessionProvider.currentUid() ?: return
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
        val uiPlan = currentState.plan

        if (destName.isNullOrBlank() || uiPlan.isEmpty()) {
            postMessage(UiText.StringResource(R.string.msg_no_plan_to_export))
            return
        }
        viewModelScope.launch {
            val html = exportPlanPdfUseCase.execute(destName, tripStart, uiPlan)
            _events.emit(UiEvent.PrintPdf(html))
        }
    }

    fun getInternalDayOrNull(dayIndex: Int) = internalPlanDays.getOrNull(dayIndex)

    fun getSlotOrNull(dayIndex: Int, slot: DaySlot): SlotPlan? {
        val d = internalPlanDays.getOrNull(dayIndex) ?: return null
        return when (slot) {
            DaySlot.MORNING -> d.morning
            DaySlot.MIDDAY -> d.midday
            DaySlot.EVENING -> d.evening
        }
    }

    fun getDayWeatherForIndexGetter(dayIndex: Int): DayWeatherUi? {
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
        _uiState.update { it.copy(plan = rebuildUiPlan()) }
    }

    fun moveDayDown(index: Int) {
        if (!_uiState.value.canEditPlan || index < 0 || index >= internalPlanDays.size - 1) return
        val tmp = internalPlanDays[index + 1]
        internalPlanDays[index + 1] = internalPlanDays[index]
        internalPlanDays[index] = tmp
        _uiState.update { it.copy(plan = rebuildUiPlan()) }
    }

    private fun rebuildUiPlan(): List<DayPlan> {
        val dest = _uiState.value.chosenDestination ?: return emptyList()
        return planGenerator.rebuildDayPlans(internalPlanDays, dest.displayName)
    }
}
