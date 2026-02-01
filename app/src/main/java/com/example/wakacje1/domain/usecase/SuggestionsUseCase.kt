package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences

/**
 * UseCase realizujący dobór destynacji w oparciu o algorytm punktowy (Weighted Scoring).
 * Ocenia dopasowanie regionu, klimatu i stylu, a następnie nakłada korektę budżetową.
 *
 * Różnica vs SuggestDestinationsUseCase:
 * - Tu: Budżet jest dzielony prosto przez dni (nie odejmujemy kosztów transportu).
 * - Tu: Wynik jest rankingiem punktowym, a nie filtrowaniem kaskadowym.
 */
class PrepareDestinationSuggestionsUseCase(
    private val destinationRepository: DestinationRepository
) {
    fun execute(prefs: Preferences): List<Destination> {
        val all = destinationRepository.getAllDestinations()

        val days = prefs.days.coerceAtLeast(1)
        // Uwaga: Zakładamy, że cały budżet jest na wydatki na miejscu (model uproszczony)
        val budgetPerDay = prefs.budget.toDouble() / days

        val scored = all.map { d ->
            var score = 0.0

            // 1. Punktacja za zgodność z preferencjami (Wagi)
            if (prefs.region == d.region) score += 3.0
            if (prefs.climate == d.climate) score += 2.0
            // Sprawdzenie tagów (np. "Zwiedzanie", "Relax")
            if (d.tags.any { it.equals(prefs.style, ignoreCase = true) }) score += 1.5

            // 2. Korekta budżetowa (Financial Ratio)
            // ratio < 1.0 oznacza, że użytkownika nie stać na minimalny budżet w tym miejscu
            val ratio = budgetPerDay / d.minBudgetPerDay.toDouble()

            when {
                ratio < 0.7 -> score -= 100.0 // Hard Reject: Mamy < 70% potrzebnej kwoty (odrzucamy)
                ratio < 1.0 -> score -= 3.0   // Soft Reject: Brakuje, ale mało (kara punktowa)
                ratio <= 1.6 -> score += 2.0  // Sweet Spot: Idealny budżet (nie za biednie, nie za bogato)
                else -> score += 1.0          // Stać nas z nawiązką
            }
            d to score
        }.filter { it.second > -50.0 } // Odsiewamy te z Hard Reject

        // Zwracamy TOP 3 najlepszych wyników
        return scored.sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }
}