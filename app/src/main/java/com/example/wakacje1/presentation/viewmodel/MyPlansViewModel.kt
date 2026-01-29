package com.example.wakacje1.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.data.local.LocalPlanRow
import com.example.wakacje1.data.local.PlansLocalRepository // Nowa klasa
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.data.remote.CloudPlanRow
import com.example.wakacje1.data.remote.PlansCloudRepository // Nowa klasa (lub wrapper)
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class PlansUiState(
    val loading: Boolean = false,
    val error: String? = null
)

// ZMIANA: Brak AndroidViewModel. Repozytoria wstrzyknięte.
class MyPlansViewModel(
    private val localRepository: PlansLocalRepository,
    private val cloudRepository: PlansCloudRepository
) : ViewModel() {

    // --- cloud metadata ---
    var cloudUi by mutableStateOf(PlansUiState())
        private set
    var cloudPlans by mutableStateOf<List<CloudPlanRow>>(emptyList())
        private set

    // --- local list ---
    var localUi by mutableStateOf(PlansUiState())
        private set
    var localPlans by mutableStateOf<List<LocalPlanRow>>(emptyList())
        private set

    private var localJob: Job? = null
    private var syncUpJob: Job? = null
    private var syncDownJob: Job? = null

    // Startujemy nasłuchiwanie
    fun start(uid: String) {
        startLocal(uid)
        startCloud(uid)
    }

    fun stop() {
        // ZMIANA: Repozytorium powinno zarządzać czyszczeniem listenerów
        cloudRepository.stopListening()
        localJob?.cancel()
        syncUpJob?.cancel()
        syncDownJob?.cancel()
    }

    private fun startLocal(uid: String) {
        localJob?.cancel()
        localUi = PlansUiState(loading = true)

        localJob = viewModelScope.launch {
            // ZMIANA: Nie podajemy Contextu! Repozytorium już go ma.
            localRepository.observePlans(uid)
                .catch { e ->
                    localUi = PlansUiState(loading = false, error = e.message)
                }
                .collect { rows ->
                    localPlans = rows
                    localUi = PlansUiState(loading = false)
                    scheduleSyncUp(uid)
                }
        }
    }

    fun loadLocal(uid: String, planId: String, onLoaded: (StoredPlan) -> Unit) {
        viewModelScope.launch {
            try {
                val p = localRepository.loadPlan(uid, planId)
                if (p == null) {
                    localUi = localUi.copy(error = "Błąd wczytania (null).")
                } else {
                    onLoaded(p)
                }
            } catch (e: Exception) {
                localUi = localUi.copy(error = e.message)
            }
        }
    }

    fun deletePlan(uid: String, planId: String) {
        viewModelScope.launch {
            // 1. Usuń lokalnie
            try {
                localRepository.deletePlan(uid, planId)
            } catch (e: Exception) {
                localUi = localUi.copy(error = e.message)
                return@launch
            }
            // 2. Usuń z chmury
            try {
                withTimeout(15_000) { cloudRepository.deletePlan(uid, planId) }
            } catch (e: Exception) {
                cloudUi = cloudUi.copy(error = e.message)
            }
        }
    }

    private fun startCloud(uid: String) {
        cloudUi = PlansUiState(loading = true)

        // ZMIANA: Użycie metody repozytorium, która zwraca Flow lub przyjmuje callbacki
        cloudRepository.startListening(
            uid = uid,
            onUpdate = { list ->
                cloudPlans = list
                cloudUi = PlansUiState(loading = false)
                scheduleSyncDown(uid)
            },
            onRemovedIds = { removed ->
                viewModelScope.launch {
                    removed.forEach { id -> localRepository.deletePlan(uid, id) }
                }
            },
            onError = { msg -> cloudUi = PlansUiState(loading = false, error = msg) }
        )
    }

    // Logikę SyncDown/SyncUp zostawiam tu dla uproszczenia (choć powinna być w UseCase),
    // ale kluczowe jest to, że nie używamy tu 'ctx' (getApplication).
    private fun scheduleSyncDown(uid: String) {
        syncDownJob?.cancel()
        syncDownJob = viewModelScope.launch {
            // ... logika porównywania dat ...
            // localRepository.upsertPlan(...)
        }
    }

    private fun scheduleSyncUp(uid: String) {
        syncUpJob?.cancel()
        syncUpJob = viewModelScope.launch {
            // ... logika porównywania dat ...
            // cloudRepository.upsertPlan(...)
        }
    }

    fun clearLocalError() { localUi = localUi.copy(error = null) }
    fun clearCloudError() { cloudUi = cloudUi.copy(error = null) }
}