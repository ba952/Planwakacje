package com.example.wakacje1.data.remote

import com.example.wakacje1.data.local.StoredPlan
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

object CloudPlansRepository {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun plansCol(uid: String) =
        db.collection("users").document(uid).collection("plans")

    fun listenPlans(
        uid: String,
        onUpdate: (List<CloudPlanRow>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return plansCol(uid)
            .orderBy("createdAtMillis")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err.message ?: "Błąd nasłuchu Firestore.")
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                val rows = docs.map {
                    CloudPlanRow(
                        id = it.id,
                        title = it.getString("title") ?: "Plan",
                        subtitle = it.getString("subtitle") ?: ""
                    )
                }.reversed()
                onUpdate(rows)
            }
    }

    suspend fun savePlan(uid: String, plan: StoredPlan): String {
        val title = plan.destination.displayName.ifBlank { "Plan" }
        val subtitle = plan.preferences?.let { "Dni: ${it.days}, budżet: ${it.budget} zł" } ?: ""

        val doc = plansCol(uid).document()
        val data = hashMapOf(
            "createdAtMillis" to plan.createdAtMillis,
            "title" to title,
            "subtitle" to subtitle,
            "plan" to plan
        )
        doc.set(data).await()
        return doc.id
    }

    suspend fun loadPlan(uid: String, planId: String): StoredPlan {
        val snap = plansCol(uid).document(planId).get().await()
        val plan = snap.get("plan") as? Map<*, *>
            ?: throw IllegalStateException("Brak pola 'plan' w dokumencie.")
        // Firestore potrafi mapować data class automatycznie, ale tu trzymamy bezpieczną ścieżkę:
        // najprościej: zrób StoredPlan jako @Parcelize? Nie trzeba.
        // W praktyce: jeśli zapisujesz StoredPlan jako obiekt, to możesz pobrać:
        // snap.get("plan", StoredPlan::class.java)
        // ale zależy od konfiguracji.
        val converted = snap.get("plan", StoredPlan::class.java)
        return converted ?: throw IllegalStateException("Nie udało się zmapować planu.")
    }

    suspend fun deletePlan(uid: String, planId: String) {
        plansCol(uid).document(planId).delete().await()
    }
}
