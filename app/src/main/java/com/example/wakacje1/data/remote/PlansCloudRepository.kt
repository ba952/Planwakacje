package com.example.wakacje1.data.remote

import com.example.wakacje1.data.local.StoredPlan
import com.google.firebase.firestore.ListenerRegistration

/**
 * Klasa pośrednicząca (Wrapper/Proxy) dla singletona [CloudPlansRepository].
 *
 * Cel architektoniczny:
 * 1. Testowalność: Obiekty statyczne (Kotlin object) są trudne do mockowania.
 * 2. Zarządzanie zasobami: Klasa odpowiada za cykl życia obiektu [ListenerRegistration].
 */
class PlansCloudRepository {

    private var listener: ListenerRegistration? = null

    fun startListening(
        uid: String,
        onUpdate: (List<CloudPlanRow>) -> Unit,
        onRemovedIds: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        listener?.remove()

        listener = CloudPlansRepository.listenPlans(
            uid = uid,
            onUpdate = onUpdate,
            onRemovedIds = onRemovedIds,
            onError = onError
        )
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    suspend fun deletePlan(uid: String, planId: String) {
        CloudPlansRepository.deletePlan(uid, planId)
    }

    suspend fun upsertPlan(uid: String, plan: StoredPlan, updatedAtMillis: Long) {
        CloudPlansRepository.upsertPlan(
            uid = uid,
            plan = plan,
            updatedAtMillis = updatedAtMillis
        )
    }

    /** NOWE: pobranie pełnego planu z chmury (payloadJson -> StoredPlan). */
    suspend fun loadPlan(uid: String, planId: String): StoredPlan {
        return CloudPlansRepository.loadPlan(uid, planId)
    }
}
