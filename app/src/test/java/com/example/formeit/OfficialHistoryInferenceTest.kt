package com.example.formeit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class OfficialHistoryInferenceTest {

    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d)
    }.timeInMillis

    private fun snap(at: Long, gA: Int, iA: Int, gM: Int, iM: Int, last: String) =
        OfficialSnapshot(at, at, gA, iA, gM, iM, last, "FUORI", "2026-11-14T13:15:00.000Z")

    @Test
    fun parseDay_keepsCalendarDateOfMidnightUtc() {
        assertEquals(day(2026, 11, 3), FormeitDates.parseDay("2026-11-03T00:00:00.000Z"))
    }

    @Test
    fun firstSnapshot_holidayIsTheJustifiedNight() {
        // Real case: absent 1-3 Nov 2026; FormeIt month = 1 giustificata + 2 non giustificate, last = 3 Nov.
        val v = OfficialHistoryInference.infer(listOf(snap(day(2026, 11, 14), 1, 3, 1, 2, "2026-11-03T00:00:00.000Z")))
        val byDay = v.days.associateBy { it.dayMillis }
        assertEquals(3, v.days.size)
        assertEquals(true, byDay[day(2026, 11, 1)]!!.justified)
        assertNotNull(byDay[day(2026, 11, 1)]!!.excludedReason)
        assertEquals(false, byDay[day(2026, 11, 2)]!!.justified)
        assertEquals(false, byDay[day(2026, 11, 3)]!!.justified)
        assertEquals(Certainty.EXACT, byDay[day(2026, 11, 3)]!!.certainty)
        assertEquals(Certainty.INFERRED, byDay[day(2026, 11, 2)]!!.certainty)
        assertEquals(1, v.earlierThisYearIngiust)
        assertEquals(0, v.earlierThisYearGiust)
    }

    @Test
    fun laterSnapshot_singleIncrementIsExact() {
        val s1 = snap(day(2026, 11, 14), 1, 3, 1, 2, "2026-11-03T00:00:00.000Z")
        val s2 = snap(day(2026, 11, 20), 1, 4, 1, 3, "2026-11-19T00:00:00.000Z")
        val v = OfficialHistoryInference.infer(listOf(s1, s2))
        val d = v.days.first { it.dayMillis == day(2026, 11, 19) }
        assertEquals(false, d.justified)
        assertEquals(Certainty.EXACT, d.certainty)
        assertEquals(4, v.days.size)
    }
}
