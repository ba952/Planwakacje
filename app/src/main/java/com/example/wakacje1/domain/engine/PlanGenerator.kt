package com.example.wakacje1.domain.engine

import com.example.wakacje1.R
import com.example.wakacje1.data.assets.ActivityTemplate
import com.example.wakacje1.data.assets.ActivityType
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
import com.example.wakacje1.domain.util.StringProvider
import kotlin.collections.plusAssign

class PlanGenerator(
    private val stringProvider: StringProvider
) {

    fun generateInternalPlan(
        prefs: Preferences,
        dest: Destination,
        allActivities: List<ActivityTemplate>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {
        val days = prefs.days.coerceAtLeast(1)
        val result = mutableListOf<InternalDayPlan>()

        // Zbiór użytych ID, żeby nie powtarzać atrakcji w całym wyjeździe
        val usedIds = mutableSetOf<String>()

        // Limit unikalnych atrakcji na dzień (żeby nie przeładować planu)
        val maxUniquePerDay = if (days < 7) 2 else 1

        for (i in 0 until days) {
            val badWeather = isBadWeatherForDayIndex(i)
            var uniqueToday = 0

            val morning = pickForSlot(
                slot = DaySlot.MORNING,
                prefs = prefs,
                dest = dest,
                all = allActivities,
                preferIndoor = badWeather,
                usedIds = usedIds,
                uniqueUsedToday = uniqueToday,
                maxUniquePerDay = maxUniquePerDay
            )
            if (isUnique(morning)) uniqueToday++

            val midday = pickForSlot(
                slot = DaySlot.MIDDAY,
                prefs = prefs,
                dest = dest,
                all = allActivities,
                preferIndoor = badWeather,
                usedIds = usedIds,
                uniqueUsedToday = uniqueToday,
                maxUniquePerDay = maxUniquePerDay
            )
            if (isUnique(midday)) uniqueToday++

            val evening = pickForSlot(
                slot = DaySlot.EVENING,
                prefs = prefs,
                dest = dest,
                all = allActivities,
                preferIndoor = badWeather,
                usedIds = usedIds,
                uniqueUsedToday = uniqueToday,
                maxUniquePerDay = maxUniquePerDay
            )

            result += InternalDayPlan(
                day = i + 1,
                morning = morning,
                midday = midday,
                evening = evening
            )
        }

        return result
    }

    // --- Metody do regeneracji i edycji (Logic) ---

    // NAPRAWA: Scalono regenerateWholeDay i regenerateDay w jedną funkcję.
    fun regenerateDay(
        dayIndex: Int,
        prefs: Preferences,
        dest: Destination,
        allActivities: List<ActivityTemplate>,
        internal: MutableList<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ) {
        if (dayIndex !in internal.indices) return
        val badWeather = isBadWeatherForDayIndex(dayIndex)

        val days = prefs.days.coerceAtLeast(1)
        val maxUniquePerDay = if (days < 7) 3 else 2
        // Ważne: zbieramy ID użyte w INNYCH dniach, żeby nie wylosować tego samego
        val usedIds = collectUsedIds(internal, excludeDayIndex = dayIndex)

        var uniqueToday = 0
        val morning = pickForSlot(DaySlot.MORNING, prefs, dest, allActivities, badWeather, usedIds = usedIds, uniqueUsedToday = uniqueToday, maxUniquePerDay = maxUniquePerDay)
        if (isUnique(morning)) uniqueToday++

        val midday = pickForSlot(DaySlot.MIDDAY, prefs, dest, allActivities, badWeather, usedIds = usedIds, uniqueUsedToday = uniqueToday, maxUniquePerDay = maxUniquePerDay)
        if (isUnique(midday)) uniqueToday++

        val evening = pickForSlot(DaySlot.EVENING, prefs, dest, allActivities, badWeather, usedIds = usedIds, uniqueUsedToday = uniqueToday, maxUniquePerDay = maxUniquePerDay)

        internal[dayIndex] = InternalDayPlan(
            day = dayIndex + 1,
            morning = morning,
            midday = midday,
            evening = evening
        )
    }

    fun rollNewSlot(
        dayIndex: Int,
        slot: DaySlot,
        prefs: Preferences,
        dest: Destination,
        allActivities: List<ActivityTemplate>,
        internal: MutableList<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ) {
        if (dayIndex !in internal.indices) return
        val badWeather = isBadWeatherForDayIndex(dayIndex)
        val current = internal[dayIndex]

        val days = prefs.days.coerceAtLeast(1)
        val maxUniquePerDay = if (days < 7) 3 else 2

        val excludeId = when (slot) {
            DaySlot.MORNING -> current.morning.baseActivityId
            DaySlot.MIDDAY -> current.midday.baseActivityId
            DaySlot.EVENING -> current.evening.baseActivityId
        }

        val usedIds = collectUsedIds(internal, excludeDayIndex = dayIndex, excludeSlot = slot)
        val uniqueAlreadyToday = countUniqueInDayExcludingSlot(current, slot)

        val newSlot = pickForSlot(
            slot = slot,
            prefs = prefs,
            dest = dest,
            all = allActivities,
            preferIndoor = badWeather,
            excludeActivityId = excludeId,
            usedIds = usedIds,
            uniqueUsedToday = uniqueAlreadyToday,
            maxUniquePerDay = maxUniquePerDay
        )

        internal[dayIndex] = when (slot) {
            DaySlot.MORNING -> current.copy(morning = newSlot)
            DaySlot.MIDDAY -> current.copy(midday = newSlot)
            DaySlot.EVENING -> current.copy(evening = newSlot)
        }
    }

    fun setCustomSlot(
        dayIndex: Int,
        slot: DaySlot,
        title: String,
        description: String,
        internal: MutableList<InternalDayPlan>
    ) {
        if (dayIndex !in internal.indices) return
        val current = internal[dayIndex]

        val custom = SlotPlan(
            baseActivityId = null,
            title = title.trim(),
            description = description.trim(),
            indoor = false
        )

        internal[dayIndex] = when (slot) {
            DaySlot.MORNING -> current.copy(morning = custom)
            DaySlot.MIDDAY -> current.copy(midday = custom)
            DaySlot.EVENING -> current.copy(evening = custom)
        }
    }

    fun rebuildDayPlans(
        internalDays: List<InternalDayPlan>,
        destinationName: String
    ): List<DayPlan> {
        return internalDays.map { d ->
            val details = buildString {
                append("${stringProvider.getString(R.string.label_morning)} ${d.morning.title}")
                if (d.morning.description.isNotBlank()) append("\n- ${d.morning.description}")

                append("\n\n${stringProvider.getString(R.string.label_midday)} ${d.midday.title}")
                if (d.midday.description.isNotBlank()) append("\n- ${d.midday.description}")

                append("\n\n${stringProvider.getString(R.string.label_evening)} ${d.evening.title}")
                if (d.evening.description.isNotBlank()) append("\n- ${d.evening.description}")
            }

            // NAPRAWA: Użycie zasobu string zamiast hardcodowania "Dzień X — Y"
            val title = stringProvider.getString(
                R.string.plan_day_title,
                d.day,
                destinationName
            )

            DayPlan(
                day = d.day,
                title = title,
                details = details
            )
        }
    }

    // ------------------------------------------------------------------------
    // CORE LOGIC - ALGORYTM WYBORU Z BUDŻETEM
    // ------------------------------------------------------------------------

    private fun pickForSlot(
        slot: DaySlot,
        prefs: Preferences,
        dest: Destination,
        all: List<ActivityTemplate>,
        preferIndoor: Boolean,
        excludeActivityId: String? = null,
        usedIds: MutableSet<String>? = null,
        uniqueUsedToday: Int,
        maxUniquePerDay: Int
    ): SlotPlan {
        if (all.isEmpty()) return fallbackSlot(slot, preferIndoor)

        // 1. Filtracja twarda: Region i Styl
        val strict = all.filter { matchesPrefs(it, prefs, dest) }
        val base = if (strict.isNotEmpty()) strict else all

        // 2. Preferowane typy dla pory dnia (np. Rano = Kultura/Natura)
        val preferredTypes = preferredTypesFor(slot)
        val typed = base.filter { it.type in preferredTypes }.ifEmpty { base }

        // 3. Pogoda (Indoor)
        val indoorFiltered = if (preferIndoor) typed.filter { it.indoor } else emptyList()
        val pool0 = if (preferIndoor && indoorFiltered.isNotEmpty()) indoorFiltered else typed

        // 4. Wykluczenie aktualnie edytowanej (żeby "Losuj" dało coś nowego)
        val pool1 = if (!excludeActivityId.isNullOrBlank()) {
            pool0.filter { it.id != excludeActivityId }.ifEmpty { pool0 }
        } else pool0

        if (pool1.isEmpty()) return fallbackSlot(slot, preferIndoor)

        // ====================================================================
        // IMPLEMENTACJA BUDŻETU (Bez zmian w JSON)
        // ====================================================================

        // A. Obliczamy ile użytkownik ma "kieszonkowego" na jeden slot.
        // Zakładamy, że około 40% całkowitego budżetu to wydatki na atrakcje/jedzenie
        // (reszta to nocleg i transport). Dzielimy przez liczbę dni i 3 sloty.
        val daysCount = prefs.days.coerceAtLeast(1)
        val spendingMoney = prefs.budget.toDouble() * 0.4
        val budgetPerSlot = spendingMoney / (daysCount * 3)

        // B. Filtrujemy atrakcje, których "szacowany koszt" jest za wysoki
        val poolBudgetAware = pool1.filter { activity ->
            val cost = estimateCost(activity.type)
            cost <= budgetPerSlot
        }

        // C. Fallback: Jeśli użytkownik jest bardzo biedny i filtr wyciął wszystko,
        // bierzemy 3 najtańsze opcje z pierwotnej puli (żeby nie zwracać pustego planu).
        // W przeciwnym razie bierzemy przefiltrowaną listę.
        val poolAfterBudget = if (poolBudgetAware.isNotEmpty()) {
            poolBudgetAware
        } else {
            // Sortujemy po szacowanej cenie rosnąco i bierzemy 5 najtańszych
            pool1.sortedBy { estimateCost(it.type) }.take(5)
        }

        // ====================================================================

        // 5. Unikalność w dniu (nie za dużo unikatów w jeden dzień)
        val pool2 = if (uniqueUsedToday >= maxUniquePerDay) {
            poolAfterBudget.filter { it.destinationId == null }.ifEmpty { poolAfterBudget }
        } else {
            poolAfterBudget
        }

        // 6. Globalna unikalność (nie powtarzaj tego co wczoraj)
        val poolNoRepeat = usedIds?.let { used -> pool2.filter { it.id !in used } }.orEmpty()

        // Finalne losowanie
        val chosen = if (poolNoRepeat.isNotEmpty()) poolNoRepeat.random() else pool2.random()

        usedIds?.add(chosen.id)
        return toSlotPlan(chosen)
    }

    /**
     * HEURYSTYKA CENOWA:
     * Ponieważ baza JSON nie zawiera cen, szacujemy koszt na podstawie TYPU aktywności.
     * To pozwala uwzględnić budżet użytkownika bez zmian w strukturze danych.
     */
    private fun estimateCost(type: ActivityType): Int {
        return when (type) {
            // Drogie
            ActivityType.NIGHT -> 120  // Kluby, imprezy
            ActivityType.FOOD -> 100   // Restauracje

            // Średnie
            ActivityType.ACTIVE -> 60  // Sport, parki linowe
            ActivityType.HISTORY -> 50 // Muzea, zamki
            ActivityType.CULTURE -> 50 // Galerie

            // Tanie / Darmowe
            ActivityType.RELAX -> 0    // Plaża, park
            ActivityType.NATURE -> 10  // Szlak, punkt widokowy
            else -> 20                 // Inne (Mix)
        }
    }

    private fun preferredTypesFor(slot: DaySlot): Set<ActivityType> = when (slot) {
        DaySlot.MORNING -> setOf(ActivityType.HISTORY, ActivityType.CULTURE, ActivityType.NATURE, ActivityType.ACTIVE)
        DaySlot.MIDDAY -> setOf(ActivityType.FOOD, ActivityType.CULTURE, ActivityType.HISTORY, ActivityType.ACTIVE)
        DaySlot.EVENING -> setOf(ActivityType.RELAX, ActivityType.NIGHT, ActivityType.FOOD, ActivityType.CULTURE)
    }

    private fun matchesPrefs(a: ActivityTemplate, prefs: Preferences, dest: Destination): Boolean {
        val destinationOk = a.destinationId == null || a.destinationId.equals(dest.id, ignoreCase = true)
        val regionOk =
            a.suitableRegions.isEmpty() ||
                    a.suitableRegions.any { it.equals(prefs.region, ignoreCase = true) } ||
                    a.suitableRegions.any { it.equals(dest.region, ignoreCase = true) }

        val styleOk =
            a.suitableStyles.isEmpty() ||
                    a.suitableStyles.any { it.equals(prefs.style, ignoreCase = true) }

        return destinationOk && regionOk && styleOk
    }

    private fun toSlotPlan(a: ActivityTemplate): SlotPlan {
        return SlotPlan(
            baseActivityId = a.id,
            title = a.title,
            description = a.description,
            indoor = a.indoor
        )
    }

    private fun fallbackSlot(slot: DaySlot, preferIndoor: Boolean): SlotPlan {
        val titleRes = when (slot) {
            DaySlot.MORNING -> R.string.fallback_morning_title
            DaySlot.MIDDAY -> R.string.fallback_midday_title
            DaySlot.EVENING -> R.string.fallback_evening_title
        }

        val descRes = when {
            preferIndoor -> R.string.fallback_desc_indoor
            else -> R.string.fallback_desc_outdoor
        }

        return SlotPlan(
            baseActivityId = null,
            title = stringProvider.getString(titleRes),
            description = stringProvider.getString(descRes),
            indoor = preferIndoor
        )
    }

    private fun collectUsedIds(
        internal: List<InternalDayPlan>,
        excludeDayIndex: Int? = null,
        excludeSlot: DaySlot? = null
    ): MutableSet<String> {
        val used = mutableSetOf<String>()
        fun add(id: String?) { if (!id.isNullOrBlank()) used.add(id) }

        internal.forEachIndexed { idx, day ->
            if (excludeDayIndex != null && idx == excludeDayIndex) {
                if (excludeSlot == null) return@forEachIndexed
                when (excludeSlot) {
                    DaySlot.MORNING -> { add(day.midday.baseActivityId); add(day.evening.baseActivityId) }
                    DaySlot.MIDDAY -> { add(day.morning.baseActivityId); add(day.evening.baseActivityId) }
                    DaySlot.EVENING -> { add(day.morning.baseActivityId); add(day.midday.baseActivityId) }
                }
            } else {
                add(day.morning.baseActivityId)
                add(day.midday.baseActivityId)
                add(day.evening.baseActivityId)
            }
        }
        return used
    }

    private fun isUnique(slot: SlotPlan): Boolean = slot.baseActivityId != null && slot.baseActivityId!!.startsWith("u_")

    private fun countUniqueInDayExcludingSlot(day: InternalDayPlan, exclude: DaySlot): Int {
        fun isUniqueId(id: String?): Boolean = !id.isNullOrBlank() && id.startsWith("u_")
        val m = if (exclude != DaySlot.MORNING) isUniqueId(day.morning.baseActivityId) else false
        val d = if (exclude != DaySlot.MIDDAY) isUniqueId(day.midday.baseActivityId) else false
        val e = if (exclude != DaySlot.EVENING) isUniqueId(day.evening.baseActivityId) else false
        return listOf(m, d, e).count { it }
    }
}