package com.example.wakacje1.domain.engine

import com.example.wakacje1.R
import com.example.wakacje1.domain.model.*
import com.example.wakacje1.domain.util.StringProvider
import kotlin.collections.plusAssign
import com.example.wakacje1.domain.assets.ActivityTemplate
import com.example.wakacje1.domain.assets.ActivityType

/**
 * Silnik (Engine) generujący harmonogram wycieczki.
 *
 * Główne funkcje:
 * 1. Waterfall Budgeting: Oblicza budżet netto (po odjęciu hotelu, transportu i rezerwy).
 * 2. Geo-Validation: Sprawdza zgodność regionu atrakcji z miastem.
 * 3. Sticky Transport: Przyjmuje konkretny koszt transportu (zależny od scenariusza).
 * 4. Recycling: W razie braku nowych atrakcji, używa ponownie tych pasujących.
 * 5. Blacklist: Obsługuje listę ignorowanych atrakcji przy przelosowywaniu.
 */
class PlanGenerator(
    private val stringProvider: StringProvider
) {
    // Stałe ekonomiczne
    private val BASE_TYPICAL_BUDGET = 450.0
    private val ACCOMMODATION_RATIO = 0.45  // 45% typowego budżetu to hotel
    private val SAFETY_BUFFER_PERCENT = 0.10 // 10% na "czarną godzinę"

    /**
     * Generuje CAŁY nowy plan od zera.
     */
    fun generateInternalPlan(
        prefs: Preferences,
        dest: Destination,
        transportCost: Int, // Konkretny koszt (zależny od scenariusza T_MIN/AVG/MAX)
        allActivities: List<ActivityTemplate>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {
        val days = prefs.days.coerceAtLeast(1)
        val result = mutableListOf<InternalDayPlan>()
        val usedIds = mutableSetOf<String>()
        val maxUniquePerDay = if (days < 7) 2 else 1

        // --- 1. ANALIZA EKONOMICZNA ---
        val budgetAfterSafety = prefs.budget * (1.0 - SAFETY_BUFFER_PERCENT)

        // Estymacja Hotelu (zawsze jako % typowego budżetu miasta)
        val typicalDaily = dest.typicalBudgetPerDay.toDouble()
        val estimatedHotelTotal = (typicalDaily * ACCOMMODATION_RATIO) * days

        // Netto na życie (Atrakcje + Jedzenie)
        val totalNetForFun = (budgetAfterSafety - transportCost - estimatedHotelTotal).coerceAtLeast(0.0)

        // Czy budżet jest krytycznie niski? (< 50% minimum)
        val minNetNeeded = (dest.minBudgetPerDay * 0.5) * days
        val isBelowMinimum = totalNetForFun < minNetNeeded

        val cityPriceMultiplier = typicalDaily / BASE_TYPICAL_BUDGET

        // --- 2. PRZYGOTOWANIE TALII ---
        val validActivities = allActivities.filter { matchesPrefs(it, prefs, dest) }
        val (uniqueForDest, generic) = validActivities.partition {
            it.destinationId.equals(dest.id, ignoreCase = true)
        }

        val deck = (uniqueForDest.shuffled() + generic.shuffled()).toMutableList()
        val allValidCandidates = validActivities // Do recyklingu

        // --- 3. GENEROWANIE DNI ---
        for (i in 0 until days) {
            val badWeather = isBadWeatherForDayIndex(i)
            var uniqueToday = 0

            val morning = pickFromDeck(DaySlot.MORNING, deck, allValidCandidates, badWeather, days, totalNetForFun, uniqueToday, maxUniquePerDay, cityPriceMultiplier, isBelowMinimum, dest)
            usedIds.addIfValid(morning.baseActivityId)
            if (isUniqueActivity(morning, uniqueForDest)) uniqueToday++

            val midday = pickFromDeck(DaySlot.MIDDAY, deck, allValidCandidates, badWeather, days, totalNetForFun, uniqueToday, maxUniquePerDay, cityPriceMultiplier, isBelowMinimum, dest)
            usedIds.addIfValid(midday.baseActivityId)
            if (isUniqueActivity(midday, uniqueForDest)) uniqueToday++

            val evening = pickFromDeck(DaySlot.EVENING, deck, allValidCandidates, badWeather, days, totalNetForFun, uniqueToday, maxUniquePerDay, cityPriceMultiplier, isBelowMinimum, dest)
            usedIds.addIfValid(evening.baseActivityId)

            result += InternalDayPlan(i + 1, morning, midday, evening)
        }

        return result
    }

    /**
     * Regeneruje JEDEN dzień.
     */
    fun regenerateDay(
        dayIndex: Int,
        prefs: Preferences,
        dest: Destination,
        transportCost: Int,
        allActivities: List<ActivityTemplate>,
        internal: MutableList<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ) {
        if (dayIndex !in internal.indices) return

        val days = prefs.days.coerceAtLeast(1)
        val maxUniquePerDay = if (days < 7) 2 else 1
        val badWeather = isBadWeatherForDayIndex(dayIndex)

        // Ekonomia (od nowa)
        val budgetAfterSafety = prefs.budget * (1.0 - SAFETY_BUFFER_PERCENT)
        val typicalDaily = dest.typicalBudgetPerDay.toDouble()
        val estimatedHotelTotal = (typicalDaily * ACCOMMODATION_RATIO) * days
        val totalNetForFun = (budgetAfterSafety - transportCost - estimatedHotelTotal).coerceAtLeast(0.0)

        val isBelowMinimum = totalNetForFun < ((dest.minBudgetPerDay * 0.5) * days)
        val cityPriceMultiplier = typicalDaily / BASE_TYPICAL_BUDGET

        // Wykluczamy ID użyte w INNYCH dniach
        val usedIds = collectUsedIds(internal, excludeDayIndex = dayIndex)
        val validActivities = allActivities.filter { matchesPrefs(it, prefs, dest) && it.id !in usedIds }

        val (uniqueForDest, generic) = validActivities.partition {
            it.destinationId.equals(dest.id, ignoreCase = true)
        }
        val deck = (uniqueForDest.shuffled() + generic.shuffled()).toMutableList()
        val allValidForRecycle = allActivities.filter { matchesPrefs(it, prefs, dest) }

        var uniqueToday = 0
        val morning = pickFromDeck(DaySlot.MORNING, deck, allValidForRecycle, badWeather, days, totalNetForFun, uniqueToday, maxUniquePerDay, cityPriceMultiplier, isBelowMinimum, dest)
        if (isUniqueActivity(morning, uniqueForDest)) uniqueToday++

        val midday = pickFromDeck(DaySlot.MIDDAY, deck, allValidForRecycle, badWeather, days, totalNetForFun, uniqueToday, maxUniquePerDay, cityPriceMultiplier, isBelowMinimum, dest)
        if (isUniqueActivity(midday, uniqueForDest)) uniqueToday++

        val evening = pickFromDeck(DaySlot.EVENING, deck, allValidForRecycle, badWeather, days, totalNetForFun, uniqueToday, maxUniquePerDay, cityPriceMultiplier, isBelowMinimum, dest)

        internal[dayIndex] = InternalDayPlan(dayIndex + 1, morning, midday, evening)
    }

    /**
     * Wymienia JEDEN slot. Obsługuje 'ignoredIds' (czarną listę).
     */
    fun rollNewSlot(
        dayIndex: Int,
        slot: DaySlot,
        prefs: Preferences,
        dest: Destination,
        transportCost: Int,
        allActivities: List<ActivityTemplate>,
        internal: MutableList<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean,
        ignoredIds: Set<String> // Czarna lista z ViewModelu
    ) {
        if (dayIndex !in internal.indices) return

        val days = prefs.days.coerceAtLeast(1)
        val maxUniquePerDay = if (days < 7) 2 else 1
        val badWeather = isBadWeatherForDayIndex(dayIndex)

        // Ekonomia
        val budgetAfterSafety = prefs.budget * (1.0 - SAFETY_BUFFER_PERCENT)
        val typicalDaily = dest.typicalBudgetPerDay.toDouble()
        val estimatedHotelTotal = (typicalDaily * ACCOMMODATION_RATIO) * days
        val totalNetForFun = (budgetAfterSafety - transportCost - estimatedHotelTotal).coerceAtLeast(0.0)

        val isBelowMinimum = totalNetForFun < ((dest.minBudgetPerDay.toDouble() * 0.5) * days)
        val cityPriceMultiplier = typicalDaily / BASE_TYPICAL_BUDGET

        val currentDay = internal[dayIndex]
        val uniqueTemplates = allActivities.filter { it.destinationId.equals(dest.id, true) }
        var uniqueToday = 0

        if (slot != DaySlot.MORNING && isUniqueActivity(currentDay.morning, uniqueTemplates)) uniqueToday++
        if (slot != DaySlot.MIDDAY && isUniqueActivity(currentDay.midday, uniqueTemplates)) uniqueToday++
        if (slot != DaySlot.EVENING && isUniqueActivity(currentDay.evening, uniqueTemplates)) uniqueToday++

        // Zbieramy zużyte ID z całego planu + Czarną Listę Sesji
        val usedIds = collectUsedIds(internal)
        usedIds.addAll(ignoredIds)

        val validActivities = allActivities.filter { matchesPrefs(it, prefs, dest) && it.id !in usedIds }
        val (uniqueForDest, generic) = validActivities.partition {
            it.destinationId.equals(dest.id, ignoreCase = true)
        }
        val deck = (uniqueForDest.shuffled() + generic.shuffled()).toMutableList()
        val allValidForRecycle = allActivities.filter { matchesPrefs(it, prefs, dest) }

        val newSlot = pickFromDeck(slot, deck, allValidForRecycle, badWeather, days, totalNetForFun, uniqueToday, maxUniquePerDay, cityPriceMultiplier, isBelowMinimum, dest)

        internal[dayIndex] = when (slot) {
            DaySlot.MORNING -> internal[dayIndex].copy(morning = newSlot)
            DaySlot.MIDDAY -> internal[dayIndex].copy(midday = newSlot)
            DaySlot.EVENING -> internal[dayIndex].copy(evening = newSlot)
        }
    }

    // --- LOGIKA WYBORU ---

    private fun pickFromDeck(
        slot: DaySlot,
        deck: MutableList<ActivityTemplate>,
        allCandidates: List<ActivityTemplate>,
        isBadWeather: Boolean,
        totalDays: Int,
        totalNetBudget: Double,
        uniqueUsedToday: Int,
        maxUniquePerDay: Int,
        priceMultiplier: Double,
        isBelowMinimum: Boolean,
        dest: Destination
    ): SlotPlan {
        val budgetPerSlot = totalNetBudget / (totalDays * 3)
        val preferredTypes = preferredTypesFor(slot)
        val iterator = deck.iterator()

        // FAZA 1: Idealne z talii
        while (iterator.hasNext()) {
            val candidate = iterator.next()

            // Strict Geo Check
            if (!isGeographicallyValid(candidate, dest)) {
                iterator.remove()
                continue
            }

            val isUnique = candidate.destinationId.equals(dest.id, ignoreCase = true)

            if (isUnique && uniqueUsedToday >= maxUniquePerDay) continue
            if (isBadWeather && !candidate.indoor) continue
            if (candidate.type !in preferredTypes) continue

            val cost = estimateCost(candidate.type, priceMultiplier)
            // Biedny student: tylko tanie lub must-see
            if (isBelowMinimum && cost > 20 && !isUnique) continue
            // Bogaty student: limit per slot
            if (cost > budgetPerSlot) continue

            iterator.remove()
            return toSlotPlan(candidate)
        }

        // FAZA 2: Poluzowanie budżetu
        val iter2 = deck.iterator()
        while (iter2.hasNext()) {
            val candidate = iter2.next()
            if (!isGeographicallyValid(candidate, dest)) continue

            val isUnique = candidate.destinationId.equals(dest.id, ignoreCase = true)
            val cost = estimateCost(candidate.type, priceMultiplier)

            if (isBelowMinimum && cost > 20 && !isUnique) continue
            if (isUnique && uniqueUsedToday >= maxUniquePerDay) continue
            if (isBadWeather && !candidate.indoor) continue
            if (candidate.type !in preferredTypes) continue

            iter2.remove()
            return toSlotPlan(candidate)
        }

        // FAZA 3: Recykling (Powtórki z 'allCandidates')
        val recycleCandidates = allCandidates.filter { candidate ->
            isGeographicallyValid(candidate, dest) &&
                    (!isBadWeather || candidate.indoor) &&
                    (candidate.type in preferredTypes)
        }

        if (recycleCandidates.isNotEmpty()) {
            return toSlotPlan(recycleCandidates.random())
        }

        // FAZA 4: Fallback
        return fallbackSlot(slot, isBadWeather)
    }

    // --- HELPERY ---

    private fun isGeographicallyValid(activity: ActivityTemplate, dest: Destination): Boolean {
        if (activity.suitableRegions.isEmpty()) return true
        return activity.suitableRegions.any { it.equals(dest.region, ignoreCase = true) }
    }

    private fun estimateCost(type: ActivityType, multiplier: Double): Int {
        val base = when (type) {
            ActivityType.NIGHT -> 120
            ActivityType.FOOD -> 100
            ActivityType.ACTIVE -> 60
            ActivityType.HISTORY, ActivityType.CULTURE -> 50
            ActivityType.NATURE -> 10
            ActivityType.RELAX -> 0
            else -> 20
        }
        return (base * multiplier).toInt()
    }

    private fun isUniqueActivity(slot: SlotPlan, list: List<ActivityTemplate>): Boolean =
        slot.baseActivityId != null && list.any { it.id == slot.baseActivityId }

    private fun MutableSet<String>.addIfValid(id: String?) { if (!id.isNullOrBlank()) add(id) }

    private fun collectUsedIds(internal: List<InternalDayPlan>, excludeDayIndex: Int? = null): MutableSet<String> {
        val used = mutableSetOf<String>()
        fun add(id: String?) { if (!id.isNullOrBlank()) used.add(id) }
        internal.forEachIndexed { idx, day ->
            if (excludeDayIndex != null && idx == excludeDayIndex) { /*skip*/ } else {
                add(day.morning.baseActivityId)
                add(day.midday.baseActivityId)
                add(day.evening.baseActivityId)
            }
        }
        return used
    }

    private fun matchesPrefs(a: ActivityTemplate, prefs: Preferences, dest: Destination): Boolean {
        val destinationOk = a.destinationId == null || a.destinationId.equals(dest.id, ignoreCase = true)
        val regionOk = a.suitableRegions.isEmpty() ||
                a.suitableRegions.any { it.equals(prefs.region, ignoreCase = true) } ||
                a.suitableRegions.any { it.equals(dest.region, ignoreCase = true) }
        val styleOk = a.suitableStyles.isEmpty() || a.suitableStyles.any { it.equals(prefs.style, ignoreCase = true) }
        return destinationOk && regionOk && styleOk
    }

    private fun preferredTypesFor(slot: DaySlot): Set<ActivityType> = when (slot) {
        DaySlot.MORNING -> setOf(ActivityType.HISTORY, ActivityType.CULTURE, ActivityType.NATURE, ActivityType.ACTIVE)
        DaySlot.MIDDAY -> setOf(ActivityType.FOOD, ActivityType.CULTURE, ActivityType.HISTORY, ActivityType.ACTIVE)
        DaySlot.EVENING -> setOf(ActivityType.RELAX, ActivityType.NIGHT, ActivityType.FOOD, ActivityType.CULTURE)
    }

    private fun toSlotPlan(a: ActivityTemplate) = SlotPlan(a.id, a.title, a.description, a.indoor)

    private fun fallbackSlot(slot: DaySlot, preferIndoor: Boolean): SlotPlan {
        val titleRes = when (slot) {
            DaySlot.MORNING -> R.string.fallback_morning_title
            DaySlot.MIDDAY -> R.string.fallback_midday_title
            DaySlot.EVENING -> R.string.fallback_evening_title
        }
        val descRes = if (preferIndoor) R.string.fallback_desc_indoor else R.string.fallback_desc_outdoor
        return SlotPlan(null, stringProvider.getString(titleRes), stringProvider.getString(descRes), preferIndoor)
    }

    fun setCustomSlot(dayIndex: Int, slot: DaySlot, title: String, description: String, internal: MutableList<InternalDayPlan>) {
        if (dayIndex !in internal.indices) return
        val current = internal[dayIndex]
        val custom = SlotPlan(null, title.trim(), description.trim(), false)
        internal[dayIndex] = when (slot) {
            DaySlot.MORNING -> current.copy(morning = custom)
            DaySlot.MIDDAY -> current.copy(midday = custom)
            DaySlot.EVENING -> current.copy(evening = custom)
        }
    }

    fun rebuildDayPlans(internalDays: List<InternalDayPlan>, destinationName: String): List<DayPlan> {
        return internalDays.map { d ->
            val details = buildString {
                append("${stringProvider.getString(R.string.label_morning)} ${d.morning.title}")
                if (d.morning.description.isNotBlank()) append("\n- ${d.morning.description}")
                append("\n\n${stringProvider.getString(R.string.label_midday)} ${d.midday.title}")
                if (d.midday.description.isNotBlank()) append("\n- ${d.midday.description}")
                append("\n\n${stringProvider.getString(R.string.label_evening)} ${d.evening.title}")
                if (d.evening.description.isNotBlank()) append("\n- ${d.evening.description}")
            }
            val title = stringProvider.getString(R.string.plan_day_title, d.day, destinationName)
            DayPlan(d.day, title, details, DaySlotsUi(SlotUi(d.morning.title, d.morning.description), SlotUi(d.midday.title, d.midday.description), SlotUi(d.evening.title, d.evening.description)))
        }
    }
}