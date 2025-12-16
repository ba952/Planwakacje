package com.example.wakacje1.data.model

import com.example.wakacje1.ui.DayPlan
import com.example.wakacje1.ui.Destination
import com.example.wakacje1.ui.Preferences
import kotlin.math.roundToInt

/**
 * Odpowiada wyłącznie za logikę generowania planu dni
 * na podstawie preferencji, miejsca i listy dostępnych aktywności.
 */
class PlanGenerator {

    /**
     * Główna funkcja – generuje listę DayPlan na cały wyjazd.
     *
     * @param prefs        – preferencje użytkownika (budżet, liczba dni, styl itd.)
     * @param destination  – wybrane miejsce
     * @param activities   – wszystkie dostępne aktywności (np. z ActivitiesRepository)
     */
    fun generatePlan(
        prefs: Preferences,
        destination: Destination,
        activities: List<ActivityTemplate>
    ): List<DayPlan> {
        val daysCount = prefs.days.coerceAtLeast(1)
        val budgetPerDay = (prefs.budget.toDouble() / daysCount).roundToInt()

        val result = mutableListOf<DayPlan>()

        for (dayNumber in 1..daysCount) {
            val baseSeed = dayNumber * 1000

            val morningTemplate = pickActivityForSlot(
                region = destination.region,
                style = prefs.style,
                preferredTypes = MORNING_TYPES,
                seed = baseSeed + 11,
                activities = activities
            )

            val middayTemplate = pickActivityForSlot(
                region = destination.region,
                style = prefs.style,
                preferredTypes = MIDDAY_TYPES,
                seed = baseSeed + 22,
                activities = activities,
                excludeId = morningTemplate?.id
            )

            val eveningTemplate = pickActivityForSlot(
                region = destination.region,
                style = prefs.style,
                preferredTypes = EVENING_TYPES,
                seed = baseSeed + 33,
                activities = activities,
                excludeId = middayTemplate?.id
            )

            val title = buildDayTitle(prefs.style, destination.displayName, dayNumber)
            val details = buildDetailsText(
                morningTemplate,
                middayTemplate,
                eveningTemplate,
                budgetPerDay
            )

            result.add(
                DayPlan(
                    day = dayNumber,
                    title = title,
                    details = details
                )
            )
        }

        return result
    }

    // --- Prywatne pomocnicze ---

    private fun buildDayTitle(style: String, placeName: String, dayNumber: Int): String =
        when (style) {
            "Relaks" -> "Dzień relaksu w $placeName"
            "Zwiedzanie" -> "Zwiedzanie $placeName"
            "Aktywny" -> "Aktywny dzień w $placeName"
            "Mix" -> "Mieszany dzień w $placeName"
            else -> "Dzień $dayNumber w $placeName"
        }

    private fun buildDetailsText(
        morning: ActivityTemplate?,
        midday: ActivityTemplate?,
        evening: ActivityTemplate?,
        budgetPerDay: Int
    ): String {

        fun slotText(label: String, template: ActivityTemplate?): String {
            val title = template?.title ?: "Czas wolny"
            val desc = template?.description
            return buildString {
                appendLine("$label: $title")
                if (!desc.isNullOrBlank()) {
                    appendLine("• $desc")
                }
                appendLine()
            }
        }

        return buildString {
            append(slotText("Poranek", morning))
            append(slotText("Południe", midday))
            append(slotText("Wieczór", evening))
            append("Orientacyjny budżet na ten dzień: ok. $budgetPerDay zł (bez dojazdu i noclegu).")
        }
    }

    /**
     * Wybiera jedną aktywność dla danej pory dnia, filtrując po regionie,
     * stylu podróży i typie aktywności (MORNING/MIDDAY/EVENING).
     */
    private fun pickActivityForSlot(
        region: String,
        style: String,
        preferredTypes: Set<ActivityType>,
        seed: Int,
        activities: List<ActivityTemplate>,
        excludeId: String? = null
    ): ActivityTemplate? {
        val candidates = activities.filter { t ->
            (t.suitableRegions.isEmpty() || region in t.suitableRegions) &&
                    (t.suitableStyles.isEmpty() || style in t.suitableStyles) &&
                    (t.type in preferredTypes)
        }

        if (candidates.isEmpty()) return null

        val filtered = if (excludeId != null) {
            candidates.filter { it.id != excludeId }
        } else {
            candidates
        }

        if (filtered.isEmpty()) return null

        val size = filtered.size
        val raw = seed % size
        val idx = if (raw < 0) raw + size else raw

        return filtered[idx]
    }

    companion object {
        // Mapowanie typów aktywności na pory dnia.
        private val MORNING_TYPES = setOf(
            ActivityType.CULTURE,
            ActivityType.NATURE,
            ActivityType.RELAX
        )

        private val MIDDAY_TYPES = setOf(
            ActivityType.NATURE,
            ActivityType.ACTIVE,
            ActivityType.CULTURE
        )

        private val EVENING_TYPES = setOf(
            ActivityType.FOOD,
            ActivityType.RELAX,
            ActivityType.NIGHT
        )
    }
}
