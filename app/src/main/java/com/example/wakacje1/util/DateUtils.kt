package com.example.wakacje1.util

import java.util.Calendar

/**
 * Obiekt pomocniczy do operacji na datach.
 * Zapewnia spójność obliczeń czasowych w całej aplikacji, szczególnie przy mapowaniu
 * dni planu na dane pogodowe z API.
 */
object DateUtils {

    /**
     * Normalizuje podany czas w milisekundach do północy (00:00:00.000) lokalnego czasu.
     * Zapobiega to błędom przesunięcia przy porównywaniu dat, gdy np. dwie daty
     * różnią się tylko godziną, a powinny wskazywać na ten sam dzień.
     */
    fun normalizeToLocalMidnight(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Oblicza timestamp dla konkretnego dnia planu na podstawie daty rozpoczęcia wyjazdu.
     * startMillis powinno być znormalizowane do północy.
     * dayIndex to kolejny numer dnia (indeksowany od 0).
     */
    fun dayMillisForIndex(startMillis: Long, dayIndex: Int): Long {
        val oneDay = 24L * 60 * 60 * 1000L
        return startMillis + (oneDay * dayIndex)
    }
}