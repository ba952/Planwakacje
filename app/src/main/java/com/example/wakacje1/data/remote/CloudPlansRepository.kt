package com.example.wakacje1.data.remote

import com.example.wakacje1.data.local.StoredPlan
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

object CloudPlansRepository {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun plansCol(uid: String) = db.collection("users").document(uid).collection("plans")

    fun listenPlans(
        uid: String,
        onUpdate: (List<CloudPlanRow>) -> Unit,
        onRemovedIds: (List<String>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return plansCol(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err.message ?: "Błąd nasłuchu planów.")
                    return@addSnapshotListener
                }

                val s = snap ?: return@addSnapshotListener

                // 1) Wykryj usunięte dokumenty
                val removedIds: List<String> = s.documentChanges
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { it.document.id }

                if (removedIds.isNotEmpty()) {
                    onRemovedIds(removedIds)
                }

                // 2) Zbuduj aktualną listę planów
                val rows = s.documents.map { d ->
                    CloudPlanRow(
                        id = d.id,
                        title = d.getString("title") ?: "Plan",
                        subtitle = d.getString("subtitle") ?: "",
                        startDateMillis = d.getLong("startDateMillis"),
                        endDateMillis = d.getLong("endDateMillis"),
                        createdAtMillis = d.getLong("createdAtMillis") ?: 0L,
                        updatedAtMillis = d.getLong("updatedAtMillis") ?: 0L
                    )
                }.sortedByDescending { it.updatedAtMillis }

                onUpdate(rows)
            }
    }

    suspend fun upsertPlan(
        uid: String,
        plan: StoredPlan,
        title: String? = null,
        subtitle: String? = null,
        updatedAtMillis: Long
    ) {
        val t = title ?: plan.destination.displayName.ifBlank { "Plan" }
        val sub = subtitle ?: ""

        val start = plan.preferences?.startDateMillis
        val end = plan.preferences?.endDateMillis

        val data = hashMapOf<String, Any?>(
            "title" to t,
            "subtitle" to sub,
            "createdAtMillis" to plan.createdAtMillis,
            "updatedAtMillis" to updatedAtMillis,
            "payloadJson" to plan.toJsonString()
        ).apply {
            start?.let { this["startDateMillis"] = it }
            end?.let { this["endDateMillis"] = it }
        }

        plansCol(uid).document(plan.id)
            .set(data)
            .await()
    }

    suspend fun loadPlan(uid: String, id: String): StoredPlan {
        val doc = plansCol(uid).document(id).get().await()
        val payload = doc.getString("payloadJson") ?: "{}"
        return StoredPlan.fromJsonString(payload) ?: error("Nie udało się odczytać planu z chmury.")
    }

    suspend fun deletePlan(uid: String, id: String) {
        plansCol(uid).document(id).delete().await()
    }
}
