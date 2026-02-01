package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import kotlin.math.abs

// Enum i klasa wyniku bez zmian
enum class TransportPass { T_MAX, T_AVG, T_MIN }

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

        // Helpery bez zmian...
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

        // POPRAWKA: Dodajemy parametr 'strictBudget'
        // strictBudget = true -> zwraca TYLKO te, na które stać (żeby sprawdzić, czy pass jest OK)
        // strictBudget = false -> uzupełnia listę za drogimi (żeby nie pokazać pustego ekranu na końcu)
        fun computeForPass(pass: TransportPass, strictBudget: Boolean): List<Destination> {
            val strict = all.filter { it.region.equals(prefs.region, true) && it.climate.equals(prefs.climate, true) }
            val relaxed1 = all.filter { it.region.equals(prefs.region, true) }
            val relaxed2 = all

            fun rank(list: List<Destination>): List<Destination> {
                val cands = list.map { d -> Candidate(d, pass, budgetPerDayFor(d, pass)) }

                // 1. Te, na które nas stać
                val ok = cands.filter { it.bpd >= it.d.minBudgetPerDay }

                // 2. Jeśli jesteśmy w trybie "strict", zwracamy tylko OK i kończymy.
                if (strictBudget) {
                    return ok
                        .sortedWith(compareByDescending<Candidate> { matchTuple(it.d).first }
                            .thenBy { it.d.transportCostRoundTripPlnAvg }) // Sortowanie pomocnicze
                        .map { it.d }
                        .take(3)
                }

                // 3. W trybie "fallback" (nie strict) uzupełniamy listę tymi za drogimi
                val fill = cands.filter { it.bpd < it.d.minBudgetPerDay }
                    .sortedBy { it.d.minBudgetPerDay } // Najtańsze z za drogich

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

            // Próba znalezienia 3 kandydatów w najściślejszym dopasowaniu
            val r1 = rank(strict)
            if (r1.size >= 3) return r1

            // Jeśli strict=true i mamy mniej niż 3, to nie szukamy dalej w relaxed,
            // bo algorytm wyżej (w execute) i tak spróbuje tańszego transportu.
            // Ale dla pewności możemy poszukać w relaxed, byleby spełniały budżet.

            val r2 = (r1 + rank(relaxed1)).distinctBy { it.id }.take(3)
            if (r2.size >= 3) return r2

            return (r2 + rank(relaxed2)).distinctBy { it.id }.take(3)
        }

        // --- Algorytm 3 przebiegów (POPRAWIONY) ---

        // Krok 1: Sprawdź T_MAX, ale TYLKO te, na które nas stać (strict=true)
        val p1 = computeForPass(TransportPass.T_MAX, strictBudget = true)
        if (p1.size >= 3) {
            return SuggestionResult(p1, TransportPass.T_MAX)
        }

        // Krok 2: Sprawdź T_AVG (też strict)
        val p2 = computeForPass(TransportPass.T_AVG, strictBudget = true)
        if (p2.size >= 3) {
            return SuggestionResult(p2, TransportPass.T_AVG)
        }

        // Krok 3: T_MIN (Ostatnia deska ratunku)
        // Tutaj najpierw sprawdzamy strict
        val p3Strict = computeForPass(TransportPass.T_MIN, strictBudget = true)
        if (p3Strict.size >= 3) {
            return SuggestionResult(p3Strict, TransportPass.T_MIN)
        }

        // Krok 4 (Fallback ostateczny):
        // Jeśli nawet przy T_MIN nie znaleźliśmy 3 idealnych, bierzemy cokolwiek (strict=false),
        // używając kosztów T_MIN (bo to daje największą szansę, że "za drogie" będą "mało za drogie").
        val pFinal = computeForPass(TransportPass.T_MIN, strictBudget = false)
        return SuggestionResult(pFinal, TransportPass.T_MIN)
    }
}