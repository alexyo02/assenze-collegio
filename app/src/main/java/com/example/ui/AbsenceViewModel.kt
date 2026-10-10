package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AbsenceRecord
import com.example.data.AbsenceRepository
import com.example.data.AbsenceTypes
import com.example.data.AppDatabase
import com.example.data.CollegeConfig
import com.example.data.CollegeSettings
import com.example.data.ResidencyRiskLevel
import com.example.data.ResidencyStats
import com.example.export.ExportManager
import com.example.util.CivilHolidaysUtil
import com.example.util.DateUtil
import com.example.util.HapticUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class AbsenceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AbsenceRepository(database.absenceDao(), database.auditDao())
    val config = CollegeConfig(application)

    val settings: StateFlow<CollegeSettings> = config.settingsFlow

    val allAbsences: StateFlow<List<AbsenceRecord>> = repository.allAbsences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Calendar month
    private val _calendarMonth = MutableStateFlow(Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    })
    val calendarMonth: StateFlow<Calendar> = _calendarMonth.asStateFlow()

    // Range selection mode (false = single date jumps; true = interval)
    private val _isRangeSelectionMode = MutableStateFlow(false)
    val isRangeSelectionMode: StateFlow<Boolean> = _isRangeSelectionMode.asStateFlow()

    // Monthly frequency chart window offset
    private val _monthlyWindowOffset = MutableStateFlow(0)
    val monthlyWindowOffset: StateFlow<Int> = _monthlyWindowOffset.asStateFlow()

    // Quick registration fields
    private val _selectedType = MutableStateFlow(AbsenceTypes.NIGHT_OUT)
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _reasonText = MutableStateFlow("")
    val reasonText: StateFlow<String> = _reasonText.asStateFlow()

    // Date range selection for the big calendar
    private val _selectedStartDate = MutableStateFlow(DateUtil.getStartOfDay())
    val selectedStartDate: StateFlow<Long> = _selectedStartDate.asStateFlow()

    private val _selectedEndDate = MutableStateFlow(DateUtil.getStartOfDay())
    val selectedEndDate: StateFlow<Long> = _selectedEndDate.asStateFlow()

    // Medical Justification state
    private val _isMedicalJustified = MutableStateFlow(false)
    val isMedicalJustified: StateFlow<Boolean> = _isMedicalJustified.asStateFlow()

    private val _medicalCertificateNote = MutableStateFlow("")
    val medicalCertificateNote: StateFlow<String> = _medicalCertificateNote.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    // Regulatory residence computation (> 56% annual requirement)
    // CRITICAL: Medical certified absences DO NOT count against the student!
    val residencyStats: StateFlow<ResidencyStats> = combine(allAbsences, settings) { records, set ->
        computeResidencyStats(records, set)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeResidencyStats(emptyList(), settings.value))

    private fun computeResidencyStats(records: List<AbsenceRecord>, set: CollegeSettings): ResidencyStats {
        val start = set.academicYearStartDate
        val end = set.academicYearEndDate

        val totalUsefulDays = CivilHolidaysUtil.countUsefulDays(start, end).coerceAtLeast(1)
        val today = DateUtil.getStartOfDay()
        val elapsedDays = CivilHolidaysUtil.countUsefulDays(start, today.coerceAtMost(end))

        val minPresenceDays = ceil(totalUsefulDays * 0.56).toInt()
        val maxAllowedAbsences = (totalUsefulDays - minPresenceDays).coerceAtLeast(0)

        // Effective absences = unjustified only! Justified with medical certificate DO NOT erode presence.
        val distinctEffectiveAbsenceDays = mutableSetOf<Long>()
        val distinctJustifiedDays = mutableSetOf<Long>()

        for (record in records) {
            val cal = Calendar.getInstance().apply { timeInMillis = record.dateMillis }
            val recordEnd = record.endDateMillis
            while (cal.timeInMillis <= recordEnd) {
                val day = cal.timeInMillis
                if (day in start..end && !CivilHolidaysUtil.isExcludedDay(day)) {
                    if (record.isJustified) {
                        distinctJustifiedDays.add(day)
                    } else {
                        distinctEffectiveAbsenceDays.add(day)
                    }
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val totalAbsences = distinctEffectiveAbsenceDays.size
        val justifiedCount = distinctJustifiedDays.size

        val remainingAbsencesBefore56 = (maxAllowedAbsences - totalAbsences).coerceAtLeast(0)
        val absencePctTotal = (totalAbsences.toDouble() / totalUsefulDays.toDouble()) * 100.0

        val elapsedPresence = (elapsedDays - totalAbsences).coerceAtLeast(0)
        val presencePctElapsed = if (elapsedDays > 0) {
            (elapsedPresence.toDouble() / elapsedDays.toDouble()) * 100.0
        } else 100.0

        val projectedPresence = ((totalUsefulDays - totalAbsences).toDouble() / totalUsefulDays.toDouble()) * 100.0

        val riskLevel = when {
            totalAbsences >= maxAllowedAbsences || projectedPresence <= 56.0 -> ResidencyRiskLevel.CRITICAL
            remainingAbsencesBefore56 <= 15 || absencePctTotal >= 32.0 -> ResidencyRiskLevel.WARNING
            else -> ResidencyRiskLevel.SAFE
        }

        return ResidencyStats(
            academicYearStart = start,
            academicYearEnd = end,
            totalUsefulDaysInYear = totalUsefulDays,
            elapsedUsefulDaysSoFar = elapsedDays,
            minRequiredPresenceDays = minPresenceDays,
            maxAllowedAbsenceDays = maxAllowedAbsences,
            totalAbsencesCount = totalAbsences,
            justifiedAbsencesCount = justifiedCount,
            unjustifiedAbsencesCount = totalAbsences,
            remainingAbsencesBefore56 = remainingAbsencesBefore56,
            currentAbsencePercentageOnTotal = absencePctTotal,
            currentPresencePercentageOnElapsed = presencePctElapsed,
            projectedAnnualPresencePercentage = projectedPresence,
            riskLevel = riskLevel
        )
    }

    // Calendar navigation
    fun nextMonth() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _calendarMonth.value.timeInMillis
            add(Calendar.MONTH, 1)
        }
        _calendarMonth.value = cal
    }

    fun previousMonth() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _calendarMonth.value.timeInMillis
            add(Calendar.MONTH, -1)
        }
        _calendarMonth.value = cal
    }

    // Monthly chart navigation
    fun previousMonthlyWindow() {
        _monthlyWindowOffset.value -= 1
    }

    fun nextMonthlyWindow() {
        _monthlyWindowOffset.value += 1
    }

    fun setRangeSelectionMode(enabled: Boolean) {
        _isRangeSelectionMode.value = enabled
        if (!enabled) {
            _selectedEndDate.value = _selectedStartDate.value
        }
    }

    /**
     * Non-sticky date selection:
     * - Clicking any new date jumps directly to that date (single day).
     * - A second tap on the same selected date triggers interval mode.
     * - In interval mode, tapping a date extends to that end date.
     */
    fun onCalendarDateClicked(dateMillis: Long) {
        val clickedDay = DateUtil.getStartOfDay(dateMillis)
        val currentStart = _selectedStartDate.value
        val currentEnd = _selectedEndDate.value

        if (!_isRangeSelectionMode.value) {
            if (clickedDay == currentStart && currentStart == currentEnd) {
                // Second tap on the same selected day: TOGGLE ON range mode
                _isRangeSelectionMode.value = true
                _snackbarMessage.value = "Modalità intervallo attiva: tocca la data di fine"
            } else {
                // JUMP to clicked date
                _selectedStartDate.value = clickedDay
                _selectedEndDate.value = clickedDay
            }
        } else {
            // Range mode is currently active:
            if (clickedDay == currentStart || clickedDay == currentEnd) {
                // Second tap on selected boundary: TOGGLE OFF range mode
                _isRangeSelectionMode.value = false
                _selectedStartDate.value = clickedDay
                _selectedEndDate.value = clickedDay
                _snackbarMessage.value = "Modalità giorno singolo attiva"
            } else {
                if (clickedDay >= currentStart) {
                    _selectedEndDate.value = clickedDay
                } else {
                    _selectedStartDate.value = clickedDay
                }
            }
        }
    }

    fun setDateRange(startMillis: Long, endMillis: Long) {
        val start = DateUtil.getStartOfDay(minOf(startMillis, endMillis))
        val end = DateUtil.getStartOfDay(maxOf(startMillis, endMillis))
        _selectedStartDate.value = start
        _selectedEndDate.value = end
        _isRangeSelectionMode.value = (start != end)
    }

    fun setSelectedType(type: String) {
        _selectedType.value = type
    }

    fun setReasonText(text: String) {
        _reasonText.value = text
    }

    fun setMedicalJustified(justified: Boolean) {
        _isMedicalJustified.value = justified
    }

    fun setMedicalCertificateNote(note: String) {
        _medicalCertificateNote.value = note
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    /**
     * Saves the selected date or range as a SINGLE interval record.
     */
    fun saveAbsence() {
        val app = getApplication<Application>()
        if (settings.value.hapticEnabled) {
            HapticUtil.performConfirmHaptic(app)
        }

        viewModelScope.launch {
            try {
                val start = _selectedStartDate.value
                val end = _selectedEndDate.value

                // Check for overlapping days in existing absences
                val existingRecords = allAbsences.value
                val overlapping = existingRecords.filter { existing ->
                    !(end < existing.dateMillis || start > existing.endDateMillis)
                }

                if (overlapping.isNotEmpty()) {
                    val isFullyCovered = overlapping.any { it.dateMillis <= start && it.endDateMillis >= end }
                    if (isFullyCovered) {
                        _snackbarMessage.value = if (start == end) {
                            "Il giorno ${DateUtil.formatShortDate(start)} è già registrato come assenza."
                        } else {
                            "L'intervallo selezionato è già registrato come assenza."
                        }
                        return@launch
                    }
                }

                val record = repository.registerAbsence(
                    startDateMillis = start,
                    endDateMillis = end,
                    absenceType = _selectedType.value,
                    reason = _reasonText.value,
                    isJustified = _isMedicalJustified.value,
                    medicalCertificateNote = _medicalCertificateNote.value
                )

                _reasonText.value = ""
                _isMedicalJustified.value = false
                _medicalCertificateNote.value = ""
                _isRangeSelectionMode.value = false

                if (record.isRange) {
                    _snackbarMessage.value = "Intervallo registrato: ${record.formattedShortDate}"
                } else {
                    _snackbarMessage.value = "Assenza registrata per il ${record.formattedShortDate}"
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Errore durante il salvataggio: ${e.localizedMessage}"
            }
        }
    }

    fun updateAbsence(
        recordId: Long,
        newStartDateMillis: Long,
        newEndDateMillis: Long = newStartDateMillis,
        newType: String,
        newReason: String,
        isJustified: Boolean,
        medicalNote: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.updateAbsence(
                id = recordId,
                newStartDateMillis = newStartDateMillis,
                newEndDateMillis = newEndDateMillis,
                newType = newType,
                newReason = newReason,
                isJustified = isJustified,
                medicalCertificateNote = medicalNote
            )
            if (success) {
                _snackbarMessage.value = "Assenza aggiornata."
                onSuccess()
            }
        }
    }

    /**
     * Splits an interval into individual single-day entries in the ledger.
     */
    fun explodeInterval(recordId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.explodeInterval(recordId)
            if (success) {
                _snackbarMessage.value = "Intervallo suddiviso in date singole."
                onSuccess()
            }
        }
    }

    /**
     * Removes a single date from an interval.
     */
    fun removeSingleDayFromInterval(recordId: Long, dayMillis: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.removeSingleDayFromInterval(recordId, dayMillis)
            if (success) {
                _snackbarMessage.value = "Giorno rimosso dall'intervallo."
                onSuccess()
            }
        }
    }

    fun deleteAbsence(recordId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteAbsence(recordId)
            if (success) {
                _snackbarMessage.value = "Assenza eliminata."
                onSuccess()
            }
        }
    }

    fun triggerCloudSync(onFileReady: ((File) -> Unit)? = null) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            try {
                kotlinx.coroutines.delay(400)
                val records = repository.getAllRecordsDirect()
                val logs = repository.getAllAuditLogsDirect()
                val backupFile = ExportManager.exportDatabaseJson(
                    getApplication(),
                    settings.value,
                    records,
                    logs
                )
                config.markCloudSyncCompleted(System.currentTimeMillis())
                _snackbarMessage.value = "Backup salvato con successo."
                onFileReady?.invoke(backupFile)
            } catch (e: Exception) {
                _snackbarMessage.value = "Errore durante il salvataggio: ${e.localizedMessage}"
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    fun exportDatabaseJson(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val records = repository.getAllRecordsDirect()
            val logs = repository.getAllAuditLogsDirect()
            val file = ExportManager.exportDatabaseJson(getApplication(), settings.value, records, logs)
            onReady(file)
        }
    }

    fun exportCsv(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val records = repository.getAllRecordsDirect()
            val file = ExportManager.exportToCsv(getApplication(), records, residencyStats.value)
            onReady(file)
        }
    }

    fun exportPdf(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val records = repository.getAllRecordsDirect()
            val file = ExportManager.exportToPdf(getApplication(), settings.value, records, residencyStats.value)
            onReady(file)
        }
    }

    fun savePdfToUri(uri: Uri, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val records = repository.getAllRecordsDirect()
                val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                if (out != null) {
                    out.use {
                        ExportManager.writePdfToStream(settings.value, records, residencyStats.value, it)
                    }
                    _snackbarMessage.value = "Report PDF salvato con successo."
                    onSuccess()
                } else {
                    _snackbarMessage.value = "Impossibile salvare il PDF sul dispositivo."
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Errore durante il salvataggio: ${e.localizedMessage}"
            }
        }
    }

    fun saveCsvToUri(uri: Uri, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val records = repository.getAllRecordsDirect()
                val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                if (out != null) {
                    out.use {
                        ExportManager.writeCsvToStream(records, residencyStats.value, it)
                    }
                    _snackbarMessage.value = "Tabella CSV salvata con successo."
                    onSuccess()
                } else {
                    _snackbarMessage.value = "Impossibile salvare il CSV sul dispositivo."
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Errore durante il salvataggio: ${e.localizedMessage}"
            }
        }
    }

    fun saveJsonToUri(uri: Uri, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val records = repository.getAllRecordsDirect()
                val logs = repository.getAllAuditLogsDirect()
                val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                if (out != null) {
                    out.use {
                        ExportManager.writeJsonToStream(settings.value, records, logs, it)
                    }
                    _snackbarMessage.value = "Backup salvato con successo."
                    onSuccess()
                } else {
                    _snackbarMessage.value = "Impossibile salvare il backup sul dispositivo."
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Errore durante il salvataggio: ${e.localizedMessage}"
            }
        }
    }

    fun importDatabaseFromUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    onResult(false, "Impossibile aprire il file.")
                    return@launch
                }
                val payload = ExportManager.importDatabaseJson(inputStream)
                if (payload == null) {
                    onResult(false, "File non valido o corrotto.")
                    return@launch
                }

                repository.restoreDatabase(
                    records = payload.records,
                    logs = payload.auditLogs,
                    clearExisting = true
                )

                config.updateSettings(
                    settings.value.copy(
                        studentName = payload.studentName,
                        collegeName = payload.collegeName,
                        roomNumber = payload.roomNumber,
                        academicYear = payload.academicYear,
                        academicYearStartDate = payload.academicYearStartDate
                    )
                )

                _snackbarMessage.value = "Archivio ripristinato: ${payload.records.size} voci importate."
                onResult(true, "Ripristinate ${payload.records.size} voci.")
            } catch (e: Exception) {
                onResult(false, "Errore durante il ripristino: ${e.localizedMessage}")
            }
        }
    }

    fun saveSettings(newSettings: CollegeSettings) {
        config.updateSettings(newSettings)
        _snackbarMessage.value = "Impostazioni aggiornate."
    }

    // --- AUTOMATIC MONTHLY BACKUP SYSTEM ---
    private val _monthlyBackups = MutableStateFlow<List<MonthlyBackupItem>>(emptyList())
    val monthlyBackups: StateFlow<List<MonthlyBackupItem>> = _monthlyBackups.asStateFlow()

    init {
        checkAndPerformMonthlyBackup()
        loadMonthlyBackups()
    }

    fun loadMonthlyBackups() {
        val app = getApplication<Application>()
        val dir = File(app.filesDir, "monthly_backups")
        if (!dir.exists()) {
            _monthlyBackups.value = emptyList()
            return
        }
        val files = dir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        val items = files.mapNotNull { file ->
            try {
                val payload = file.inputStream().use { ExportManager.importDatabaseJson(it) }
                val monthKey = file.nameWithoutExtension.removePrefix("auto_backup_")
                MonthlyBackupItem(
                    monthKey = monthKey,
                    displayTitle = formatMonthTitle(monthKey),
                    timestamp = file.lastModified(),
                    recordCount = payload?.records?.size ?: 0,
                    file = file
                )
            } catch (_: Exception) {
                null
            }
        }
        _monthlyBackups.value = items
    }

    private fun formatMonthTitle(key: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.ITALIAN)
            val date = sdf.parse(key) ?: Date()
            SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(date).replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            key
        }
    }

    fun checkAndPerformMonthlyBackup() {
        viewModelScope.launch {
            if (!settings.value.autoMonthlyBackupEnabled) return@launch
            val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.ITALIAN).format(Date())
            val lastMonthKey = settings.value.lastMonthlyBackupMonth
            if (currentMonthKey != lastMonthKey) {
                performMonthlyBackup(currentMonthKey)
            }
        }
    }

    fun performMonthlyBackupNow() {
        viewModelScope.launch {
            val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.ITALIAN).format(Date())
            performMonthlyBackup(currentMonthKey)
            _snackbarMessage.value = "Backup mensile salvato."
        }
    }

    private suspend fun performMonthlyBackup(monthKey: String) {
        try {
            val records = repository.getAllRecordsDirect()
            val logs = repository.getAllAuditLogsDirect()
            val app = getApplication<Application>()
            val dir = File(app.filesDir, "monthly_backups").apply { mkdirs() }
            val file = File(dir, "auto_backup_$monthKey.json")
            FileOutputStream(file).use { out ->
                ExportManager.writeJsonToStream(settings.value, records, logs, out)
            }
            config.setLastMonthlyBackupMonth(monthKey)
            loadMonthlyBackups()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreMonthlyBackup(item: MonthlyBackupItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val payload = item.file.inputStream().use { ExportManager.importDatabaseJson(it) }
                if (payload != null) {
                    repository.restoreDatabase(payload.records, payload.auditLogs, clearExisting = true)
                    config.updateSettings(
                        settings.value.copy(
                            studentName = payload.studentName,
                            collegeName = payload.collegeName,
                            roomNumber = payload.roomNumber,
                            academicYear = payload.academicYear,
                            academicYearStartDate = payload.academicYearStartDate
                        )
                    )
                    _snackbarMessage.value = "Ripristinato backup di ${item.displayTitle} (${payload.records.size} voci)."
                    onResult(true)
                } else {
                    _snackbarMessage.value = "File di backup non valido."
                    onResult(false)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "Errore durante il ripristino: ${e.localizedMessage}"
                onResult(false)
            }
        }
    }

    // --- SECURITY LOCK & AUTHENTICATION (PIN) ---
    fun verifySecurityPin(enteredPin: String): Boolean {
        return enteredPin == settings.value.securityPin
    }

    fun setSecurityLockEnabled(enabled: Boolean) {
        config.setSecurityLockEnabled(enabled)
        _snackbarMessage.value = if (enabled) "Lucchetto di sicurezza ATTIVATO 🔒" else "Lucchetto di sicurezza DISATTIVATO 🔓"
    }

    fun updateSecurityPin(newPin: String) {
        if (newPin.length == 4) {
            config.setSecurityPin(newPin)
            _snackbarMessage.value = "PIN di sicurezza aggiornato."
        }
    }

    fun setAutoMonthlyBackupEnabled(enabled: Boolean) {
        config.setAutoMonthlyBackupEnabled(enabled)
        _snackbarMessage.value = if (enabled) "Backup automatico mensile attivato." else "Backup automatico disattivato."
    }
}

data class MonthlyBackupItem(
    val monthKey: String,
    val displayTitle: String,
    val timestamp: Long,
    val recordCount: Int,
    val file: File
)
