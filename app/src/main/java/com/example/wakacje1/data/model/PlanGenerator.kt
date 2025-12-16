package com.example.wakacje1.data.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Slot w ciągu dnia (poranek / południe / wieczór).
 */
enum class DaySlot { MORNING, MIDDAY, EVENING }

/**
 * Pojedynczy wpis planu (slot).
 * baseActivityId służy do uniknięcia powtórki przy ponownym losowaniu.
 */
data class SlotPlan(
    val baseActivityId: String?,
    val title: String,
    val description: String,
    val isCustom: Boolean = false
)

/**
 * Plan dnia jako 3 sloty + budżet per dzień.
 */
data class InternalDayPlan(
    val title: String,
    val morning: SlotPlan?,
    val midday: SlotPlan?,
    val evening: SlotPlan?,
    val budgetPerDay: Int
)

object PlanGenerator {

    private val MORNING_TYPES = setOf(ActivityType.CULTURE, ActivityType.NATURE, ActivityType.RELAX)
    private val MIDDAY_TYPES = setOf(ActivityType.NATURE, ActivityType.ACTIVE, ActivityType.CULTURE)
    private val EVENING_TYPES = setOf(ActivityType.FOOD, ActivityType.RELAX, ActivityType.NIGHT)

    /**
     * Generuje cały plan jako strukturę InternalDayPlan (łatwe edytowanie / ponowne losowanie slotów).
     *
     * @param isBadWeatherForDayIndex funkcja mówiąca czy dzień (0..days-1) ma "złą pogodę"
     */
    fun generateInternalPlan(
        prefs: Preferences,
        dest: Destination,
        allActivities: List<ActivityTemplate>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {

        val daysCount = prefs.days.coerceAtLeast(1)
        val budgetPerDay = (prefs.budget.toDouble() / daysCount).roundToInt()

        val result = mutableListOf<InternalDayPlan>()
        for (dayNumber in 1..daysCount) {
            val preferIndoor = isBadWeatherForDayIndex(dayNumber - 1)
            result += buildInternalDay(
                dayNumber = dayNumber,
                prefs = prefs,
                dest = dest,
                budgetPerDay = budgetPerDay,
                allActivities = allActivities,
                extraSeed = 0,
                preferIndoor = preferIndoor
            )
        }
        return result
    }

    fun rebuildDayPlans(
        internal: List<InternalDayPlan>,
        placeName: String
    ): List<DayPlan> {
        return internal.mapIndexed { index, d ->
            DayPlan(
                day = index + 1,
                title = d.title.ifBlank { "Dzień ${index + 1} – $placeName" },
                details = buildDetailsText(d)
            )
        }
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

        val daysCount = prefs.days.coerceAtLeast(1)
        val budgetPerDay = (prefs.budget.toDouble() / daysCount).roundToInt()

        val dayNumber = dayIndex + 1
        val extraSeed = (System.currentTimeMillis() and 0xFFFF).toInt()
        val preferIndoor = isBadWeatherForDayIndex(dayIndex)

        internal[dayIndex] = buildInternalDay(
            dayNumber = dayNumber,
            prefs = prefs,
            dest = dest,
            budgetPerDay = budgetPerDay,
            allActivities = allActivities,
            extraSeed = extraSeed,
            preferIndoor = preferIndoor
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
        val current = internal[dayIndex]

        val preferIndoor = when (slot) {
            DaySlot.MORNING, DaySlot.MIDDAY -> isBadWeatherForDayIndex(dayIndex)
            DaySlot.EVENING -> true
        }

        val (types, seedOffset, currentSlot) = when (slot) {
            DaySlot.MORNING -> Triple(MORNING_TYPES, 11, current.morning)
            DaySlot.MIDDAY -> Triple(MIDDAY_TYPES, 22, current.midday)
            DaySlot.EVENING -> Triple(EVENING_TYPES, 33, current.evening)
        }

        val excludeId = currentSlot?.baseActivityId
        val seed = ((System.currentTimeMillis() shr 4) and 0x7FFFFFFF).toInt() + seedOffset

        val template = pickActivityForSlot(
            region = dest.region,
            style = prefs.style,
            preferredTypes = types,
            seed = seed,
            excludeId = excludeId,
            allActivities = allActivities,
            preferIndoor = preferIndoor
        ) ?: return

        val newSlot = SlotPlan(
            baseActivityId = template.id,
            title = template.title,
            description = template.description,
            isCustom = false
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

        val safeTitle = if (title.isBlank()) "Własny pomysł" else title

        val custom = SlotPlan(
            baseActivityId = null,
            title = safeTitle,
            description = description,
            isCustom = true
        )

        internal[dayIndex] = when (slot) {
            DaySlot.MORNING -> current.copy(morning = custom)
            DaySlot.MIDDAY -> current.copy(midday = custom)
            DaySlot.EVENING -> current.copy(evening = custom)
        }
    }

    // ========================= prywatne =========================

    private fun buildInternalDay(
        dayNumber: Int,
        prefs: Preferences,
        dest: Destination,
        budgetPerDay: Int,
        allActivities: List<ActivityTemplate>,
        extraSeed: Int,
        preferIndoor: Boolean
    ): InternalDayPlan {
        val baseSeed = dayNumber * 1000 + extraSeed

        val morningTemplate = pickActivityForSlot(
            region = dest.region,
            style = prefs.style,
            preferredTypes = MORNING_TYPES,
            seed = baseSeed + 11,
            excludeId = null,
            allActivities = allActivities,
            preferIndoor = preferIndoor
        )

        val middayTemplate = pickActivityForSlot(
            region = dest.region,
            style = prefs.style,
            preferredTypes = MIDDAY_TYPES,
            seed = baseSeed + 22,
            excludeId = morningTemplate?.id,
            allActivities = allActivities,
            preferIndoor = preferIndoor
        )

        val eveningTemplate = pickActivityForSlot(
            region = dest.region,
            style = prefs.style,
            preferredTypes = EVENING_TYPES,
            seed = baseSeed + 33,
            excludeId = middayTemplate?.id,
            allActivities = allActivities,
            preferIndoor = true
        )

        val dayTitle = when (prefs.style) {
            "Relaks" -> "Dzień relaksu w ${dest.displayName}"
            "Zwiedzanie" -> "Zwiedzanie ${dest.displayName}"
            "Aktywny" -> "Aktywny dzień w ${dest.displayName}"
            "Mix" -> "Mieszany dzień w ${dest.displayName}"
            else -> "Dzień $dayNumber w ${dest.displayName}"
        }

        return InternalDayPlan(
            title = dayTitle,
            morning = morningTemplate?.toSlot(),
            midday = middayTemplate?.toSlot(),
            evening = eveningTemplate?.toSlot(),
            budgetPerDay = budgetPerDay
        )
    }

    private fun ActivityTemplate.toSlot(): SlotPlan =
        SlotPlan(baseActivityId = id, title = title, description = description, isCustom = false)

    private fun pickActivityForSlot(
        region: String,
        style: String,
        preferredTypes: Set<ActivityType>,
        seed: Int,
        excludeId: String?,
        allActivities: List<ActivityTemplate>,
        preferIndoor: Boolean
    ): ActivityTemplate? {

        val candidates = allActivities.filter { t ->
            (t.suitableRegions.isEmpty() || region in t.suitableRegions) &&
                    (t.suitableStyles.isEmpty() || style in t.suitableStyles) &&
                    (t.type in preferredTypes)
        }
        if (candidates.isEmpty()) return null

        val weatherFiltered = if (preferIndoor) {
            val indoor = candidates.filter { it.indoor }
            if (indoor.isNotEmpty()) indoor else candidates
        } else {
            candidates
        }

        val filtered = if (excludeId != null) weatherFiltered.filter { it.id != excludeId } else weatherFiltered
        if (filtered.isEmpty()) return null

        val idx = abs(seed) % filtered.size
        return filtered[idx]
    }

    private fun buildDetailsText(internal: InternalDayPlan): String {
        fun slotText(label: String, slot: SlotPlan?): String {
            val title = slot?.title ?: "Czas wolny"
            val desc = slot?.description
            return buildString {
                appendLine("$label: $title")
                if (!desc.isNullOrBlank()) appendLine("• $desc")
                appendLine()
            }
        }

        return buildString {
            append(slotText("Poranek", internal.morning))
            append(slotText("Południe", internal.midday))
            append(slotText("Wieczór", internal.evening))
            append("Orientacyjny budżet na ten dzień: ok. ${internal.budgetPerDay} zł (bez dojazdu i noclegu).")
        }
    }
}
