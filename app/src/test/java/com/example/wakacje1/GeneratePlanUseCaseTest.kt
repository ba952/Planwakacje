package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.data.assets.ActivityTemplate
import com.example.wakacje1.data.assets.ActivityType
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratePlanUseCaseTest {

    private val activitiesRepository = mockk<ActivitiesRepository>()
    private val planGenerator = mockk<PlanGenerator>()
    private val useCase = GeneratePlanUseCase(activitiesRepository, planGenerator)

    @Test
    fun `execute should fetch activities and call generator`() = runTest {
        // --- GIVEN ---
        val prefs = Preferences(2000, 3, "Góry", "Umiarkowany", "Aktywnie")

        // TWORZENIE OBIEKTU DESTINATION (Ręczne)
        // Musimy dopasować się do tego, co masz w klasie Destination.kt.
        // Skoro JSON ma Range [200, 880], a kod nie przyjmuje Range,
        // to wpisujemy te wartości do Min i Max.

        val dest = Destination(
            id = "d003",
            displayName = "Barcelona",
            country = "Hiszpania",
            region = "Europa - miasto",
            climate = "Ciepły",

            minBudgetPerDay = 200,


            typicalBudgetPerDay = 320,


            tags = listOf("Zwiedzanie", "Mix"),

            apiQuery = "Barcelona",


            transportCostRoundTripPlnMin = 200,
            transportCostRoundTripPlnMax = 880


        )

        val dummyActivities = listOf(
            ActivityTemplate(
                id = "a002",
                title = "Galeria",
                description = "Opis",
                type = ActivityType.CULTURE,
                suitableRegions = setOf("Europa - miasto"),
                suitableStyles = setOf("Zwiedzanie"),
                indoor = true,
                destinationId = null
            )
        )

        val expectedPlan = mutableListOf<InternalDayPlan>()

        // Mockowanie
        coEvery { activitiesRepository.getAllActivities() } returns dummyActivities

        coEvery {
            planGenerator.generateInternalPlan(any(), any(), any(), any())
        } returns expectedPlan

        // --- WHEN ---
        val result = useCase.execute(prefs, dest, isBadWeatherForDayIndex = { false })

        // --- THEN ---
        coVerify(exactly = 1) { activitiesRepository.getAllActivities() }

        coVerify(exactly = 1) {
            planGenerator.generateInternalPlan(prefs, dest, dummyActivities, any())
        }

        assertEquals(expectedPlan, result)
    }
}