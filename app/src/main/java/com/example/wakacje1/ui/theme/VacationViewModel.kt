package com.example.wakacje1.ui.theme

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.data.WeatherRepository
import com.example.wakacje1.data.model.ActivitiesRepository
import com.example.wakacje1.data.model.ActivityTemplate
import com.example.wakacje1.data.model.ActivityType
import com.example.wakacje1.data.model.DestinationRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt
import com.example.wakacje1.data.model.Destination

// ========================= MODELE DLA UI =========================

data class Preferences(
    val budget: Int,                 // budżet całkowity (PLN)
    val days: Int,                   // liczba dni (z kalendarza)
    val climate: String,             // np. "Ciepły", "Umiarkowany", "Chłodny"
    val region: String,              // np. "Europa - miasto", "Morze Śródziemne", "Góry"
    val style: String,               // np. "Relaks", "Zwiedzanie", "Aktywny", "Mix"
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null
)


data class DayPlan(
    val day: Int,
    val title: String,
    val details: String
)

data class WeatherUiState(
    val loading: Boolean = false,
    val city: String? = null,
    val temperature: Double? = null,
    val description: String? = null,
    val error: String? = null
)

data class DayWeatherUi(
    val dateMillis: Long,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    val isBadWeather: Boolean
)

data class UiDaySlot(
    val title: String,
    val description: String,
    val isCustom: Boolean
)

enum class DaySlot { MORNING, MIDDAY, EVENING }

// ==================== WEWNĘTRZNA STRUKTURA PLANU ====================

private data class SlotPlan(
    val baseActivityId: String?,
    val title: String,
    val description: String,
    val isCustom: Boolean = false
)

private data class InternalDayPlan(
    val title: String,
    val morning: SlotPlan?,
    val midday: SlotPlan?,
    val evening: SlotPlan?,
    val budgetPerDay: Int
)

private val MORNING_TYPES = setOf(ActivityType.CULTURE, ActivityType.NATURE, ActivityType.RELAX)
private val MIDDAY_TYPES = setOf(ActivityType.NATURE, ActivityType.ACTIVE, ActivityType.CULTURE)
private val EVENING_TYPES = setOf(ActivityType.FOOD, ActivityType.RELAX, ActivityType.NIGHT)

// ============================== VIEWMODEL ==============================

class VacationViewModel(application: Application) : AndroidViewModel(application) {

    private val destinationRepository = DestinationRepository(application)
    private val activitiesRepository = ActivitiesRepository(application)

    var preferences by mutableStateOf<Preferences?>(null)
        private set

    var destinationSuggestions by mutableStateOf<List<Destination>>(emptyList())
        private set

    var chosenDestination by mutableStateOf<Destination?>(null)
        private set

    var plan by mutableStateOf<List<DayPlan>>(emptyList())
        private set

    var weather by mutableStateOf(WeatherUiState())
        private set

    var dayWeatherByDate by mutableStateOf<Map<Long, DayWeatherUi>>(emptyMap())
        private set

    private var internalPlanDays: MutableList<InternalDayPlan> = mutableListOf()

    // ------------------ Preferencje ------------------

    fun updatePreferences(prefs: Preferences) {
        preferences = prefs
        dayWeatherByDate = emptyMap()
    }

    // ------------------ Propozycje miejsc ------------------

    fun prepareDestinationSuggestions() {
        val prefs = preferences ?: run {
            destinationSuggestions = emptyList()
            return
        }

        val all = destinationRepository.getAllDestinations()
        val days = prefs.days.coerceAtLeast(1)
        val budgetPerDay = prefs.budget.toDouble() / days

        val scored = all.map { dest ->
            var score = 0.0

            if (prefs.region == dest.region) score += 3.0
            if (prefs.climate == dest.climate) score += 2.0
            if (dest.tags.any { it.equals(prefs.style, ignoreCase = true) }) score += 1.5

            val ratio = budgetPerDay / dest.minBudgetPerDay.toDouble()
            when {
                ratio < 0.7 -> score -= 100.0
                ratio < 1.0 -> score -= 3.0
                ratio <= 1.5 -> score += 2.0
                else -> score += 1.0
            }

            dest to score
        }.filter { it.second > -50.0 }

        destinationSuggestions = scored
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }

    fun chooseDestination(destination: Destination) {
        chosenDestination = destination
        internalPlanDays = mutableListOf()
        plan = emptyList()
        dayWeatherByDate = emptyMap()

        if (destination.apiQuery.isNotBlank()) {
            loadWeatherForCity(destination.apiQuery)
            loadForecastForTrip()
        }
    }

    // ------------------ Pogoda bieżąca ------------------

    fun loadWeatherForCity(cityQuery: String, force: Boolean = false) {
        viewModelScope.launch {
            weather = WeatherUiState(loading = true)
            try {
                val result = WeatherRepository.getWeatherForCity(cityQuery, forceRefresh = force)
                weather = WeatherUiState(
                    loading = false,
                    city = result.city,
                    temperature = result.temperature,
                    description = result.description,
                    error = null
                )
            } catch (e: Exception) {
                weather = WeatherUiState(
                    loading = false,
                    error = e.message ?: "Nie udało się pobrać pogody."
                )
            }
        }
    }

    // ------------------ Prognoza na dni wyjazdu ------------------

    fun loadForecastForTrip(force: Boolean = false) {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return
        val startMillis = prefs.startDateMillis ?: return
        val days = prefs.days.coerceAtLeast(1)

        viewModelScope.launch {
            try {
                val forecastList: List<WeatherRepository.WeatherForecastDay> =
                    WeatherRepository.getForecastForCity(dest.apiQuery, forceRefresh = force)

                val byDate: Map<Long, WeatherRepository.WeatherForecastDay> =
                    forecastList.associateBy { it.dateMillis }

                val result = mutableMapOf<Long, DayWeatherUi>()
                val oneDay = 24L * 60 * 60 * 1000L
                val startNorm = normalizeToLocalMidnight(startMillis)

                for (i in 0 until days) {
                    val dayMillis = startNorm + oneDay * i
                    val f = byDate[dayMillis]

                    result[dayMillis] = DayWeatherUi(
                        dateMillis = dayMillis,
                        tempMin = f?.tempMin,
                        tempMax = f?.tempMax,
                        description = f?.description,
                        isBadWeather = f?.isBadWeather ?: false
                    )
                }

                dayWeatherByDate = result
            } catch (_: Exception) {
                dayWeatherByDate = emptyMap()
            }
        }
    }

    fun getWeatherForDayIndex(dayIndex: Int): DayWeatherUi? {
        val prefs = preferences ?: return null
        val startMillis = prefs.startDateMillis ?: return null
        val oneDay = 24L * 60 * 60 * 1000L
        val startNorm = normalizeToLocalMidnight(startMillis)
        val dayMillis = startNorm + oneDay * dayIndex
        return dayWeatherByDate[dayMillis]
    }

    private fun isBadWeatherForDayIndex(dayIndex: Int): Boolean {
        return getWeatherForDayIndex(dayIndex)?.isBadWeather ?: false
    }

    // ------------------ Generowanie planu ------------------

    fun generatePlan() {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return

        val daysCount = prefs.days.coerceAtLeast(1)
        val budgetPerDay = (prefs.budget.toDouble() / daysCount).roundToInt()

        val newInternal = mutableListOf<InternalDayPlan>()
        for (dayNumber in 1..daysCount) {
            newInternal += buildInternalDay(
                dayNumber = dayNumber,
                prefs = prefs,
                dest = dest,
                budgetPerDay = budgetPerDay,
                extraSeed = 0
            )
        }

        internalPlanDays = newInternal
        plan = rebuildPlanFromInternal()
    }

    private fun buildInternalDay(
        dayNumber: Int,
        prefs: Preferences,
        dest: Destination,
        budgetPerDay: Int,
        extraSeed: Int
    ): InternalDayPlan {
        val baseSeed = dayNumber * 1000 + extraSeed

        // jeśli prognoza mówi "zła pogoda", preferujemy indoor dla tego dnia
        val preferIndoor = isBadWeatherForDayIndex(dayNumber - 1)

        val morningTemplate = pickActivityForSlot(
            region = dest.region,
            style = prefs.style,
            preferredTypes = MORNING_TYPES,
            seed = baseSeed + 11,
            preferIndoor = preferIndoor
        )

        val middayTemplate = pickActivityForSlot(
            region = dest.region,
            style = prefs.style,
            preferredTypes = MIDDAY_TYPES,
            seed = baseSeed + 22,
            preferIndoor = preferIndoor
        )

        val eveningTemplate = pickActivityForSlot(
            region = dest.region,
            style = prefs.style,
            preferredTypes = EVENING_TYPES,
            seed = baseSeed + 33,
            preferIndoor = true // wieczorem indoor często ma sens niezależnie od pogody
        )

        val morningSlot = morningTemplate?.let {
            SlotPlan(baseActivityId = it.id, title = it.title, description = it.description)
        }
        val middaySlot = middayTemplate?.let {
            SlotPlan(baseActivityId = it.id, title = it.title, description = it.description)
        }
        val eveningSlot = eveningTemplate?.let {
            SlotPlan(baseActivityId = it.id, title = it.title, description = it.description)
        }

        val dayTitle = when (prefs.style) {
            "Relaks" -> "Dzień relaksu w ${dest.displayName}"
            "Zwiedzanie" -> "Zwiedzanie ${dest.displayName}"
            "Aktywny" -> "Aktywny dzień w ${dest.displayName}"
            "Mix" -> "Mieszany dzień w ${dest.displayName}"
            else -> "Dzień $dayNumber w ${dest.displayName}"
        }

        return InternalDayPlan(
            title = dayTitle,
            morning = morningSlot,
            midday = middaySlot,
            evening = eveningSlot,
            budgetPerDay = budgetPerDay
        )
    }

    private fun rebuildPlanFromInternal(): List<DayPlan> {
        return internalPlanDays.mapIndexed { index, internal ->
            DayPlan(
                day = index + 1,
                title = internal.title,
                details = buildDetailsText(internal)
            )
        }
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

        val dayIndex = internalPlanDays.indexOf(internal)
        val w = if (dayIndex >= 0) getWeatherForDayIndex(dayIndex) else null
        val weatherLine = if (w != null && (w.tempMin != null || w.tempMax != null || !w.description.isNullOrBlank())) {
            val temps = when {
                w.tempMin != null && w.tempMax != null -> "(${w.tempMin.roundToInt()}°C – ${w.tempMax.roundToInt()}°C)"
                else -> ""
            }
            val desc = w.description ?: ""
            "\nPogoda: $desc $temps\n"
        } else ""

        return buildString {
            append(slotText("Poranek", internal.morning))
            append(slotText("Południe", internal.midday))
            append(slotText("Wieczór", internal.evening))
            append(weatherLine)
            append("Orientacyjny budżet na ten dzień: ok. ${internal.budgetPerDay} zł (bez dojazdu i noclegu).")
        }
    }

    private fun pickActivityForSlot(
        region: String,
        style: String,
        preferredTypes: Set<ActivityType>,
        seed: Int,
        excludeId: String? = null,
        preferIndoor: Boolean = false
    ): ActivityTemplate? {
        val allActivities = activitiesRepository.getAllActivities()

        val candidates = allActivities.filter { t ->
            (t.suitableRegions.isEmpty() || region in t.suitableRegions) &&
                    (t.suitableStyles.isEmpty() || style in t.suitableStyles) &&
                    (t.type in preferredTypes)
        }

        if (candidates.isEmpty()) return null

        // preferuj indoor, ale nie blokuj jeśli nie ma indoor w tej kategorii
        val weatherFiltered = if (preferIndoor) {
            val indoorOnes = candidates.filter { it.indoor }
            if (indoorOnes.isNotEmpty()) indoorOnes else candidates
        } else {
            candidates
        }

        val filtered = if (excludeId != null) weatherFiltered.filter { it.id != excludeId } else weatherFiltered
        if (filtered.isEmpty()) return null

        val size = filtered.size
        val raw = seed % size
        val idx = if (raw < 0) raw + size else raw
        return filtered[idx]
    }

    // ------------------ Sloty dla UI ------------------

    fun getUiSlotsForDay(dayIndex: Int): Triple<UiDaySlot, UiDaySlot, UiDaySlot>? {
        val internal = internalPlanDays.getOrNull(dayIndex) ?: return null

        fun mapSlot(slot: SlotPlan?): UiDaySlot =
            UiDaySlot(
                title = slot?.title ?: "Czas wolny",
                description = slot?.description.orEmpty(),
                isCustom = slot?.isCustom ?: false
            )

        return Triple(
            mapSlot(internal.morning),
            mapSlot(internal.midday),
            mapSlot(internal.evening)
        )
    }

    // ------------------ Operacje na dniach ------------------

    fun moveDayUp(index: Int) {
        if (index <= 0 || index >= internalPlanDays.size) return
        val mutable = internalPlanDays.toMutableList()
        val tmp = mutable[index - 1]
        mutable[index - 1] = mutable[index]
        mutable[index] = tmp
        internalPlanDays = mutable.toMutableList()
        plan = rebuildPlanFromInternal()
    }

    fun moveDayDown(index: Int) {
        if (index < 0 || index >= internalPlanDays.size - 1) return
        val mutable = internalPlanDays.toMutableList()
        val tmp = mutable[index + 1]
        mutable[index + 1] = mutable[index]
        mutable[index] = tmp
        internalPlanDays = mutable.toMutableList()
        plan = rebuildPlanFromInternal()
    }

    fun regenerateDay(dayIndex: Int) {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return
        if (dayIndex !in internalPlanDays.indices) return

        val daysCount = internalPlanDays.size.coerceAtLeast(1)
        val budgetPerDay = (prefs.budget.toDouble() / daysCount).roundToInt()

        val dayNumber = dayIndex + 1
        val extraSeed = (System.currentTimeMillis() and 0xFFFF).toInt()

        val updated = buildInternalDay(
            dayNumber = dayNumber,
            prefs = prefs,
            dest = dest,
            budgetPerDay = budgetPerDay,
            extraSeed = extraSeed
        )

        val mutable = internalPlanDays.toMutableList()
        mutable[dayIndex] = updated
        internalPlanDays = mutable.toMutableList()
        plan = rebuildPlanFromInternal()
    }

    fun rollNewActivity(dayIndex: Int, slot: DaySlot) {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return
        if (dayIndex !in internalPlanDays.indices) return

        val internal = internalPlanDays[dayIndex]

        val (types, seedOffset, currentSlot, preferIndoor) = when (slot) {
            DaySlot.MORNING -> Quad(MORNING_TYPES, 11, internal.morning, isBadWeatherForDayIndex(dayIndex))
            DaySlot.MIDDAY -> Quad(MIDDAY_TYPES, 22, internal.midday, isBadWeatherForDayIndex(dayIndex))
            DaySlot.EVENING -> Quad(EVENING_TYPES, 33, internal.evening, true)
        }

        val seed = ((System.currentTimeMillis() shr 4) and 0x7FFFFFFF).toInt() + seedOffset
        val excludeId = currentSlot?.baseActivityId

        val template = pickActivityForSlot(
            region = dest.region,
            style = prefs.style,
            preferredTypes = types,
            seed = seed,
            excludeId = excludeId,
            preferIndoor = preferIndoor
        ) ?: return

        val newSlot = SlotPlan(
            baseActivityId = template.id,
            title = template.title,
            description = template.description,
            isCustom = false
        )

        val updated = when (slot) {
            DaySlot.MORNING -> internal.copy(morning = newSlot)
            DaySlot.MIDDAY -> internal.copy(midday = newSlot)
            DaySlot.EVENING -> internal.copy(evening = newSlot)
        }

        val mutable = internalPlanDays.toMutableList()
        mutable[dayIndex] = updated
        internalPlanDays = mutable.toMutableList()
        plan = rebuildPlanFromInternal()
    }

    fun setCustomActivity(dayIndex: Int, slot: DaySlot, title: String, description: String) {
        if (dayIndex !in internalPlanDays.indices) return

        val internal = internalPlanDays[dayIndex]
        val safeTitle = if (title.isBlank()) "Własny plan" else title

        val newSlot = SlotPlan(
            baseActivityId = null,
            title = safeTitle,
            description = description,
            isCustom = true
        )

        val updated = when (slot) {
            DaySlot.MORNING -> internal.copy(morning = newSlot)
            DaySlot.MIDDAY -> internal.copy(midday = newSlot)
            DaySlot.EVENING -> internal.copy(evening = newSlot)
        }

        val mutable = internalPlanDays.toMutableList()
        mutable[dayIndex] = updated
        internalPlanDays = mutable.toMutableList()
        plan = rebuildPlanFromInternal()
    }

    // ------------------ Pomocnicze: daty ------------------

    private fun normalizeToLocalMidnight(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // prosta "krotka" na 4 wartości (żeby nie robić własnych plików)
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
