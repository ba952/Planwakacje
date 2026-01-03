package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences

class PrepareDestinationSuggestionsUseCase(
    private val destinationRepository: DestinationRepository
) {
    fun execute(prefs: Preferences): List<Destination> {
        val all = destinationRepository.getAllDestinations()

        val days = prefs.days.coerceAtLeast(1)
        val budgetPerDay = prefs.budget.toDouble() / days

        val scored = all.map { d ->
            var score = 0.0
            if (prefs.region == d.region) score += 3.0
            if (prefs.climate == d.climate) score += 2.0
            if (d.tags.any { it.equals(prefs.style, ignoreCase = true) }) score += 1.5

            val ratio = budgetPerDay / d.minBudgetPerDay.toDouble()
            when {
                ratio < 0.7 -> score -= 100.0
                ratio < 1.0 -> score -= 3.0
                ratio <= 1.6 -> score += 2.0
                else -> score += 1.0
            }
            d to score
        }.filter { it.second > -50.0 }

        return scored.sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }
}
