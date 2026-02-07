package com.example.wakacje1.domain.engine

import com.example.wakacje1.data.assets.ActivityTemplate
import com.example.wakacje1.data.assets.ActivityType
import com.example.wakacje1.domain.model.*
import com.example.wakacje1.domain.util.StringProvider
import org.junit.Assert.*
import org.junit.Test

class PlanGeneratorTest {

    // 1. StringProvider (zgodny z interfejsem: vararg args: Any)
    private val fakeStringProvider = object : StringProvider {
        override fun getString(resId: Int, vararg args: Any): String {
            return "TestString"
        }
    }

    // Generator
    private val generator = PlanGenerator(fakeStringProvider)

    // 2. Destination (Wszystkie pola uzupełnione)
    private val testDest = Destination(
        id = "dest1",
        displayName = "TestCity",
        country = "TestCountry",
        region = "TestRegion",
        climate = "Umiarkowany",
        minBudgetPerDay = 100,
        typicalBudgetPerDay = 400,
        tags = emptyList(),
        apiQuery = "TestCityApi",
        transportCostRoundTripPlnMin = 100,
        transportCostRoundTripPlnMax = 200
    )

    // 3. Preferences (POPRAWIONE: dodano climate)
    private val testPrefs = Preferences(
        days = 1,
        budget = 500,         // Int
        climate = "Any",      // <--- DODAŁEM TO POLE (było brakujące)
        region = "TestRegion",
        style = "Any"
    )

    // --- Atrakcje ---
    private val expensiveMuseum = ActivityTemplate(
        id = "1", title = "Drogie Muzeum", description = "...",
        type = ActivityType.CULTURE, suitableRegions = emptySet(), suitableStyles = emptySet(),
        indoor = true, destinationId = "dest1"
    )

    private val cheapPark = ActivityTemplate(
        id = "2", title = "Tani Park", description = "...",
        type = ActivityType.NATURE, suitableRegions = emptySet(), suitableStyles = emptySet(),
        indoor = false,
        destinationId = "dest1"
    )

    private val nightClub = ActivityTemplate(
        id = "3", title = "Nocny Klub", description = "...",
        type = ActivityType.NIGHT, suitableRegions = emptySet(), suitableStyles = emptySet(),
        indoor = true, destinationId = "dest1"
    )

    private val allTestActivities = listOf(expensiveMuseum, cheapPark, nightClub)

    // --- TEST 1: BUDŻET ---
    @Test
    fun generate_respects_budget_limit() {
        // Mały budżet (250 zł)
        val poorPrefs = testPrefs.copy(budget = 250)

        val result = generator.generateInternalPlan(
            prefs = poorPrefs,
            dest = testDest,
            transportCost = 0,
            allActivities = allTestActivities,
            isBadWeatherForDayIndex = { false }
        )

        val morningTitle = result.first().morning.title
        assertEquals("Przy małym budżecie powinien wybrać Tani Park", "Tani Park", morningTitle)
    }

    // --- TEST 2: POGODA ---
    @Test
    fun generate_avoids_outdoor_places_during_bad_weather() {
        val result = generator.generateInternalPlan(
            prefs = testPrefs.copy(budget = 2000),
            dest = testDest,
            transportCost = 0,
            allActivities = allTestActivities,
            isBadWeatherForDayIndex = { true } // Zła pogoda
        )

        val day = result.first()
        val titlesToday = listOf(day.morning.title, day.midday.title, day.evening.title)
        val hasPark = titlesToday.contains("Tani Park")
        assertFalse("W deszczu nie powinno być parku (Activity outdoor)!", hasPark)
    }

    // --- TEST 3: PORY DNIA ---
    @Test
    fun generate_puts_Night_Life_only_in_Evening_slot() {
        val result = generator.generateInternalPlan(
            prefs = testPrefs.copy(budget = 2000),
            dest = testDest,
            transportCost = 0,
            allActivities = allTestActivities,
            isBadWeatherForDayIndex = { false }
        )
        val day = result.first()
        assertNotEquals("Rano nie może być Nocnego Klubu!", "Nocny Klub", day.morning.title)
    }
}