package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtil {
    private val localeIt = Locale.ITALIAN

    fun formatFullDate(millis: Long): String {
        val sdf = SimpleDateFormat("EEEE d MMMM yyyy", localeIt)
        val formatted = sdf.format(Date(millis))
        return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(localeIt) else it.toString() }
    }

    fun formatShortDate(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", localeIt)
        return sdf.format(Date(millis))
    }

    fun formatTime(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", localeIt)
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", localeIt)
        return sdf.format(Date(millis))
    }

    fun getStartOfDay(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        return getStartOfDay(millis1) == getStartOfDay(millis2)
    }

    fun getMonthYearLabel(millis: Long): String {
        val sdf = SimpleDateFormat("MMMM yyyy", localeIt)
        return sdf.format(Date(millis)).replaceFirstChar { it.uppercase(localeIt) }
    }
}
