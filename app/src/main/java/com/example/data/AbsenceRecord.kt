package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.util.DateUtil
import com.squareup.moshi.JsonClass

object AbsenceTypes {
    const val NIGHT_OUT = "NOTTE_FUORI"
    const val HOME_RETURN = "RITORNO_A_CASA"
    const val TRAVEL = "VIAGGIO"

    val allTypes = listOf(NIGHT_OUT, HOME_RETURN, TRAVEL)

    fun getDisplayName(type: String): String = when (type) {
        NIGHT_OUT -> "Notte Fuori"
        HOME_RETURN -> "Ritorno a Casa"
        TRAVEL -> "Viaggio"
        else -> type
    }

    fun getEmoji(type: String): String = when (type) {
        NIGHT_OUT -> "🌙"
        HOME_RETURN -> "🏠"
        TRAVEL -> "✈️"
        else -> "📋"
    }

    fun getColorHex(type: String): Long = when (type) {
        NIGHT_OUT -> 0xFF7C3AED  // Violet
        HOME_RETURN -> 0xFF2563EB // Royal Blue
        TRAVEL -> 0xFFF59E0B      // Amber
        else -> 0xFF6B7280
    }
}

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "absence_records",
    indices = [
        Index(value = ["dateMillis"]),
        Index(value = ["endDateMillis"]),
        Index(value = ["entryNumber"], unique = true)
    ]
)
data class AbsenceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryNumber: Long,
    val dateMillis: Long,          // Start date (start of day)
    val endDateMillis: Long = dateMillis, // End date (start of day)
    val createdTimestamp: Long = System.currentTimeMillis(),
    val absenceType: String = AbsenceTypes.NIGHT_OUT,
    val reason: String = "",
    val isLocked: Boolean = false,
    val auditHash: String = "",
    val syncStatus: String = "SYNCED",
    val lastModifiedTimestamp: Long = createdTimestamp,
    val modificationReason: String? = null,
    val isJustified: Boolean = false,
    val medicalCertificateNote: String? = null
) {
    val isRange: Boolean
        get() = endDateMillis > dateMillis

    val daysCount: Int
        get() = (((endDateMillis - dateMillis) / (1000 * 60 * 60 * 24)).toInt() + 1).coerceAtLeast(1)

    val formattedDate: String
        get() = if (isRange) {
            "${DateUtil.formatShortDate(dateMillis)} - ${DateUtil.formatFullDate(endDateMillis)} ($daysCount notti)"
        } else {
            DateUtil.formatFullDate(dateMillis)
        }

    val formattedShortDate: String
        get() = if (isRange) {
            "${DateUtil.formatShortDate(dateMillis)} - ${DateUtil.formatShortDate(endDateMillis)} ($daysCount notti)"
        } else {
            DateUtil.formatShortDate(dateMillis)
        }

    val formattedCreatedTime: String
        get() = DateUtil.formatDateTime(createdTimestamp)

    val typeDisplayName: String
        get() = AbsenceTypes.getDisplayName(absenceType)

    val typeEmoji: String
        get() = AbsenceTypes.getEmoji(absenceType)

    fun containsDate(dayMillis: Long): Boolean {
        val day = DateUtil.getStartOfDay(dayMillis)
        return day in dateMillis..endDateMillis
    }
}
