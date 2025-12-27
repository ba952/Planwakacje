package com.example.wakacje1.data.model

object PlanGenerator {

    fun generateInternalPlan(
        prefs: Preferences,
        dest: Destination,
        allActivities: List<ActivityTemplate>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {
        val days = prefs.days.coerceAtLeast(1)
        val result = mutableListOf<InternalDayPlan>()

        for (i in 0 until days) {
            val badWeather = isBadWeatherForDayIndex(i)

            val morning = pickForSlot(
                slot = DaySlot.MORNING,
                prefs = prefs,
                dest = dest,
                all = allActivities,
                preferIndoor = badWeather
            )
            val midday = pickForSlot(
                slot = DaySlot.MIDDAY,
                prefs = prefs,
                dest = dest,
                all = allActivities,
                preferIndoor = badWeather
            )
            val evening = pickForSlot(
                slot = DaySlot.EVENING,
                prefs = prefs,
                dest = dest,
                all = allActivities,
                preferIndoor = badWeather
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

        internal[dayIndex] = InternalDayPlan(
            day = dayIndex + 1,
            morning = pickForSlot(DaySlot.MORNING, prefs, dest, allActivities, badWeather),
            midday = pickForSlot(DaySlot.MIDDAY, prefs, dest, allActivities, badWeather),
            evening = pickForSlot(DaySlot.EVENING, prefs, dest, allActivities, badWeather)
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

        val newSlot = pickForSlot(slot, prefs, dest, allActivities, badWeather)

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
        preferIndoor: Boolean
    ): SlotPlan {
        if (all.isEmpty()) return fallbackSlot(slot, preferIndoor)

        val base = all.filter { matchesPrefs(it, prefs, dest) }

        val preferredTypes = preferredTypesFor(slot)
        val typed = base.filter { it.type in preferredTypes }.ifEmpty { base }

        val indoorFiltered = if (preferIndoor) typed.filter { it.indoor } else emptyList()
        val finalPool = if (preferIndoor && indoorFiltered.isNotEmpty()) indoorFiltered else typed

        if (finalPool.isEmpty()) {
            val any = all.random()
            return toSlotPlan(any)
        }

        val chosen = finalPool.random()
        return toSlotPlan(chosen)
    }

    private fun preferredTypesFor(slot: DaySlot): Set<ActivityType> = when (slot) {
        DaySlot.MORNING -> setOf(ActivityType.CULTURE, ActivityType.NATURE, ActivityType.ACTIVE)
        DaySlot.MIDDAY -> setOf(ActivityType.FOOD, ActivityType.CULTURE, ActivityType.ACTIVE, ActivityType.NATURE)
        DaySlot.EVENING -> setOf(ActivityType.RELAX, ActivityType.NIGHT, ActivityType.FOOD, ActivityType.CULTURE)
    }

    private fun matchesPrefs(a: ActivityTemplate, prefs: Preferences, dest: Destination): Boolean {
        val regionOk =
            a.suitableRegions.isEmpty() ||
                    a.suitableRegions.any { it.equals(prefs.region, ignoreCase = true) } ||
                    a.suitableRegions.any { it.equals(dest.region, ignoreCase = true) }

        val styleOk =
            a.suitableStyles.isEmpty() ||
                    a.suitableStyles.any { it.equals(prefs.style, ignoreCase = true) }

        return regionOk && styleOk
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
            DaySlot.MORNING -> "Spacer / zwiedzanie"
            DaySlot.MIDDAY -> "Obiad i krótka atrakcja"
            DaySlot.EVENING -> "Wieczorny relaks"
        }

        val desc = if (preferIndoor) "Zła pogoda — rozważ aktywność pod dachem." else ""

        return SlotPlan(
            baseActivityId = null,
            title = title,
            description = desc,
            indoor = preferIndoor
        )
    }
}
