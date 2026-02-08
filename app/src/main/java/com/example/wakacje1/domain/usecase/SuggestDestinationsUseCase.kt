package com.example.wakacje1.domain.usecase


import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.assets.DestinationRepository


enum class TransportPass { T_MAX, T_AVG, T_MIN }

data class SuggestionResult(
    val suggestions: List<Destination>,
    val transportPass: TransportPass
)

class SuggestDestinationsUseCase(
    private val destinationRepository: DestinationRepository
) {
    // Stałe ekonomiczne - MUSZĄ BYĆ TAKIE SAME JAK W PLAN GENERATOR
    private val ACCOMMODATION_RATIO = 0.45 // 45% typowego budżetu to hotel
    private val SAFETY_BUFFER_PERCENT = 0.10 // 10% na czarną godzinę

    fun execute(prefs: Preferences): SuggestionResult {
        val all = destinationRepository.getAllDestinations()
        val days = prefs.days.coerceAtLeast(1)

        // 1. KASKADA: Najpierw odejmujemy żelazną rezerwę
        val budgetAfterSafety = prefs.budget * (1.0 - SAFETY_BUFFER_PERCENT)

        // --- Helpery ---
        fun transportFor(d: Destination, pass: TransportPass): Int = when (pass) {
            TransportPass.T_MAX -> d.transportCostRoundTripPlnMax
            TransportPass.T_AVG -> d.transportCostRoundTripPlnAvg
            TransportPass.T_MIN -> d.transportCostRoundTripPlnMin
        }

        /**
         * Oblicza "Disposable Income Per Day" (Netto na życie/atrakcje).
         * Wzór: (Budżet - 10% - Transport - Hotel) / Dni
         */
        fun calculateNetDailyBudget(d: Destination, pass: TransportPass): Double {
            val transport = transportFor(d, pass)

            // Estymacja hotelu: 45% typowego budżetu w tym mieście * liczba dni
            // Fallback: jeśli brak danych, zakładamy 200zł/noc (450 * 0.45)
            val typicalDaily = d.typicalBudgetPerDay?.toDouble() ?: 450.0
            val estimatedHotelTotal = (typicalDaily * ACCOMMODATION_RATIO) * days

            val remainingForFun = budgetAfterSafety - transport - estimatedHotelTotal
            return remainingForFun / days
        }

        // Czy miasto pasuje stylowo?
        fun styleMatch(d: Destination): Boolean =
            d.tags.any { it.equals(prefs.style, ignoreCase = true) } ||
                    (prefs.style.equals("Mix", true) && d.tags.any { it.equals("Mix", true) })

        // Punktacja dopasowania (Region + Klimat + Styl)
        fun matchTuple(d: Destination): Int {
            val regionOk = d.region.equals(prefs.region, true)
            val climateOk = d.climate.equals(prefs.climate, true)
            val styleOk = styleMatch(d)
            return (if (regionOk) 3 else 0) + (if (climateOk) 2 else 0) + (if (styleOk) 1 else 0)
        }

        data class Candidate(val d: Destination, val netDaily: Double)

        /**
         * Główna funkcja filtrująca dla danego wariantu transportu (Pass).
         */
        fun computeForPass(pass: TransportPass, strictBudget: Boolean): List<Destination> {
            // Wstępna filtracja regionu/klimatu (żeby nie liczyć wszystkiego)
            val candidates = all.filter {
                if (strictBudget) it.region.equals(prefs.region, true) else true
            }.map { d ->
                Candidate(d, calculateNetDailyBudget(d, pass))
            }

            // FILTER: Czy stać nas na minimalne życie?
            // "Na życie" (jedzenie+atrakcje) potrzebujemy ok. 50% minBudget (reszta to tani nocleg)
            val affordable = candidates.filter {
                val minDailyNeeded = (it.d.minBudgetPerDay ?: 0) * 0.5
                it.netDaily >= minDailyNeeded
            }

            // Jeśli strictBudget = true, zwracamy tylko te, na które nas stać
            if (strictBudget) {
                return affordable
                    .sortedWith(compareByDescending<Candidate> { matchTuple(it.d) }
                        .thenByDescending { it.netDaily }) // Im więcej kasy zostaje, tym lepiej
                    .map { it.d }
                    .take(3)
            }

            // Fallback (strict = false): Dopełniamy listę "marzeniami" (za drogimi ofertami)
            // Ale sortujemy je tak, by te z najmniejszą dziurą budżetową były pierwsze.
            val fill = candidates.filter { !affordable.contains(it) }
                .sortedByDescending { it.netDaily } // np. -10zł jest lepsze niż -500zł

            return (affordable + fill)
                .sortedByDescending { matchTuple(it.d) } // Priorytet: Styl
                .distinctBy { it.d.id }
                .map { it.d }
                .take(3)
        }

        // --- 3 PRZEBIEGI (3 PASSY) ---

        // 1. Pesymistyczny (Drogi transport)
        val p1 = computeForPass(TransportPass.T_MAX, true)
        if (p1.size >= 3) return SuggestionResult(p1, TransportPass.T_MAX)

        // 2. Średni (Standardowy)
        val p2 = computeForPass(TransportPass.T_AVG, true)
        if (p2.size >= 3) return SuggestionResult(p2, TransportPass.T_AVG)

        // 3. Optymistyczny (Tani transport)
        val p3 = computeForPass(TransportPass.T_MIN, true)
        if (p3.size >= 3) return SuggestionResult(p3, TransportPass.T_MIN)

        // 4. Ostateczność (Pokaż cokolwiek, nawet jeśli nas nie stać)
        val pFinal = computeForPass(TransportPass.T_MIN, false)
        return SuggestionResult(pFinal, TransportPass.T_MIN)
    }
}