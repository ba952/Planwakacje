package com.example.wakacje1.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.data.remote.CloudPlanRow
import com.example.wakacje1.data.remote.CloudPlansRepository
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

data class PlansUiState(
    val loading: Boolean = false,
    val error: String? = null
)

class MyPlansViewModel : ViewModel() {

    var ui by mutableStateOf(PlansUiState())
        private set

    var plans by mutableStateOf<List<CloudPlanRow>>(emptyList())
        private set

    private var reg: ListenerRegistration? = null

    fun start(uid: String) {
        stop()
        ui = PlansUiState(loading = true, error = null)

        reg = CloudPlansRepository.listenPlans(
            uid = uid,
            onUpdate = { list ->
                plans = list
                ui = PlansUiState(loading = false, error = null)
            },
            onError = { msg ->
                ui = PlansUiState(loading = false, error = msg)
            }
        )
    }

    fun stop() {
        reg?.remove()
        reg = null
    }

    fun delete(uid: String, planId: String) {
        viewModelScope.launch {
            try {
                CloudPlansRepository.deletePlan(uid, planId)
            } catch (e: Exception) {
                ui = PlansUiState(loading = false, error = e.message ?: "Błąd usuwania.")
            }
        }
    }

    fun save(uid: String, plan: StoredPlan, onDone: (String) -> Unit) {
        viewModelScope.launch {
            ui = PlansUiState(loading = true, error = null)
            try {
                val id = CloudPlansRepository.savePlan(uid, plan)
                ui = PlansUiState(loading = false, error = null)
                onDone(id)
            } catch (e: Exception) {
                ui = PlansUiState(loading = false, error = e.message ?: "Błąd zapisu w chmurze.")
            }
        }
    }

    fun load(uid: String, planId: String, onLoaded: (StoredPlan) -> Unit) {
        viewModelScope.launch {
            ui = PlansUiState(loading = true, error = null)
            try {
                val p = CloudPlansRepository.loadPlan(uid, planId)
                ui = PlansUiState(loading = false, error = null)
                onLoaded(p)
            } catch (e: Exception) {
                ui = PlansUiState(loading = false, error = e.message ?: "Błąd wczytania planu.")
            }
        }
    }
}
