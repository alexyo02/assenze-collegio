package com.example.data

import com.example.util.DateUtil
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class AbsenceRepository(
    private val absenceDao: AbsenceDao,
    private val auditDao: AuditDao
) {
    val allAbsences: Flow<List<AbsenceRecord>> = absenceDao.getAllAbsences()
    val totalCount: Flow<Int> = absenceDao.getAbsenceCount()

    fun getAbsencesForDate(dateMillis: Long): Flow<List<AbsenceRecord>> {
        val startOfDay = DateUtil.getStartOfDay(dateMillis)
        return absenceDao.getAbsencesForDate(startOfDay)
    }

    suspend fun registerAbsence(
        startDateMillis: Long,
        endDateMillis: Long = startDateMillis,
        absenceType: String,
        reason: String,
        isJustified: Boolean = false,
        medicalCertificateNote: String? = null
    ): AbsenceRecord {
        val start = DateUtil.getStartOfDay(minOf(startDateMillis, endDateMillis))
        val end = DateUtil.getStartOfDay(maxOf(startDateMillis, endDateMillis))

        val maxEntry = absenceDao.getMaxEntryNumber() ?: 0L
        val nextEntryNumber = maxEntry + 1L
        val now = System.currentTimeMillis()

        val cleanReason = reason.trim()
        val cleanMedNote = medicalCertificateNote?.trim()?.ifBlank { null }

        val record = AbsenceRecord(
            entryNumber = nextEntryNumber,
            dateMillis = start,
            endDateMillis = end,
            createdTimestamp = now,
            absenceType = absenceType,
            reason = cleanReason,
            isLocked = false,
            auditHash = "",
            syncStatus = "SYNCED",
            lastModifiedTimestamp = now,
            isJustified = isJustified,
            medicalCertificateNote = cleanMedNote
        )

        val insertedId = absenceDao.insert(record)
        return record.copy(id = insertedId)
    }

    suspend fun updateAbsence(
        id: Long,
        newStartDateMillis: Long,
        newEndDateMillis: Long = newStartDateMillis,
        newType: String,
        newReason: String,
        isJustified: Boolean,
        medicalCertificateNote: String?
    ): Boolean {
        val existing = absenceDao.getById(id) ?: return false
        val start = DateUtil.getStartOfDay(minOf(newStartDateMillis, newEndDateMillis))
        val end = DateUtil.getStartOfDay(maxOf(newStartDateMillis, newEndDateMillis))
        val now = System.currentTimeMillis()

        val cleanReason = newReason.trim()
        val cleanMedNote = medicalCertificateNote?.trim()?.ifBlank { null }

        val updated = existing.copy(
            dateMillis = start,
            endDateMillis = end,
            absenceType = newType,
            reason = cleanReason,
            isJustified = isJustified,
            medicalCertificateNote = cleanMedNote,
            lastModifiedTimestamp = now
        )

        absenceDao.update(updated)
        return true
    }

    /**
     * Splits a multi-day interval into individual single-day absence records.
     */
    suspend fun explodeInterval(recordId: Long): Boolean {
        val existing = absenceDao.getById(recordId) ?: return false
        if (!existing.isRange) return false

        absenceDao.delete(existing)

        val cal = Calendar.getInstance().apply { timeInMillis = existing.dateMillis }
        var nextEntry = (absenceDao.getMaxEntryNumber() ?: 0L) + 1L
        val newRecords = mutableListOf<AbsenceRecord>()

        while (cal.timeInMillis <= existing.endDateMillis) {
            val day = cal.timeInMillis
            newRecords.add(
                AbsenceRecord(
                    entryNumber = nextEntry++,
                    dateMillis = day,
                    endDateMillis = day,
                    createdTimestamp = existing.createdTimestamp,
                    absenceType = existing.absenceType,
                    reason = existing.reason,
                    isJustified = existing.isJustified,
                    medicalCertificateNote = existing.medicalCertificateNote,
                    lastModifiedTimestamp = System.currentTimeMillis()
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        absenceDao.insertAll(newRecords)
        return true
    }

    /**
     * Removes a single day from an interval, updating boundaries or splitting if in the middle.
     */
    suspend fun removeSingleDayFromInterval(recordId: Long, dayToRemoveMillis: Long): Boolean {
        val existing = absenceDao.getById(recordId) ?: return false
        val day = DateUtil.getStartOfDay(dayToRemoveMillis)

        if (day < existing.dateMillis || day > existing.endDateMillis) return false

        if (existing.dateMillis == existing.endDateMillis) {
            absenceDao.delete(existing)
            return true
        }

        if (day == existing.dateMillis) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = existing.dateMillis
                add(Calendar.DAY_OF_MONTH, 1)
            }
            absenceDao.update(existing.copy(dateMillis = cal.timeInMillis, lastModifiedTimestamp = System.currentTimeMillis()))
            return true
        }

        if (day == existing.endDateMillis) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = existing.endDateMillis
                add(Calendar.DAY_OF_MONTH, -1)
            }
            absenceDao.update(existing.copy(endDateMillis = cal.timeInMillis, lastModifiedTimestamp = System.currentTimeMillis()))
            return true
        }

        // Day in the middle: split into 2 intervals
        val cal1 = Calendar.getInstance().apply {
            timeInMillis = day
            add(Calendar.DAY_OF_MONTH, -1)
        }
        val cal2 = Calendar.getInstance().apply {
            timeInMillis = day
            add(Calendar.DAY_OF_MONTH, 1)
        }

        val maxEntry = absenceDao.getMaxEntryNumber() ?: 0L
        val part1 = existing.copy(endDateMillis = cal1.timeInMillis, lastModifiedTimestamp = System.currentTimeMillis())
        val part2 = existing.copy(id = 0, entryNumber = maxEntry + 1L, dateMillis = cal2.timeInMillis, lastModifiedTimestamp = System.currentTimeMillis())

        absenceDao.update(part1)
        absenceDao.insert(part2)
        return true
    }

    suspend fun deleteAbsence(id: Long): Boolean {
        val existing = absenceDao.getById(id) ?: return false
        absenceDao.delete(existing)
        return true
    }

    suspend fun getAllRecordsDirect(): List<AbsenceRecord> = absenceDao.getAllDirect()

    suspend fun getAllAuditLogsDirect(): List<AuditLogEntry> = auditDao.getAllLogsDirect()

    suspend fun restoreDatabase(
        records: List<AbsenceRecord>,
        logs: List<AuditLogEntry>,
        clearExisting: Boolean = true
    ) {
        if (clearExisting) {
            absenceDao.clearAll()
            auditDao.clearLogs()
        }
        absenceDao.insertAll(records)
        auditDao.insertAllLogs(logs)
    }
}
