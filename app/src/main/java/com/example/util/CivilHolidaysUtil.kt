package com.example.util

import java.util.Calendar

object CivilHolidaysUtil {

    /**
     * Determines if a given date is an excluded day from the useful days of residency:
     * - Entire month of August (1-31 August)
     * - Christmas residence closure period (December 23 - January 6)
     * - Italian civil holidays:
     *   - 25 April (Festa della Liberazione)
     *   - 1 May (Festa del Lavoro)
     *   - 2 June (Festa della Repubblica)
     *   - 1 November (Tutti i Santi)
     *   - 8 December (Immacolata Concezione)
     *   - Easter Monday (Lunedì dell'Angelo / Pasquetta)
     */
    fun getExclusionReason(millis: Long): String? {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val month = cal.get(Calendar.MONTH) // 0-based: 0=Jan, 7=Aug, 11=Dec
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)

        // 1. Entire month of August
        if (month == Calendar.AUGUST) {
            return "Chiusura Residenza (Intero Mese di Agosto)"
        }

        // 2. Christmas closure: 23 Dec to 6 Jan
        if ((month == Calendar.DECEMBER && day >= 23) || (month == Calendar.JANUARY && day <= 6)) {
            return "Chiusura Residenze (Periodo Natalizio)"
        }

        // 3. Civil Holidays
        if (month == Calendar.NOVEMBER && day == 1) {
            return "Festività Civile (1° Novembre - Tutti i Santi)"
        }
        if (month == Calendar.DECEMBER && day == 8) {
            return "Festività Civile (8 Dicembre - Immacolata)"
        }
        if (month == Calendar.APRIL && day == 25) {
            return "Festività Civile (25 Aprile - Liberazione)"
        }
        if (month == Calendar.MAY && day == 1) {
            return "Festività Civile (1° Maggio - Festa del Lavoro)"
        }
        if (month == Calendar.JUNE && day == 2) {
            return "Festività Civile (2 Giugno - Festa della Repubblica)"
        }

        // Easter & Easter Monday
        val easter = computeEaster(year)
        val easterMonday = Calendar.getInstance().apply {
            timeInMillis = easter.timeInMillis
            add(Calendar.DAY_OF_MONTH, 1)
        }
        if (cal.get(Calendar.MONTH) == easter.get(Calendar.MONTH) && cal.get(Calendar.DAY_OF_MONTH) == easter.get(Calendar.DAY_OF_MONTH)) {
            return "Festività (Domenica di Pasqua)"
        }
        if (cal.get(Calendar.MONTH) == easterMonday.get(Calendar.MONTH) && cal.get(Calendar.DAY_OF_MONTH) == easterMonday.get(Calendar.DAY_OF_MONTH)) {
            return "Festività Civile (Lunedì dell'Angelo - Pasquetta)"
        }

        return null
    }

    fun isExcludedDay(millis: Long): Boolean {
        return getExclusionReason(millis) != null
    }

    /**
     * Computes Easter Sunday for a given year using Anonymous Gregorian algorithm.
     */
    private fun computeEaster(year: Int): Calendar {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31 - 1 // 0-based
        val day = ((h + l - 7 * m + 114) % 31) + 1

        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * Calculates the number of useful days between startDate and endDate (inclusive).
     */
    fun countUsefulDays(startDateMillis: Long, endDateMillis: Long): Int {
        if (startDateMillis > endDateMillis) return 0
        var count = 0
        val cal = Calendar.getInstance().apply { timeInMillis = DateUtil.getStartOfDay(startDateMillis) }
        val end = DateUtil.getStartOfDay(endDateMillis)

        while (cal.timeInMillis <= end) {
            if (!isExcludedDay(cal.timeInMillis)) {
                count++
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return count
    }

    /**
     * Default Academic Year computation:
     * Useful days run from custom start date (default 1 Oct) to 30 September.
     */
    fun getDefaultAcademicYearStartDate(referenceMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceMillis }
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) // 0-based: 0=Jan, 8=Sep, 9=Oct

        // If currently in Jan-Sep, the academic year started Oct 1st of previous year
        val startYear = if (currentMonth < Calendar.OCTOBER) currentYear - 1 else currentYear

        cal.set(Calendar.YEAR, startYear)
        cal.set(Calendar.MONTH, Calendar.OCTOBER)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getAcademicYearEndDate(startDateMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
        val startYear = cal.get(Calendar.YEAR)
        // End is always 30 September of the next year (or same year if started before Oct)
        val endYear = startYear + 1

        cal.set(Calendar.YEAR, endYear)
        cal.set(Calendar.MONTH, Calendar.SEPTEMBER)
        cal.set(Calendar.DAY_OF_MONTH, 30)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}
