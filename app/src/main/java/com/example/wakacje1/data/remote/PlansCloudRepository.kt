package com.example.wakacje1.data.remote

import com.example.wakacje1.data.local.StoredPlan
import com.google.firebase.firestore.ListenerRegistration

/**
 * Wrapper na statyczny obiekt CloudPlansRepository.
 * Dzięki temu możemy wstrzykiwać tę klasę przez Koin i mockować ją w testach.
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
        // POPRAWKA: Używamy nazwanych argumentów, żeby pominąć title i subtitle (zostaną użyte wartości domyślne null)
        CloudPlansRepository.upsertPlan(
            uid = uid,
            plan = plan,
            updatedAtMillis = updatedAtMillis
        )
    }
}