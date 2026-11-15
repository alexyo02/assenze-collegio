package com.example.data

enum class ResidencyRiskLevel(
    val title: String,
    val description: String,
    val hexColor: Long
) {
    SAFE(
        "SITUAZIONE REGOLARE",
        "Permanenza ampiamente conforme ai requisiti (> 56%).",
        0xFF10B981 // Emerald Green
    ),
    WARNING(
        "ATTENZIONE - SOGLIA VICINA",
        "Hai consumato una parte consistente delle assenze ammissibili. Monitora le uscite.",
        0xFFF59E0B // Amber/Orange
    ),
    CRITICAL(
        "ALLARME - RISCHIO DECADENZA POSTO",
        "Superato il limite o rimaste pochissime assenze prima di scendere sotto il 56% obbligatorio!",
        0xFFEF4444 // Red
    )
}

data class ResidencyStats(
    val academicYearStart: Long,
    val academicYearEnd: Long,
    val totalUsefulDaysInYear: Int,
    val elapsedUsefulDaysSoFar: Int,
    val minRequiredPresenceDays: Int,
    val maxAllowedAbsenceDays: Int,
    val totalAbsencesCount: Int,
    val justifiedAbsencesCount: Int,
    val unjustifiedAbsencesCount: Int,
    val remainingAbsencesBefore56: Int,
    val currentAbsencePercentageOnTotal: Double,
    val currentPresencePercentageOnElapsed: Double,
    val projectedAnnualPresencePercentage: Double,
    val riskLevel: ResidencyRiskLevel,
    /** Unjustified days in the local ledger only. */
    val localUnjustifiedCount: Int = totalAbsencesCount,
    /** Official FormeIt yearly unjustified counter, when a session is available. */
    val officialUnjustifiedCount: Int? = null
) {
    /** True when the official counter is higher than the ledger and drives the statistics. */
    val isOfficialDriven: Boolean get() = (officialUnjustifiedCount ?: -1) > localUnjustifiedCount
}
