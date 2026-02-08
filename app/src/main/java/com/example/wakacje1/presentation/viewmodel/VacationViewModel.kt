package com.example.wakacje1.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.R
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
import com.example.wakacje1.domain.usecase.SuggestDestinationsUseCase
import com.example.wakacje1.presentation.common.AppError
import com.example.wakacje1.presentation.common.ErrorMapper
import com.example.wakacje1.presentation.common.UiEvent
import com.example.wakacje1.presentation.common.UiText
import com.example.wakacje1.presentation.vacation.LoadLocalPlanResult
import com.example.wakacje1.presentation.vacation.SaveLocalPlanResult
import com.example.wakacje1.presentation.vacation.VacationExportManager
import com.example.wakacje1.presentation.vacation.VacationPersistenceManager
import com.example.wakacje1.presentation.vacation.VacationPlanEditor
import com.example.wakacje1.presentation.vacation.VacationSelectors
import com.example.wakacje1.presentation.vacation.VacationWeatherManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VacationViewModel(
    private val suggestDestinationsUseCase: SuggestDestinationsUseCase,
    private val planEditor: VacationPlanEditor,
    private val weatherManager: VacationWeatherManager,
    private val persistenceManager: VacationPersistenceManager,
    private val exportManager: VacationExportManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VacationUiState())
    val uiState: StateFlow<VacationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

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
        persistenceManager.resetCurrentPlan()
        planEditor.resetSession()

        _uiState.update {
            VacationUiState(
                preferences = prefs,
                isLoading = false,
                internalPlan = emptyList()
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
        persistenceManager.resetCurrentPlan()
        planEditor.resetSession()

        _uiState.update {
            it.copy(
                chosenDestination = destination,
                plan = emptyList(),
                internalPlan = emptyList(),
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
        val state = _uiState.value
        val prefs = state.preferences ?: return
        val dest = state.chosenDestination ?: return

        val transportCost = VacationSelectors.getTransportCostUsedForSuggestions(state, dest)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val res = planEditor.generatePlan(
                    prefs = prefs,
                    dest = dest,
                    transportCost = transportCost,
                    isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                )
                _uiState.update {
                    it.copy(
                        internalPlan = res.internalPlan,
                        plan = res.uiPlan,
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
        val state = _uiState.value
        if (!state.canEditPlan) return
        val prefs = state.preferences ?: return
        val dest = state.chosenDestination ?: return
        if (state.internalPlan.isEmpty()) return

        val transportCost = VacationSelectors.getTransportCostUsedForSuggestions(state, dest)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val res = planEditor.regenerateDay(
                    dayIndex = dayIndex,
                    prefs = prefs,
                    dest = dest,
                    transportCost = transportCost,
                    currentInternal = state.internalPlan,
                    isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                )
                _uiState.update {
                    it.copy(
                        internalPlan = res.internalPlan,
                        plan = res.uiPlan,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                postError(e, UiText.StringResource(R.string.error_regenerating_day))
            }
        }
    }

    fun rollNewActivity(dayIndex: Int, slot: DaySlot) {
        val state = _uiState.value
        if (!state.canEditPlan) return
        val prefs = state.preferences ?: return
        val dest = state.chosenDestination ?: return
        if (state.internalPlan.isEmpty()) return

        val transportCost = VacationSelectors.getTransportCostUsedForSuggestions(state, dest)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val res = planEditor.rollNewActivity(
                    dayIndex = dayIndex,
                    slot = slot,
                    prefs = prefs,
                    dest = dest,
                    transportCost = transportCost,
                    currentInternal = state.internalPlan,
                    isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                )
                _uiState.update {
                    it.copy(
                        internalPlan = res.internalPlan,
                        plan = res.uiPlan,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                postError(e, UiText.StringResource(R.string.error_rolling_activity))
            }
        }
    }

    fun setCustomActivity(dayIndex: Int, slot: DaySlot, title: String, description: String) {
        val state = _uiState.value
        if (!state.canEditPlan) return
        val dest = state.chosenDestination ?: return
        if (state.internalPlan.isEmpty()) return

        val res = planEditor.setCustomActivity(
            dayIndex = dayIndex,
            slot = slot,
            title = title,
            description = description,
            currentInternal = state.internalPlan,
            dest = dest
        )

        _uiState.update { it.copy(internalPlan = res.internalPlan, plan = res.uiPlan) }
    }

    fun loadWeatherForCity(cityQuery: String, force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    weather = WeatherUiState(
                        loading = true,
                        error = null
                    )
                )
            }
            val result = weatherManager.loadWeatherForCity(cityQuery, force)
            _uiState.update { it.copy(weather = result) }
        }
    }

    fun loadForecastForTrip(force: Boolean = false) {
        val state = _uiState.value
        val prefs = state.preferences ?: return
        val dest = state.chosenDestination ?: return

        viewModelScope.launch {
            // czyścimy poprzedni komunikat na czas pobierania
            _uiState.update { it.copy(forecastNotice = null) }

            val result = weatherManager.loadForecastForTrip(prefs, dest, force)
            _uiState.update { it.copy(dayWeatherByDate = result.byDate, forecastNotice = result.notice) }
        }
    }

    private fun isBadWeatherForDayIndex(dayIndex: Int): Boolean {
        val state = _uiState.value
        return weatherManager.isBadWeatherForDayIndex(
            prefs = state.preferences,
            dayWeatherByDate = state.dayWeatherByDate,
            dayIndex = dayIndex
        )
    }

    fun savePlanLocally(uid: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val res = persistenceManager.savePlanLocally(uid, _uiState.value)) {
                is SaveLocalPlanResult.NoUser ->
                    postMessage(UiText.StringResource(R.string.msg_no_user))

                is SaveLocalPlanResult.NothingToSave ->
                    Unit

                is SaveLocalPlanResult.Success -> {
                    if (res.cloudError != null) {
                        val err = ErrorMapper.mapCloudSaveFailedButLocalOk(res.cloudError)
                        _events.emit(UiEvent.Error(err))
                    } else {
                        postMessage(UiText.StringResource(R.string.msg_saved))
                    }
                }

                is SaveLocalPlanResult.Failure ->
                    postError(res.error, UiText.StringResource(R.string.error_saving))
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun loadPlanLocally(uid: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val res = persistenceManager.loadPlanLocally(uid)) {
                is LoadLocalPlanResult.NoUser ->
                    Unit

                is LoadLocalPlanResult.NoPlans ->
                    postMessage(UiText.StringResource(R.string.msg_no_plans))

                is LoadLocalPlanResult.Success -> {
                    planEditor.resetSession()
                    _uiState.update { res.newState.copy(isLoading = false) }
                    postMessage(UiText.StringResource(R.string.msg_loaded))
                }

                is LoadLocalPlanResult.Failure ->
                    postError(res.error, UiText.StringResource(R.string.error_loading))
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun applyStoredPlan(stored: StoredPlan) {
        planEditor.resetSession()
        val newState = persistenceManager.applyStoredPlan(stored)
        _uiState.update { newState }
    }

    fun exportCurrentPlanToPdf() {
        val state = _uiState.value
        val destName = state.chosenDestination?.displayName
        val tripStart = state.preferences?.startDateMillis
        val uiPlan = state.plan

        if (destName.isNullOrBlank() || uiPlan.isEmpty()) {
            postMessage(UiText.StringResource(R.string.msg_no_plan_to_export))
            return
        }

        viewModelScope.launch {
            val html = exportManager.buildHtml(destName, tripStart, uiPlan)
            _events.emit(UiEvent.PrintPdf(html))
        }
    }

    // --- API dla UI (delegacja do selectorów / weather managera) ---

    fun getInternalDayOrNull(dayIndex: Int) =
        VacationSelectors.getInternalDayOrNull(_uiState.value, dayIndex)

    fun getSlotOrNull(dayIndex: Int, slot: DaySlot): SlotPlan? =
        VacationSelectors.getSlotOrNull(_uiState.value, dayIndex, slot)

    fun getDayWeatherForIndexGetter(dayIndex: Int): DayWeatherUi? =
        weatherManager.getDayWeatherForIndex(_uiState.value.preferences, _uiState.value.dayWeatherByDate, dayIndex)

    fun getBudgetPerDayWithTransport(d: Destination): Int =
        VacationSelectors.getBudgetPerDayWithTransport(_uiState.value, d)

    fun getTransportCostUsedForSuggestions(d: Destination): Int =
        VacationSelectors.getTransportCostUsedForSuggestions(_uiState.value, d)

    fun getTransportScenarioLabel(): UiText =
        VacationSelectors.getTransportScenarioLabel(_uiState.value)

    fun moveDayUp(index: Int) {
        val state = _uiState.value
        if (!state.canEditPlan) return
        val res = planEditor.moveDayUp(index, state.internalPlan, state.chosenDestination)
        _uiState.update { it.copy(internalPlan = res.internalPlan, plan = res.uiPlan) }
    }

    fun moveDayDown(index: Int) {
        val state = _uiState.value
        if (!state.canEditPlan) return
        val res = planEditor.moveDayDown(index, state.internalPlan, state.chosenDestination)
        _uiState.update { it.copy(internalPlan = res.internalPlan, plan = res.uiPlan) }
    }
}
