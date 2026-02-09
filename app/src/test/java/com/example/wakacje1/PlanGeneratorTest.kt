package com.example.wakacje1.domain.engine

import com.example.wakacje1.domain.assets.ActivityTemplate
import com.example.wakacje1.domain.assets.ActivityType
import com.example.wakacje1.domain.model.*
import com.example.wakacje1.domain.util.StringProvider
import org.junit.Assert.*
import org.junit.Test

class PlanGeneratorTest {

    // StringProvider (zgodny z interfejsem: vararg args: Any)
    private val fakeStringProvider = object : StringProvider {
        override fun getString(resId: Int, vararg args: Any): String = "TestString"
    }

    private val generator = PlanGenerator(fakeStringProvider)

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

    private val testPrefs = Preferences(
        days = 1,
        budget = 500,
        climate = "Any",
        region = "TestRegion",
        style = "Any"
    )

    // --- Aktywności testowe ---
    private val expensiveMuseum = ActivityTemplate(
        id = "1",
        title = "Drogie Muzeum",
        description = "...",
        type = ActivityType.CULTURE,
        suitableRegions = emptySet(),
        suitableStyles = emptySet(),
        indoor = true,
        destinationId = "dest1"
    )

    private val cheapPark = ActivityTemplate(
        id = "2",
        title = "Tani Park",
        description = "...",
        type = ActivityType.NATURE,
        suitableRegions = emptySet(),
        suitableStyles = emptySet(),
        indoor = false,
        destinationId = "dest1"
    )

    private val nightClub = ActivityTemplate(
        id = "3",
        title = "Nocny Klub",
        description = "...",
        type = ActivityType.NIGHT,
        suitableRegions = emptySet(),
        suitableStyles = emptySet(),
        indoor = true,
        destinationId = "dest1"
    )

    // Dodatkowe indoor, żeby test pogody nie był flaky (żeby generator miał z czego wybierać)
    private val indoorGallery = ActivityTemplate(
        id = "4",
        title = "Galeria",
        description = "...",
        type = ActivityType.CULTURE,
        suitableRegions = emptySet(),
        suitableStyles = emptySet(),
        indoor = true,
        destinationId = "dest1"
    )

    private val indoorRestaurant = ActivityTemplate(
        id = "5",
        title = "Restauracja",
        description = "...",
        type = ActivityType.FOOD,
        suitableRegions = emptySet(),
        suitableStyles = emptySet(),
        indoor = true,
        destinationId = "dest1"
    )

    private val allTestActivities = listOf(
        expensiveMuseum,
        cheapPark,
        nightClub,
        indoorGallery,
        indoorRestaurant
    )

    private val byId: Map<String, ActivityTemplate> = allTestActivities.associateBy { it.id }

    private fun templatesOf(day: InternalDayPlan): List<ActivityTemplate> =
        listOf(day.morning, day.midday, day.evening)
            .mapNotNull { it.baseActivityId }
            .mapNotNull { byId[it] }

    // --- TEST 1: BUDŻET (własnościowy, bez zgadywania kolejności) ---
    @Test
    fun generate_never_picks_activity_with_non_matching_region_when_regions_specified() {
        val prefs = testPrefs.copy(days = 1, budget = 2000)

        val wrongRegionActivity = ActivityTemplate(
            id = "WRONG1",
            title = "Atrakcja z innym regionem",
            description = "...",
            type = ActivityType.CULTURE,
            suitableRegions = setOf("CompletelyDifferentRegion"),
            suitableStyles = emptySet(),
            indoor = true,
            destinationId = "dest1"
        )

        val activities = allTestActivities + wrongRegionActivity
        val byIdLocal = activities.associateBy { it.id }

        val result = generator.generateInternalPlan(
            prefs = prefs,
            dest = testDest, // region = "TestRegion"
            transportCost = 0,
            allActivities = activities,
            isBadWeatherForDayIndex = { false }
        )

        val day = result.first()
        val pickedIds = listOf(day.morning.baseActivityId, day.midday.baseActivityId, day.evening.baseActivityId)
        val pickedTemplates = pickedIds.mapNotNull { it?.let { id -> byIdLocal[id] } }

        assertFalse(
            "Generator nie powinien wybierać aktywności, która ma suitableRegions i nie pasuje do dest.region.",
            pickedTemplates.any { it.id == "WRONG1" }
        )
    }

    // --- TEST 2: POGODA (stabilny) ---
    @Test
    fun generate_avoids_outdoor_slots_during_bad_weather() {
        val result = generator.generateInternalPlan(
            prefs = testPrefs.copy(budget = 2000),
            dest = testDest,
            transportCost = 0,
            allActivities = allTestActivities,
            isBadWeatherForDayIndex = { true }
        )

        val day = result.first()

        // Sprawdzamy bezpośrednio flagę indoor w SlotPlan.
        // Fallback też będzie respektował preferIndoor, więc test jest stabilny.
        val slots = listOf(day.morning, day.midday, day.evening)
        assertTrue(
            "Podczas złej pogody wszystkie sloty powinny być indoor=true",
            slots.all { it.indoor }
        )
    }

    // --- TEST 3: NIGHT tylko w EVENING (zgodne z preferredTypesFor) ---
    @Test
    fun generate_puts_night_type_only_in_evening_slot() {
        val result = generator.generateInternalPlan(
            prefs = testPrefs.copy(budget = 2000),
            dest = testDest,
            transportCost = 0,
            allActivities = allTestActivities,
            isBadWeatherForDayIndex = { false }
        )

        val day = result.first()

        val morningType = day.morning.baseActivityId?.let { byId[it]?.type }
        val middayType = day.midday.baseActivityId?.let { byId[it]?.type }

        assertNotEquals("Rano nie może być typu NIGHT", ActivityType.NIGHT, morningType)
        assertNotEquals("W południe nie może być typu NIGHT", ActivityType.NIGHT, middayType)

        // Nie wymuszamy, że wieczorem MUSI być NIGHT – bo EVENING dopuszcza też RELAX/FOOD/CULTURE.
    }
}
