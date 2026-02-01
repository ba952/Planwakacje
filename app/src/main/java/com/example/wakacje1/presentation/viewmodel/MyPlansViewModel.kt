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

/**
 * Stan UI dla operacji na listach planów (ładowanie, błędy).
 */
data class PlansUiState(
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel odpowiedzialny za zarządzanie listami planów (lokalną i chmurową).
 * Implementuje logikę "Offline-First" – najpierw wyświetla dane lokalne, a następnie synchronizuje je z chmurą.
 */
class MyPlansViewModel(
    private val localRepository: PlansLocalRepository,
    private val cloudRepository: PlansCloudRepository
) : ViewModel() {

    // --- Stan listy chmurowej (metadane i status synchronizacji) ---
    var cloudUi by mutableStateOf(PlansUiState())
        private set
    var cloudPlans by mutableStateOf<List<CloudPlanRow>>(emptyList())
        private set

    // --- Stan listy lokalnej (główne źródło danych dla UI) ---
    var localUi by mutableStateOf(PlansUiState())
        private set
    var localPlans by mutableStateOf<List<LocalPlanRow>>(emptyList())
        private set

    // Referencje do aktywnych zadań korutyn (jobów), umożliwiające ich anulowanie
    private var localJob: Job? = null
    private var syncUpJob: Job? = null
    private var syncDownJob: Job? = null

    /**
     * Rozpoczyna obserwowanie danych lokalnych i chmurowych dla danego użytkownika.
     */
    fun start(uid: String) {
        startLocal(uid)
        startCloud(uid)
    }

    /**
     * Zatrzymuje wszystkie procesy nasłuchiwania i synchronizacji.
     */
    fun stop() {
        // ZMIANA: Repozytorium powinno zarządzać czyszczeniem listenerów (np. Firebase)
        cloudRepository.stopListening()
        localJob?.cancel()
        syncUpJob?.cancel()
        syncDownJob?.cancel()
    }

    /**
     * Uruchamia reaktywne nasłuchiwanie bazy lokalnej (Room/Flow).
     */
    private fun startLocal(uid: String) {
        localJob?.cancel()
        localUi = PlansUiState(loading = true)

        localJob = viewModelScope.launch {
            // Obserwowanie zmian w bazie danych – każde odświeżenie danych lokalnych
            // może wyzwolić próbę synchronizacji "w górę" (do chmury).
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

    /**
     * Wczytuje pełny obiekt planu z bazy lokalnej.
     */
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

    /**
     * Usuwa plan z obu źródeł danych.
     */
    fun deletePlan(uid: String, planId: String) {
        viewModelScope.launch {
            // 1. Priorytetowe usunięcie lokalne (natychmiastowy feedback w UI)
            try {
                localRepository.deletePlan(uid, planId)
            } catch (e: Exception) {
                localUi = localUi.copy(error = e.message)
                return@launch
            }
            // 2. Usunięcie z chmury z limitem czasowym (timeout)
            try {
                withTimeout(15_000) { cloudRepository.deletePlan(uid, planId) }
            } catch (e: Exception) {
                cloudUi = cloudUi.copy(error = e.message)
            }
        }
    }

    /**
     * Uruchamia nasłuchiwanie zmian w chmurze (np. Firestore Snapshot Listener).
     */
    private fun startCloud(uid: String) {
        cloudUi = PlansUiState(loading = true)

        cloudRepository.startListening(
            uid = uid,
            onUpdate = { list ->
                // Nowe dane z chmury wyzwalają logikę synchronizacji "w dół" (do bazy lokalnej)
                cloudPlans = list
                cloudUi = PlansUiState(loading = false)
                scheduleSyncDown(uid)
            },
            onRemovedIds = { removed ->
                // Automatyczne czyszczenie bazy lokalnej po usunięciu planów w chmurze
                viewModelScope.launch {
                    removed.forEach { id -> localRepository.deletePlan(uid, id) }
                }
            },
            onError = { msg -> cloudUi = PlansUiState(loading = false, error = msg) }
        )
    }

    /**
     * Planuje synchronizację danych z chmury do bazy lokalnej (np. po aktualizacji u innego klienta).
     */
    private fun scheduleSyncDown(uid: String) {
        syncDownJob?.cancel()
        syncDownJob = viewModelScope.launch {
            // Miejsce na logikę porównywania dat modyfikacji (updatedAt)
            // localRepository.upsertPlan(...)
        }
    }

    /**
     * Planuje synchronizację zmian lokalnych do chmury.
     */
    private fun scheduleSyncUp(uid: String) {
        syncUpJob?.cancel()
        syncUpJob = viewModelScope.launch {
            // Miejsce na logikę wysyłania zmian, które nie trafiły jeszcze do chmury
            // cloudRepository.upsertPlan(...)
        }
    }

    // Metody do czyszczenia stanów błędów w UI
    fun clearLocalError() { localUi = localUi.copy(error = null) }
    fun clearCloudError() { cloudUi = cloudUi.copy(error = null) }
}