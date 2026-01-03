package com.example.wakacje1.presentation.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.data.local.LocalPlanRow
import com.example.wakacje1.data.local.PlanStorage
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.data.remote.CloudPlanRow
import com.example.wakacje1.data.remote.CloudPlansRepository
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.collections.iterator

data class PlansUiState(
    val loading: Boolean = false,
    val error: String? = null
)

class MyPlansViewModel(application: Application) : AndroidViewModel(application) {

    // --- cloud metadata (do sync) ---
    var cloudUi by mutableStateOf(PlansUiState())
        private set

    var cloudPlans by mutableStateOf<List<CloudPlanRow>>(emptyList())
        private set

    private var cloudReg: ListenerRegistration? = null

    // --- local list (UI) ---
    var localUi by mutableStateOf(PlansUiState())
        private set

    var localPlans by mutableStateOf<List<LocalPlanRow>>(emptyList())
        private set

    private var localJob: Job? = null
    private var syncUpJob: Job? = null
    private var syncDownJob: Job? = null

    fun start(uid: String) {
        startLocal(uid)
        startCloud(uid)
    }

    fun stop() {
        cloudReg?.remove()
        cloudReg = null
        localJob?.cancel()
        localJob = null
        syncUpJob?.cancel()
        syncUpJob = null
        syncDownJob?.cancel()
        syncDownJob = null
    }

    // ---------------- LOCAL ----------------

    private fun startLocal(uid: String) {
        localJob?.cancel()
        localUi = PlansUiState(loading = true, error = null)

        val ctx = getApplication<Application>()
        localJob = viewModelScope.launch {
            PlanStorage.observePlans(ctx, uid)
                .catch { e ->
                    localUi = PlansUiState(loading = false, error = e.message ?: "Błąd listy lokalnej.")
                }
                .collect { rows ->
                    localPlans = rows
                    localUi = PlansUiState(loading = false, error = null)
                    scheduleSyncUp(uid) // lokalne zmiany -> cloud
                }
        }
    }

    fun loadLocal(uid: String, planId: String, onLoaded: (StoredPlan) -> Unit) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                val p = PlanStorage.loadPlan(ctx, uid, planId)
                if (p == null) {
                    localUi = localUi.copy(error = "Nie udało się wczytać planu lokalnego (uszkodzony JSON?).")
                    return@launch
                }
                onLoaded(p)
            } catch (e: Exception) {
                localUi = localUi.copy(error = e.message ?: "Błąd wczytania lokalnego.")
            }
        }
    }

    fun deletePlan(uid: String, planId: String) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            // 1) usuń lokalnie od razu
            try {
                PlanStorage.deletePlan(ctx, uid, planId)
            } catch (e: Exception) {
                localUi = localUi.copy(error = e.message ?: "Błąd usuwania lokalnego.")
                return@launch
            }

            // 2) usuń w chmurze (w tle, z timeout)
            try {
                withTimeout(15_000) { CloudPlansRepository.deletePlan(uid, planId) }
            } catch (e: Exception) {
                cloudUi = cloudUi.copy(error = e.message ?: "Nie udało się usunąć w chmurze.")
            }
        }
    }
    fun clearLocalError() {
        localUi = localUi.copy(error = null)
    }

    fun clearCloudError() {
        cloudUi = cloudUi.copy(error = null)
    }

    // ---------------- CLOUD ----------------

    private fun startCloud(uid: String) {
        cloudReg?.remove()
        cloudUi = PlansUiState(loading = true, error = null)

        val ctx = getApplication<Application>()

        cloudReg = CloudPlansRepository.listenPlans(
            uid = uid,
            onUpdate = { list ->
                cloudPlans = list
                cloudUi = PlansUiState(loading = false, error = null)
                scheduleSyncDown(uid, ctx) // cloud -> local
            },
            onRemovedIds = { removed ->
                // usunięte w cloud -> usuń w local
                viewModelScope.launch {
                    try {
                        for (id in removed) {
                            PlanStorage.deletePlan(ctx, uid, id)
                        }
                    } catch (e: Exception) {
                        localUi = localUi.copy(error = e.message ?: "Błąd usuwania lokalnego po zmianie w chmurze.")
                    }
                }
            },
            onError = { msg ->
                cloudUi = PlansUiState(loading = false, error = msg)
            }
        )
    }

    // ---------------- AUTO SYNC ----------------

    private fun scheduleSyncDown(uid: String, ctx: Application) {
        syncDownJob?.cancel()
        syncDownJob = viewModelScope.launch {
            try {
                val cloudMap = cloudPlans.associateBy { it.id }
                val localMap = localPlans.associateBy { it.id }

                for ((id, row) in cloudMap) {
                    val local = localMap[id]
                    val localUpdated = local?.updatedAtMillis ?: -1L

                    if (row.updatedAtMillis > localUpdated) {
                        val p = withTimeout(15_000) { CloudPlansRepository.loadPlan(uid, id) }

                        val title = row.title.ifBlank { p.destination.displayName.ifBlank { "Plan" } }
                        val start = row.startDateMillis ?: p.preferences?.startDateMillis
                        val end = row.endDateMillis ?: p.preferences?.endDateMillis


                        PlanStorage.upsertPlan(
                            context = ctx,
                            uid = uid,
                            plan = p,
                            title = title,
                            startDateMillis = start,
                            endDateMillis = end,
                            updatedAtMillis = row.updatedAtMillis
                        )
                    }
                }
            } catch (e: Exception) {
                localUi = localUi.copy(error = e.message ?: "Błąd auto-sync (chmura → lokalnie).")
            }
        }
    }

    private fun scheduleSyncUp(uid: String) {
        syncUpJob?.cancel()
        syncUpJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            try {
                val cloudMap = cloudPlans.associateBy { it.id }

                for (r in localPlans) {
                    val cloudUpdated = cloudMap[r.id]?.updatedAtMillis ?: -1L
                    if (r.updatedAtMillis > cloudUpdated) {
                        val p = PlanStorage.loadPlan(ctx, uid, r.id) ?: continue
                        withTimeout(15_000) {
                            CloudPlansRepository.upsertPlan(uid, p, updatedAtMillis = r.updatedAtMillis)
                        }
                    }
                }
            } catch (e: Exception) {
                cloudUi = cloudUi.copy(error = e.message ?: "Błąd auto-sync (lokalnie → chmura).")
            }
        }
    }
}
