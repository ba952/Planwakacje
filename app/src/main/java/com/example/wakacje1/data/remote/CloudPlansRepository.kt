package com.example.wakacje1.data.remote

import com.example.wakacje1.data.local.StoredPlan
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Niskopoziomowe źródło danych (DataSource) operujące bezpośrednio na SDK Firestore.
 * Odpowiada za operacje CRUD w chmurze oraz synchronizację czasu rzeczywistego.
 */
object CloudPlansRepository {

    // Leniwa inicjalizacja instancji Firestore (Singleton SDK)
    private val db by lazy { FirebaseFirestore.getInstance() }

    /**
     * Definiuje ścieżkę do prywatnej kolekcji planów danego użytkownika.
     * Struktura NoSQL: users/{uid}/plans/{planId}
     * Dzięki temu reguły bezpieczeństwa (Security Rules) są proste do zdefiniowania (request.auth.uid == uid).
     */
    private fun plansCol(uid: String) = db.collection("users").document(uid).collection("plans")

    /**
     * Ustanawia aktywny nasłuch (Real-time Listener) na kolekcję planów.
     *
     * @return [ListenerRegistration] - obiekt pozwalający na odpięcie nasłuchu (np. w onCleared ViewModelu),
     * co jest krytyczne dla zapobiegania wyciekom pamięci i zbędnemu zużyciu transferu.
     */
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

                // 1) Detekcja usunięć (DocumentChange.Type.REMOVED)
                // Pozwala zaktualizować lokalną bazę danych (kaskadowe usunięcie).
                val removedIds: List<String> = s.documentChanges
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { it.document.id }

                if (removedIds.isNotEmpty()) {
                    onRemovedIds(removedIds)
                }

                // 2) Budowanie aktualnego stanu listy (Projekcja do DTO)
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

    /**
     * Zapisuje plan w chmurze (Upsert).
     *
     * DECYZJA ARCHITEKTONICZNA:
     * Zamiast tworzyć głębokie sub-kolekcje dla każdego dnia i aktywności,
     * cała struktura planu jest serializowana do jednego pola `payloadJson` (String).
     *
     * Zalety:
     * 1. Atomowość zapisu (jedna transakcja).
     * 2. Drastyczna redukcja liczby operacji "Write" i "Read" (niższe koszty Firebase).
     * 3. Pola `title`, `date` są wyciągnięte "na zewnątrz" tylko do celów indeksowania i wyświetlania listy.
     */
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
            "payloadJson" to plan.toJsonString() // Serializacja BLOB
        ).apply {
            start?.let { this["startDateMillis"] = it }
            end?.let { this["endDateMillis"] = it }
        }

        plansCol(uid).document(plan.id)
            .set(data)
            .await() // Extension function zamieniająca Task na Coroutine
    }

    /**
     * Pobiera pojedynczy dokument i deserializuje payload JSON do pełnego modelu [StoredPlan].
     */
    suspend fun loadPlan(uid: String, id: String): StoredPlan {
        val doc = plansCol(uid).document(id).get().await()
        val payload = doc.getString("payloadJson") ?: "{}"
        return StoredPlan.fromJsonString(payload)
    }

    suspend fun deletePlan(uid: String, id: String) {
        plansCol(uid).document(id).delete().await()
    }
}