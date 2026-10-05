package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AbsenceTypes
import com.example.util.CivilHolidaysUtil
import com.example.util.HashUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Assenze Collegio", appName)
  }

  @Test
  fun `verify cryptographic seal calculation with medical justification`() {
    val hash = HashUtil.computeRecordHash(
      entryNumber = 1L,
      dateMillis = 1700000000000L,
      createdTimestamp = 1700000005000L,
      absenceType = AbsenceTypes.NIGHT_OUT,
      reason = "Rientro a casa",
      isJustified = true,
      medicalCertificateNote = "Cert. Medico Dott. Rossi"
    )
    assertTrue("Hash should not be empty", hash.isNotEmpty())
    assertEquals("Hash should be 64 hex characters (SHA-256)", 64, hash.length)

    val isValid = HashUtil.verifyRecordHash(
      entryNumber = 1L,
      dateMillis = 1700000000000L,
      createdTimestamp = 1700000005000L,
      absenceType = AbsenceTypes.NIGHT_OUT,
      reason = "Rientro a casa",
      isJustified = true,
      medicalCertificateNote = "Cert. Medico Dott. Rossi",
      expectedHash = hash
    )
    assertTrue("Verification should pass for exact payload", isValid)
  }

  @Test
  fun `verify civil holidays and August excluded from useful days`() {
    // 15 August (Ferragosto / August)
    val august15 = Calendar.getInstance().apply {
      set(2026, Calendar.AUGUST, 15)
    }.timeInMillis
    assertTrue("August must be excluded", CivilHolidaysUtil.isExcludedDay(august15))

    // 25 December (Christmas)
    val christmas = Calendar.getInstance().apply {
      set(2025, Calendar.DECEMBER, 25)
    }.timeInMillis
    assertTrue("Christmas must be excluded", CivilHolidaysUtil.isExcludedDay(christmas))

    // 25 April (Festa Liberazione)
    val april25 = Calendar.getInstance().apply {
      set(2026, Calendar.APRIL, 25)
    }.timeInMillis
    assertTrue("25 April must be excluded", CivilHolidaysUtil.isExcludedDay(april25))

    // Standard useful day (e.g., 15 October)
    val october15 = Calendar.getInstance().apply {
      set(2025, Calendar.OCTOBER, 15)
    }.timeInMillis
    assertFalse("15 October is a useful day", CivilHolidaysUtil.isExcludedDay(october15))
  }
}
