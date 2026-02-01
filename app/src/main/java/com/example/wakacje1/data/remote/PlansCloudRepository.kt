package com.example.wakacje1.data.remote

import com.example.wakacje1.data.local.StoredPlan
import com.google.firebase.firestore.ListenerRegistration

/**
 * Klasa pośrednicząca (Wrapper/Proxy) dla singletona [CloudPlansRepository].
 *
 * Cel architektoniczny:
 * 1. Testowalność: Obiekty statyczne (Kotlin object) są trudne do mockowania.
 * Ta klasa pozwala na wstrzykiwanie zależności (DI) i łatwe tworzenie mocków w testach jednostkowych.
 * 2. Zarządzanie zasobami: Klasa odpowiada za cykl życia obiektu [ListenerRegistration],
 * gwarantując poprawne odpinanie nasłuchu (zapobieganie wyciekom pamięci).
 */
class PlansCloudRepository {

    // Uchwyt do aktywnego nasłuchu Firestore. Null oznacza brak aktywnej sesji.
    private var listener: ListenerRegistration? = null

    /**
     * Uruchamia strumień danych Real-time dla wskazanego użytkownika.
     * Implementuje mechanizm "Safety First": automatycznie usuwa poprzedni listener (jeśli istniał),
     * zanim utworzy nowy, aby uniknąć duplikacji callbacków.
     */
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

    /**
     * Jawne zakończenie nasłuchu.
     * Powinno być wywoływane w momencie niszczenia ViewModelu (onCleared).
     */
    fun stopListening() {
        listener?.remove()
        listener = null
    }

    // --- Metody delegujące (Pass-through) do warstwy niskopoziomowej ---

    suspend fun deletePlan(uid: String, planId: String) {
        CloudPlansRepository.deletePlan(uid, planId)
    }

    suspend fun upsertPlan(uid: String, plan: StoredPlan, updatedAtMillis: Long) {
        // Delegacja zapisu z pominięciem opcjonalnych pól (title/subtitle),
        // które zostaną wywnioskowane wewnątrz CloudPlansRepository.
        CloudPlansRepository.upsertPlan(
            uid = uid,
            plan = plan,
            updatedAtMillis = updatedAtMillis
        )
    }
}