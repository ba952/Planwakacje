package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.DestinationRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import kotlin.math.abs

/**
 * Poziomy symulacji kosztów transportu.
 * T_MAX = Pesymistyczny (drogi lot/dojazd).
 * T_AVG = Średni (standardowy).
 * T_MIN = Optymistyczny (tanie linie/promocje).
 */
enum class TransportPass { T_MAX, T_AVG, T_MIN }

data class SuggestionResult(
    val suggestions: List<Destination>,
    val transportPass: TransportPass
)

/**
 * UseCase realizujący algorytm rekomendacji destynacji.
 *
 * ZASADA DZIAŁANIA (Algorytm 3-przebiegowy):
 * System próbuje dopasować destynacje w 3 krokach, manipulując szacowanym kosztem transportu.
 * Cel: Znaleźć co najmniej 3 destynacje, na które użytkownika stać.
 *
 * 1. Pass T_MAX: Czy stać Cię na wyjazd przy drogim transporcie? (Bezpieczny budżet).
 * 2. Pass T_AVG: Czy stać Cię przy średnich cenach lotów?
 * 3. Pass T_MIN: Czy stać Cię, jeśli upolujesz tanie bilety?
 * 4. Fallback: Jeśli nadal brak wyników, pokaż cokolwiek (nawet lekko za drogiego), przyjmując T_MIN.
 */
class SuggestDestinationsUseCase(
    private val destinationRepository: DestinationRepository
) {

    fun execute(prefs: Preferences): SuggestionResult {
        val all = destinationRepository.getAllDestinations()
        val days = prefs.days.coerceAtLeast(1)

        // --- Helpery obliczeniowe ---

        fun transportFor(d: Destination, pass: TransportPass): Int = when (pass) {
            TransportPass.T_MAX -> d.transportCostRoundTripPlnMax
            TransportPass.T_AVG -> d.transportCostRoundTripPlnAvg
            TransportPass.T_MIN -> d.transportCostRoundTripPlnMin
        }

        // Oblicza "Kieszonkowe na dzień" (Budget Per Day - BPD) po odjęciu transportu
        fun budgetPerDayFor(d: Destination, pass: TransportPass): Int {
            val remaining = prefs.budget - transportFor(d, pass)
            if (remaining <= 0) return 0
            return remaining / days
        }

        fun styleMatch(d: Destination): Boolean =
            d.tags.any { it.equals(prefs.style, ignoreCase = true) } ||
                    d.tags.any { it.equals("Mix", ignoreCase = true) && prefs.style.equals("Mix", true) }

        // Punktacja dopasowania (Region + Klimat + Styl)
        fun matchTuple(d: Destination): Triple<Int, Int, Int> {
            val regionOk = d.region.equals(prefs.region, true)
            val climateOk = d.climate.equals(prefs.climate, true)
            val styleOk = styleMatch(d)
            val lvl = (if (regionOk) 1 else 0) + (if (climateOk) 1 else 0) + (if (styleOk) 1 else 0)
            return Triple(lvl, if (regionOk) 1 else 0, if (climateOk) 1 else 0)
        }

        data class Candidate(val d: Destination, val pass: TransportPass, val bpd: Int)

        /**
         * Główna funkcja filtrująca i sortująca.
         * @param strictBudget
         * true -> Hard Filter: Zwraca TYLKO te, na które stać (bpd >= minBudget).
         * false -> Soft Filter: Uzupełnia listę "za drogimi" ofertami, żeby nie zwracać pustej listy.
         */
        fun computeForPass(pass: TransportPass, strictBudget: Boolean): List<Destination> {
            val strict = all.filter { it.region.equals(prefs.region, true) && it.climate.equals(prefs.climate, true) }
            val relaxed1 = all.filter { it.region.equals(prefs.region, true) }
            val relaxed2 = all

            fun rank(list: List<Destination>): List<Destination> {
                val cands = list.map { d -> Candidate(d, pass, budgetPerDayFor(d, pass)) }

                // 1. Affordable: Te, na które nas stać
                val ok = cands.filter { it.bpd >= it.d.minBudgetPerDay }

                // 2. Jeśli tryb ścisły (sprawdzamy czy dany Pass ma sens), zwracamy tylko Affordable.
                if (strictBudget) {
                    return ok
                        .sortedWith(compareByDescending<Candidate> { matchTuple(it.d).first }
                            .thenBy { it.d.transportCostRoundTripPlnAvg })
                        .map { it.d }
                        .take(3)
                }

                // 3. Fallback (tryb luźny): Dopełniamy listę tymi "trochę za drogimi".
                // Sortujemy "za drogie" rosnąco po wymaganym budżecie (najtańsze z drogich).
                val fill = cands.filter { it.bpd < it.d.minBudgetPerDay }
                    .sortedBy { it.d.minBudgetPerDay }

                val merged = (ok + fill).distinctBy { it.d.id }

                return merged
                    .sortedWith(
                        compareByDescending<Candidate> { matchTuple(it.d).first }
                            .thenBy { abs(it.bpd - it.d.typicalBudgetPerDay) } // Najbliżej typowego budżetu
                            .thenBy { it.d.transportCostRoundTripPlnAvg }
                            .thenBy { it.d.displayName }
                    )
                    .map { it.d }
                    .take(3)
            }

            // Kaskada wyszukiwania: Idealne -> Tylko Region -> Cokolwiek
            val r1 = rank(strict)
            if (r1.size >= 3) return r1

            val r2 = (r1 + rank(relaxed1)).distinctBy { it.id }.take(3)
            if (r2.size >= 3) return r2

            return (r2 + rank(relaxed2)).distinctBy { it.id }.take(3)
        }

        // --- EGZEKUCJA ALGORYTMU (3 PASSY) ---

        // Krok 1: Pesymistyczny (Drogi transport). Czy stać nas na cokolwiek?
        val p1 = computeForPass(TransportPass.T_MAX, strictBudget = true)
        if (p1.size >= 3) {
            return SuggestionResult(p1, TransportPass.T_MAX)
        }

        // Krok 2: Średni (Standardowy transport).
        val p2 = computeForPass(TransportPass.T_AVG, strictBudget = true)
        if (p2.size >= 3) {
            return SuggestionResult(p2, TransportPass.T_AVG)
        }

        // Krok 3: Optymistyczny (Tani transport). Ostatnia szansa na zmieszczenie się w budżecie.
        val p3Strict = computeForPass(TransportPass.T_MIN, strictBudget = true)
        if (p3Strict.size >= 3) {
            return SuggestionResult(p3Strict, TransportPass.T_MIN)
        }

        // Krok 4: Ostateczny Fallback.
        // Skoro nawet przy tanim transporcie nie stać nas na 3 destynacje,
        // bierzemy T_MIN (bo daje najlepsze wyniki) i wyłączamy 'strictBudget',
        // żeby pokazać cokolwiek (nawet jeśli przekracza budżet).
        val pFinal = computeForPass(TransportPass.T_MIN, strictBudget = false)
        return SuggestionResult(pFinal, TransportPass.T_MIN)
    }
}