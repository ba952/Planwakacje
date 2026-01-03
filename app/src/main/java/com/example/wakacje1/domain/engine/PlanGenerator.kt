package com.example.wakacje1.domain.engine

import com.example.wakacje1.data.assets.ActivityTemplate
import com.example.wakacje1.data.assets.ActivityType
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
import kotlin.collections.plusAssign

object PlanGenerator {

    fun generateInternalPlan(
        prefs: Preferences,
        dest: Destination,
        allActivities: List<ActivityTemplate>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {
        val days = prefs.days.coerceAtLeast(1)
        val result = mutableListOf<InternalDayPlan>()

        // globalny zbiór użytych aktywności (miękka unikalność)
        val usedIds = mutableSetOf<String>()

        // NEW: limit unikalnych na dzień
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

    fun regenerateWholeDay(
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

        // użyte ID względem reszty planu (bez tego dnia)
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

        // usedIds: bez aktualnego slotu (żeby “Losuj” realnie zmieniał)
        val usedIds = collectUsedIds(internal, excludeDayIndex = dayIndex, excludeSlot = slot)

        // NEW: ile unikalnych już jest w tym dniu, bez aktualnego slotu
        val uniqueAlreadyToday = countUniqueInDayExcludingSlot(current, slot)
        // jeżeli już masz 2 unikalne (dla >=7 dni), to ten slot nie może stać się unikalny
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
                append("Poranek: ${d.morning.title}")
                if (d.morning.description.isNotBlank()) append("\n- ${d.morning.description}")

                append("\n\nPołudnie: ${d.midday.title}")
                if (d.midday.description.isNotBlank()) append("\n- ${d.midday.description}")

                append("\n\nWieczór: ${d.evening.title}")
                if (d.evening.description.isNotBlank()) append("\n- ${d.evening.description}")
            }

            DayPlan(
                day = d.day,
                title = "Dzień ${d.day} — $destinationName",
                details = details
            )
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
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

        // 1) twarde dopasowanie pod miasto+region+styl
        val strict = all.filter { matchesPrefs(it, prefs, dest) }

        // 2) jeśli strict puste, poluzuj (żeby nie wpadać w fallback)
        val base = if (strict.isNotEmpty()) strict else all

        // 3) preferowane typy pod slot
        val preferredTypes = preferredTypesFor(slot)
        val typed = base.filter { it.type in preferredTypes }.ifEmpty { base }

        // 4) indoor przy złej pogodzie (ale nie “na siłę”)
        val indoorFiltered = if (preferIndoor) typed.filter { it.indoor } else emptyList()
        val pool0 = if (preferIndoor && indoorFiltered.isNotEmpty()) indoorFiltered else typed

        // 5) wyklucz aktualne ID (przy “Losuj”)
        val pool1 = if (!excludeActivityId.isNullOrBlank()) {
            pool0.filter { it.id != excludeActivityId }.ifEmpty { pool0 }
        } else pool0

        if (pool1.isEmpty()) return fallbackSlot(slot, preferIndoor)

        // 6) NEW: limit unikalnych na dzień
        val pool2 = if (uniqueUsedToday >= maxUniquePerDay) {
            pool1.filter { it.destinationId == null }.ifEmpty { pool1 } // jak brak generycznych, nie blokuj całkiem
        } else {
            pool1
        }

        // 7) miękka unikalność globalna (bez powtórek, ale jak brak to pozwól)
        val poolNoRepeat = usedIds?.let { used -> pool2.filter { it.id !in used } }.orEmpty()
        val chosen = if (poolNoRepeat.isNotEmpty()) poolNoRepeat.random() else pool2.random()

        usedIds?.add(chosen.id)
        return toSlotPlan(chosen)
    }

    private fun preferredTypesFor(slot: DaySlot): Set<ActivityType> = when (slot) {
        DaySlot.MORNING -> setOf(ActivityType.CULTURE, ActivityType.NATURE, ActivityType.ACTIVE)
        DaySlot.MIDDAY -> setOf(ActivityType.FOOD, ActivityType.CULTURE, ActivityType.ACTIVE, ActivityType.NATURE)
        DaySlot.EVENING -> setOf(ActivityType.RELAX, ActivityType.NIGHT, ActivityType.FOOD, ActivityType.CULTURE)
    }

    private fun matchesPrefs(a: ActivityTemplate, prefs: Preferences, dest: Destination): Boolean {
        // unikalne atrakcje tylko dla właściwego miasta
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
        val title = when (slot) {
            DaySlot.MORNING -> "Zwiedzanie okolicy"
            DaySlot.MIDDAY -> "Posiłek i przerwa"
            DaySlot.EVENING -> "Wieczorny spacer / relaks"
        }

        val desc = when {
            preferIndoor -> "Zła pogoda — wybierz atrakcję pod dachem (muzeum, galeria, lokalna kawiarnia)."
            else -> "Wybierz najciekawsze miejsce w pobliżu i dopasuj tempo do dnia."
        }

        return SlotPlan(
            baseActivityId = null,
            title = title,
            description = desc,
            indoor = preferIndoor
        )
    }

    private fun collectUsedIds(
        internal: List<InternalDayPlan>,
        excludeDayIndex: Int? = null,
        excludeSlot: DaySlot? = null
    ): MutableSet<String> {
        val used = mutableSetOf<String>()

        fun add(id: String?) {
            if (!id.isNullOrBlank()) used.add(id)
        }

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

    // jeśli nie masz prefiksu u_ w id unikalnych, to użyj tego zamiast powyższego:
    // private fun isUnique(slot: SlotPlan): Boolean = false

    private fun countUniqueInDayExcludingSlot(day: InternalDayPlan, exclude: DaySlot): Int {
        fun isUniqueId(id: String?): Boolean = !id.isNullOrBlank() && id.startsWith("u_")

        val m = if (exclude != DaySlot.MORNING) isUniqueId(day.morning.baseActivityId) else false
        val d = if (exclude != DaySlot.MIDDAY) isUniqueId(day.midday.baseActivityId) else false
        val e = if (exclude != DaySlot.EVENING) isUniqueId(day.evening.baseActivityId) else false

        return listOf(m, d, e).count { it }
    }
}
