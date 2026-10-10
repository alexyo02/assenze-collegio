package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.util.CivilHolidaysUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CollegeSettings(
    val studentName: String = "Alessio Troisi",
    val collegeName: String = "Residenza Universitaria",
    val roomNumber: String = "Stanza 101",
    val academicYear: String = "2025/2026",
    val securityPin: String = "6339",
    val isSecurityLockEnabled: Boolean = false,
    val hapticEnabled: Boolean = true,
    val autoCloudBackup: Boolean = true,
    val autoMonthlyBackupEnabled: Boolean = true,
    val lastMonthlyBackupMonth: String = "",
    val cloudEmail: String = "studente@collegio.it",
    val lastCloudSync: Long = 0L,
    val academicYearStartDate: Long = CivilHolidaysUtil.getDefaultAcademicYearStartDate(),
    val academicYearEndDate: Long = CivilHolidaysUtil.getAcademicYearEndDate(CivilHolidaysUtil.getDefaultAcademicYearStartDate()),
    val minRequiredPresencePercentage: Double = 56.0
)

class CollegeConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("college_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<CollegeSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): CollegeSettings {
        val defaultStart = CivilHolidaysUtil.getDefaultAcademicYearStartDate()
        val startDate = prefs.getLong("academic_year_start", defaultStart)
        val endDate = CivilHolidaysUtil.getAcademicYearEndDate(startDate)

        return CollegeSettings(
            studentName = prefs.getString("student_name", "Alessio Troisi") ?: "Alessio Troisi",
            collegeName = prefs.getString("college_name", "Residenza Universitaria") ?: "Residenza Universitaria",
            roomNumber = prefs.getString("room_number", "Stanza 101") ?: "Stanza 101",
            academicYear = prefs.getString("academic_year", "2025/2026") ?: "2025/2026",
            securityPin = prefs.getString("security_pin", "6339") ?: "6339",
            isSecurityLockEnabled = prefs.getBoolean("security_lock_enabled", false),
            hapticEnabled = prefs.getBoolean("haptic_enabled", true),
            autoCloudBackup = prefs.getBoolean("auto_cloud_backup", true),
            autoMonthlyBackupEnabled = prefs.getBoolean("auto_monthly_backup_enabled", true),
            lastMonthlyBackupMonth = prefs.getString("last_monthly_backup_month", "") ?: "",
            cloudEmail = prefs.getString("cloud_email", "alessiotroisi02@gmail.com") ?: "alessiotroisi02@gmail.com",
            lastCloudSync = prefs.getLong("last_cloud_sync", 0L),
            academicYearStartDate = startDate,
            academicYearEndDate = endDate,
            minRequiredPresencePercentage = 56.0
        )
    }

    fun updateSettings(newSettings: CollegeSettings) {
        val calculatedEnd = CivilHolidaysUtil.getAcademicYearEndDate(newSettings.academicYearStartDate)
        val finalSettings = newSettings.copy(academicYearEndDate = calculatedEnd)

        prefs.edit()
            .putString("student_name", finalSettings.studentName)
            .putString("college_name", finalSettings.collegeName)
            .putString("room_number", finalSettings.roomNumber)
            .putString("academic_year", finalSettings.academicYear)
            .putString("security_pin", finalSettings.securityPin)
            .putBoolean("security_lock_enabled", finalSettings.isSecurityLockEnabled)
            .putBoolean("haptic_enabled", finalSettings.hapticEnabled)
            .putBoolean("auto_cloud_backup", finalSettings.autoCloudBackup)
            .putBoolean("auto_monthly_backup_enabled", finalSettings.autoMonthlyBackupEnabled)
            .putString("last_monthly_backup_month", finalSettings.lastMonthlyBackupMonth)
            .putString("cloud_email", finalSettings.cloudEmail)
            .putLong("last_cloud_sync", finalSettings.lastCloudSync)
            .putLong("academic_year_start", finalSettings.academicYearStartDate)
            .apply()
        _settingsFlow.value = finalSettings
    }

    fun setSecurityLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("security_lock_enabled", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isSecurityLockEnabled = enabled)
    }

    fun setSecurityPin(pin: String) {
        prefs.edit().putString("security_pin", pin).apply()
        _settingsFlow.value = _settingsFlow.value.copy(securityPin = pin)
    }

    fun setAutoMonthlyBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_monthly_backup_enabled", enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(autoMonthlyBackupEnabled = enabled)
    }

    fun setLastMonthlyBackupMonth(monthYear: String) {
        prefs.edit().putString("last_monthly_backup_month", monthYear).apply()
        _settingsFlow.value = _settingsFlow.value.copy(lastMonthlyBackupMonth = monthYear)
    }

    fun markCloudSyncCompleted(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_cloud_sync", timestamp).apply()
        _settingsFlow.value = _settingsFlow.value.copy(lastCloudSync = timestamp)
    }
}
