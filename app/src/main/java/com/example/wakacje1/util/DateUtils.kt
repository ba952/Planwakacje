package com.example.wakacje1.util

import java.util.Calendar

object DateUtils {
    private const val ONE_DAY = 24L * 60 * 60 * 1000L

    fun normalizeToLocalMidnight(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun dayMillisForIndex(startMidnightMillis: Long, dayIndex: Int): Long =
        startMidnightMillis + ONE_DAY * dayIndex
}
