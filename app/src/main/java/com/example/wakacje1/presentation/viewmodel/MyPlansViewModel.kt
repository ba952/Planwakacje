package com.example.wakacje1.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.data.local.LocalPlanRow
import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.remote.CloudPlanRow
import com.example.wakacje1.data.remote.PlansCloudRepository
import com.example.wakacje1.presentation.common.AppError
import com.example.wakacje1.presentation.common.ErrorMapper
import com.example.wakacje1.presentation.common.UiEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class PlansUiState(
    val loading: Boolean = false
)

/**
 * ViewModel odpowiedzialny za zarządzanie listami planów (lokalną i chmurową).
 *
 * Offline-first:
 * - UI czyta z lokalnej bazy (Room/Flow).
 * - Chmura działa jako synchronizacja.
 * - Konflikty: LWW (updatedAtMillis).
 *
 * Wersja A:
 * - BRAK error w stanie.
 * - Wszystkie błędy lecą jako UiEvent.Error(AppError).
 */
class MyPlansViewModel(
    private val localRepository: PlansLocalRepository,
    private val cloudRepository: PlansCloudRepository
) : ViewModel() {

    var cloudUi by mutableStateOf(PlansUiState())
        private set
    var cloudPlans by mutableStateOf<List<CloudPlanRow>>(emptyList())
        private set

    var localUi by mutableStateOf(PlansUiState())
        private set
    var localPlans by mutableStateOf<List<LocalPlanRow>>(emptyList())
        private set

    private var localJob: Job? = null
    private var syncUpJob: Job? = null
    private var syncDownJob: Job? = null

    private var activeUid: String? = null

    private var cloudReady = false
    private var localReady = false

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    fun start(uid: String) {
        if (activeUid == uid) return
        stop()
        activeUid = uid

        cloudReady = false
        localReady = false

        startLocal(uid)
        startCloud(uid)
    }

    fun stop() {
        activeUid = null

        cloudRepository.stopListening()
        localJob?.cancel()
        syncUpJob?.cancel()
        syncDownJob?.cancel()

        localJob = null
        syncUpJob = null
        syncDownJob = null

        cloudReady = false
        localReady = false

        // opcjonalnie: czyścimy loading, żeby UI nie pokazywało starych stanów
        localUi = PlansUiState(loading = false)
        cloudUi = PlansUiState(loading = false)
        cloudPlans = emptyList()
        localPlans = emptyList()
    }

    private fun startLocal(uid: String) {
        localJob?.cancel()
        localUi = PlansUiState(loading = true)

        localJob = viewModelScope.launch {
            localRepository.observePlans(uid)
                .catch { e ->
                    localUi = PlansUiState(loading = false)
                    _events.emit(UiEvent.Error(ErrorMapper.map(e)))
                }
                .collect { rows ->
                    localPlans = rows
                    localUi = PlansUiState(loading = false)
                    localReady = true

                    if (cloudReady) scheduleSyncUp(uid)
                }
        }
    }

    fun loadLocal(uid: String, planId: String) {
        viewModelScope.launch {
            try {
                val p = localRepository.loadPlan(uid, planId)
                if (p == null) {
                    _events.emit(UiEvent.Error(AppError.NotFound(tech = "StoredPlan null for id=$planId")))
                } else {
                    _events.emit(UiEvent.OpenPlan(p))
                }
            } catch (e: Exception) {
                _events.emit(UiEvent.Error(ErrorMapper.map(e)))
            }
        }
    }

    fun deletePlan(uid: String, planId: String) {
        viewModelScope.launch {
            // 1) lokalnie
            try {
                localRepository.deletePlan(uid, planId)
            } catch (e: Exception) {
                _events.emit(UiEvent.Error(ErrorMapper.map(e)))
                return@launch
            }

            // 2) chmura (timeout) – jeśli failnie, tylko komunikat, lokalnie już usunięte
            try {
                withTimeout(15_000) { cloudRepository.deletePlan(uid, planId) }
            } catch (e: Exception) {
                _events.emit(UiEvent.Error(ErrorMapper.map(e)))
            }
        }
    }

    private fun startCloud(uid: String) {
        cloudUi = PlansUiState(loading = true)

        cloudRepository.startListening(
            uid = uid,
            onUpdate = { list ->
                cloudPlans = list
                cloudUi = PlansUiState(loading = false)
                cloudReady = true

                scheduleSyncDown(uid)

                if (localReady) scheduleSyncUp(uid)
            },
            onRemovedIds = { removed ->
                viewModelScope.launch {
                    removed.forEach { id ->
                        try {
                            localRepository.deletePlan(uid, id)
                        } catch (e: Exception) {
                            _events.emit(UiEvent.Error(ErrorMapper.map(e)))
                        }
                    }
                }
            },
            onError = { msg ->
                cloudUi = PlansUiState(loading = false)
                _events.tryEmit(UiEvent.Error(AppError.Network(tech = msg)))
            }
        )
    }

    private fun scheduleSyncDown(uid: String) {
        syncDownJob?.cancel()
        syncDownJob = viewModelScope.launch {
            delay(250)

            val uidNow = activeUid ?: return@launch
            if (uidNow != uid) return@launch

            val localMap = localPlans.associateBy { it.id }

            val toPull = cloudPlans.filter { cloudRow ->
                val localRow = localMap[cloudRow.id]
                localRow == null || cloudRow.updatedAtMillis > localRow.updatedAtMillis
            }

            if (toPull.isEmpty()) return@launch

            try {
                toPull.forEach { row ->
                    val cloudPlan = withTimeout(15_000) { cloudRepository.loadPlan(uid, row.id) }
                    localRepository.upsertStoredPlan(uid, cloudPlan, updatedAtMillis = row.updatedAtMillis)
                }
            } catch (e: Exception) {
                _events.emit(UiEvent.Error(ErrorMapper.map(e)))
            }
        }
    }

    private fun scheduleSyncUp(uid: String) {
        syncUpJob?.cancel()
        syncUpJob = viewModelScope.launch {
            delay(250)

            val uidNow = activeUid ?: return@launch
            if (uidNow != uid) return@launch
            if (!cloudReady || !localReady) return@launch

            val cloudMap = cloudPlans.associateBy { it.id }

            val toPush = localPlans.filter { localRow ->
                val cloudRow = cloudMap[localRow.id]
                cloudRow == null || localRow.updatedAtMillis > cloudRow.updatedAtMillis
            }

            if (toPush.isEmpty()) return@launch

            try {
                toPush.forEach { row ->
                    val localPlan = localRepository.loadPlan(uid, row.id) ?: return@forEach
                    withTimeout(15_000) {
                        cloudRepository.upsertPlan(uid, localPlan, updatedAtMillis = row.updatedAtMillis)
                    }
                }
            } catch (e: Exception) {
                _events.emit(UiEvent.Error(ErrorMapper.map(e)))
            }
        }
    }
}
