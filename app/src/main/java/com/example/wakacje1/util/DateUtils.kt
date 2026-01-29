package com.example.wakacje1.util

import java.util.Calendar

object DateUtils {

    fun normalizeToLocalMidnight(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun dayMillisForIndex(startMillis: Long, dayIndex: Int): Long {
        val oneDay = 24L * 60 * 60 * 1000L
        return startMillis + (oneDay * dayIndex)
    }
}