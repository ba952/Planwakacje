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

/**
 * Silnik (Engine) generujący harmonogram wycieczki.
 * Odpowiada za dobór atrakcji do poszczególnych dni i pór dnia (Slotów),
 * uwzględniając preferencje użytkownika, budżet oraz prognozę pogody.
 */
class PlanGenerator(
    private val stringProvider: StringProvider
) {

    /**
     * Generuje nowy, pełny plan wyjazdu.
     *
     * @param prefs Preferencje użytkownika (budżet, styl, długość pobytu).
     * @param dest Wybrana destynacja.
     * @param allActivities Pełna lista dostępnych aktywności (z repozytorium).
     * @param isBadWeatherForDayIndex Funkcja określająca, czy w dany dzień (index) przewidywana jest zła pogoda.
     */
    fun generateInternalPlan(
        prefs: Preferences,
        dest: Destination,
        allActivities: List<ActivityTemplate>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {
        val days = prefs.days.coerceAtLeast(1)
        val result = mutableListOf<InternalDayPlan>()

        // Zbiór użytych ID - zapobiega duplikatom atrakcji w całym planie
        val usedIds = mutableSetOf<String>()

        // Limit unikalnych (specjalnych) atrakcji na dzień, aby nie "przładować" planu zwiedzaniem
        val maxUniquePerDay = if (days < 7) 2 else 1

        for (i in 0 until days) {
            val badWeather = isBadWeatherForDayIndex(i)
            var uniqueToday = 0

            // Generowanie slotów: Rano -> Południe -> Wieczór
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

    // --- Metody edycji planu (Regeneracja) ---

    /**
     * Regeneruje (przelosowuje) cały dzień planu.
     * Przydatne, gdy użytkownikowi nie podoba się propozycja na dany dzień.
     * Dba o to, by nie wylosować atrakcji użytych w innych dniach.
     */
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

        // Zbieramy ID użyte w INNYCH dniach (excludeDayIndex), aby zachować globalną unikalność
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

    /**
     * Losuje pojedynczą atrakcję dla konkretnego slotu (np. "Wymień tylko to, co robię rano").
     */
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

        // Wykluczamy obecną atrakcję, żeby nie wylosować tego samego
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

    /**
     * Ustawia slot ręcznie (Custom Activity) - wpisane przez użytkownika.
     */
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

    /**
     * Konwertuje wewnętrzny model planu (InternalDayPlan) na model widoku (DayPlan).
     * Dodaje sformatowane nagłówki i opisy z zasobów.
     */
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

    /**
     * Główna funkcja algorytmiczna. Wybiera najlepszą aktywność dla danego slotu.
     * Etapy:
     * 1. Filtrowanie po regionie/stylu.
     * 2. Filtrowanie po typie (np. Wieczór -> Impreza/Kolacja).
     * 3. Filtrowanie po pogodzie (Indoor/Outdoor).
     * 4. Filtracja BUDŻETOWA (Heurystyka kosztów).
     * 5. Unikalność (Globalna i dzienna).
     */
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

        // 3. Pogoda (Indoor) - jeśli pada, szukamy atrakcji wewnątrz
        val indoorFiltered = if (preferIndoor) typed.filter { it.indoor } else emptyList()
        val pool0 = if (preferIndoor && indoorFiltered.isNotEmpty()) indoorFiltered else typed

        // 4. Wykluczenie (np. przy regeneracji konkretnego slotu)
        val pool1 = if (!excludeActivityId.isNullOrBlank()) {
            pool0.filter { it.id != excludeActivityId }.ifEmpty { pool0 }
        } else pool0

        if (pool1.isEmpty()) return fallbackSlot(slot, preferIndoor)

        // ====================================================================
        // IMPLEMENTACJA BUDŻETU
        // ====================================================================

        // A. Szacujemy "kieszonkowe" na slot.
        // Zakładamy, że 40% budżetu to wydatki na atrakcje/jedzenie (reszta to hotel/transport).
        val daysCount = prefs.days.coerceAtLeast(1)
        val spendingMoney = prefs.budget.toDouble() * 0.4
        val budgetPerSlot = spendingMoney / (daysCount * 3)

        // B. Odrzucamy atrakcje, które są za drogie wg naszej heurystyki
        val poolBudgetAware = pool1.filter { activity ->
            val cost = estimateCost(activity.type)
            cost <= budgetPerSlot
        }

        // C. Fallback: Jeśli budżet jest bardzo mały i wyciął wszystko,
        // bierzemy 5 najtańszych opcji, aby nie zwracać pustego planu (UX).
        val poolAfterBudget = if (poolBudgetAware.isNotEmpty()) {
            poolBudgetAware
        } else {
            pool1.sortedBy { estimateCost(it.type) }.take(5)
        }

        // ====================================================================

        // 5. Unikalność w dniu (np. max 2 muzea dziennie)
        val pool2 = if (uniqueUsedToday >= maxUniquePerDay) {
            poolAfterBudget.filter { it.destinationId == null }.ifEmpty { poolAfterBudget }
        } else {
            poolAfterBudget
        }

        // 6. Globalna unikalność (nie powtarzaj tego co wczoraj)
        val poolNoRepeat = usedIds?.let { used -> pool2.filter { it.id !in used } }.orEmpty()

        // Finalne losowanie z puli kandydatów
        val chosen = if (poolNoRepeat.isNotEmpty()) poolNoRepeat.random() else pool2.random()

        usedIds?.add(chosen.id)
        return toSlotPlan(chosen)
    }

    /**
     * HEURYSTYKA CENOWA:
     * Ponieważ baza JSON (ActivityTemplate) nie zawiera dokładnych cen biletów,
     * system szacuje koszt na podstawie TYPU aktywności.
     * Pozwala to uwzględnić budżet użytkownika bez konieczności przebudowy całej bazy danych.
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

    // Mapowanie pory dnia na preferowane typy aktywności
    private fun preferredTypesFor(slot: DaySlot): Set<ActivityType> = when (slot) {
        DaySlot.MORNING -> setOf(ActivityType.HISTORY, ActivityType.CULTURE, ActivityType.NATURE, ActivityType.ACTIVE)
        DaySlot.MIDDAY -> setOf(ActivityType.FOOD, ActivityType.CULTURE, ActivityType.HISTORY, ActivityType.ACTIVE)
        DaySlot.EVENING -> setOf(ActivityType.RELAX, ActivityType.NIGHT, ActivityType.FOOD, ActivityType.CULTURE)
    }

    // Sprawdza dopasowanie aktywności do preferencji (Region/Styl/Destynacja)
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

    // Zwraca domyślny slot "zapchajdziurę" w przypadku braku pasujących aktywności
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

    // Zbiera ID wszystkich aktywności użytych w planie (z opcją wykluczenia dnia/slotu)
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