package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import kotlin.math.abs

// Przeniesiony enum (jest publiczny, bo ViewModel musi go widzieć)
enum class TransportPass { T_MAX, T_AVG, T_MIN }

// Klasa wynikowa - zwraca listę ORAZ informację, który wariant transportu wybrano
data class SuggestionResult(
    val suggestions: List<Destination>,
    val transportPass: TransportPass
)

class SuggestDestinationsUseCase(
    private val destinationRepository: DestinationRepository
) {

    fun execute(prefs: Preferences): SuggestionResult {
        val all = destinationRepository.getAllDestinations()
        val days = prefs.days.coerceAtLeast(1)

        // Helpery (skopiowane z ViewModelu)
        fun transportFor(d: Destination, pass: TransportPass): Int = when (pass) {
            TransportPass.T_MAX -> d.transportCostRoundTripPlnMax
            TransportPass.T_AVG -> d.transportCostRoundTripPlnAvg
            TransportPass.T_MIN -> d.transportCostRoundTripPlnMin
        }

        fun budgetPerDayFor(d: Destination, pass: TransportPass): Int {
            val remaining = prefs.budget - transportFor(d, pass)
            if (remaining <= 0) return 0
            return remaining / days
        }

        fun styleMatch(d: Destination): Boolean =
            d.tags.any { it.equals(prefs.style, ignoreCase = true) } ||
                    d.tags.any { it.equals("Mix", ignoreCase = true) && prefs.style.equals("Mix", true) }

        fun matchTuple(d: Destination): Triple<Int, Int, Int> {
            val regionOk = d.region.equals(prefs.region, true)
            val climateOk = d.climate.equals(prefs.climate, true)
            val styleOk = styleMatch(d)
            val lvl = (if (regionOk) 1 else 0) + (if (climateOk) 1 else 0) + (if (styleOk) 1 else 0)
            return Triple(lvl, if (regionOk) 1 else 0, if (climateOk) 1 else 0)
        }

        data class Candidate(val d: Destination, val pass: TransportPass, val bpd: Int)

        fun computeForPass(pass: TransportPass): List<Destination> {
            val strict = all.filter { it.region.equals(prefs.region, true) && it.climate.equals(prefs.climate, true) }
            val relaxed1 = all.filter { it.region.equals(prefs.region, true) }
            val relaxed2 = all

            fun rank(list: List<Destination>): List<Destination> {
                val cands = list.map { d -> Candidate(d, pass, budgetPerDayFor(d, pass)) }
                val ok = cands.filter { it.bpd >= it.d.minBudgetPerDay }
                val fill = cands.filter { it.bpd < it.d.minBudgetPerDay }
                    .sortedBy { it.d.minBudgetPerDay }

                val merged = (ok + fill).distinctBy { it.d.id }

                return merged
                    .sortedWith(
                        compareByDescending<Candidate> { matchTuple(it.d).first }
                            .thenBy { abs(it.bpd - it.d.typicalBudgetPerDay) }
                            .thenBy { it.d.transportCostRoundTripPlnAvg }
                            .thenBy { it.d.displayName }
                    )
                    .map { it.d }
                    .take(3)
            }

            val r1 = rank(strict)
            if (r1.size >= 3) return r1
            val r2 = (r1 + rank(relaxed1)).distinctBy { it.id }.take(3)
            if (r2.size >= 3) return r2
            return (r2 + rank(relaxed2)).distinctBy { it.id }.take(3)
        }

        // Algorytm 3 przebiegów
        val p1 = computeForPass(TransportPass.T_MAX)
        if (p1.size >= 3) {
            return SuggestionResult(p1, TransportPass.T_MAX)
        }

        val p2 = computeForPass(TransportPass.T_AVG)
        if (p2.size >= 3) {
            return SuggestionResult(p2, TransportPass.T_AVG)
        }

        val p3 = computeForPass(TransportPass.T_MIN)
        return SuggestionResult(p3, TransportPass.T_MIN)
    }
}