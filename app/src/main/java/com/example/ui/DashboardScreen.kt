package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AbsenceRecord
import com.example.data.AbsenceTypes
import com.example.data.ResidencyRiskLevel
import com.example.util.CivilHolidaysUtil
import com.example.util.DateUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: AbsenceViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allAbsences by viewModel.allAbsences.collectAsStateWithLifecycle()
    val residencyStats by viewModel.residencyStats.collectAsStateWithLifecycle()
    val calendarMonth by viewModel.calendarMonth.collectAsStateWithLifecycle()

    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val reasonText by viewModel.reasonText.collectAsStateWithLifecycle()
    val selectedStartDate by viewModel.selectedStartDate.collectAsStateWithLifecycle()
    val selectedEndDate by viewModel.selectedEndDate.collectAsStateWithLifecycle()
    val isRangeSelectionMode by viewModel.isRangeSelectionMode.collectAsStateWithLifecycle()
    val isMedicalJustified by viewModel.isMedicalJustified.collectAsStateWithLifecycle()
    val medicalCertificateNote by viewModel.medicalCertificateNote.collectAsStateWithLifecycle()

    val selectedDaysCount = remember(selectedStartDate, selectedEndDate) {
        (((selectedEndDate - selectedStartDate) / (1000 * 60 * 60 * 24)).toInt() + 1).coerceAtLeast(1)
    }

    val todayMillis = remember { DateUtil.getStartOfDay() }
    val academicStartMillis = remember(settings.academicYearStartDate) {
        DateUtil.getStartOfDay(settings.academicYearStartDate)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ESSENTIAL HERO CARD: PERMANENZA REGOLAMENTARE (> 56%)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Permanenza Annuale",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", residencyStats.projectedAnnualPresencePercentage),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val badgeColor = when (residencyStats.riskLevel) {
                            ResidencyRiskLevel.SAFE -> Color(0xFF10B981)
                            ResidencyRiskLevel.WARNING -> Color(0xFFF59E0B)
                            ResidencyRiskLevel.CRITICAL -> Color(0xFFEF4444)
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = when (residencyStats.riskLevel) {
                                    ResidencyRiskLevel.SAFE -> "Regolare (> 56%)"
                                    ResidencyRiskLevel.WARNING -> "Attenzione"
                                    ResidencyRiskLevel.CRITICAL -> "Sotto Soglia"
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = badgeColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val progressFraction = (residencyStats.totalAbsencesCount.toFloat() / residencyStats.maxAllowedAbsenceDays.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${residencyStats.totalAbsencesCount} assenze registrate",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${residencyStats.remainingAbsencesBefore56} giorni disponibili",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // BIG CALENDAR: UNIFORM CIRCLE SIZE (36.dp) & CLEAR COLOR STATES
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Month & Navigation Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN)
                        val monthTitle = monthFormat.format(calendarMonth.time).replaceFirstChar { it.uppercase(Locale.ITALIAN) }

                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.previousMonth() }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Mese Precedente")
                            }
                            IconButton(onClick = { viewModel.nextMonth() }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Mese Successivo")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Days of week header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val days = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
                        days.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val daysInMonth = remember(calendarMonth) {
                        computeMonthGrid(calendarMonth)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        daysInMonth.chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                week.forEach { dayItem ->
                                    if (dayItem == null) {
                                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val dayMillis = dayItem.dateMillis
                                        val isSelected = dayMillis in selectedStartDate..selectedEndDate
                                        // GREEN IS ONLY ELIGIBLE FROM academicStartMillis UP TO todayMillis!
                                        val isPastInAcademicYear = dayMillis >= academicStartMillis && dayMillis <= todayMillis
                                        val isExcluded = CivilHolidaysUtil.isExcludedDay(dayMillis)

                                        val matchingAbsence = allAbsences.firstOrNull { it.containsDate(dayMillis) }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            UniformCalendarDay(
                                                dayNumber = dayItem.dayNumber,
                                                isPastInAcademicYear = isPastInAcademicYear,
                                                isExcluded = isExcluded,
                                                matchingAbsence = matchingAbsence,
                                                isSelected = isSelected,
                                                onClick = { viewModel.onCalendarDateClicked(dayMillis) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Color Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorDotLegend(color = Color(0xFF10B981), label = "Presente")
                        ColorDotLegend(color = Color(0xFFEF4444), label = "Assenza")
                        ColorDotLegend(color = Color(0xFF2563EB), label = "Certificato")
                        ExcludedDotLegend(label = "Escluso (✕)")
                    }
                }
            }
        }

        // FAST REGISTRATION CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Periodo Selezionato",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val rangeLabel = if (selectedDaysCount == 1) {
                                DateUtil.formatFullDate(selectedStartDate)
                            } else {
                                "${DateUtil.formatShortDate(selectedStartDate)} - ${DateUtil.formatShortDate(selectedEndDate)} ($selectedDaysCount notti)"
                            }
                            Text(
                                text = rangeLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!DateUtil.isSameDay(selectedStartDate, System.currentTimeMillis())) {
                            TextButton(
                                onClick = {
                                    val today = DateUtil.getStartOfDay()
                                    viewModel.setDateRange(today, today)
                                }
                            ) {
                                Text("Oggi")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Selection Mode Toggle: Giorno Singolo vs Intervallo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { viewModel.setRangeSelectionMode(false) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (!isRangeSelectionMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Giorno Singolo",
                                    fontSize = 12.sp,
                                    fontWeight = if (!isRangeSelectionMode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isRangeSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            onClick = { viewModel.setRangeSelectionMode(true) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isRangeSelectionMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Intervallo Date",
                                    fontSize = 12.sp,
                                    fontWeight = if (isRangeSelectionMode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isRangeSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3 Types Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AbsenceTypes.allTypes.forEach { type ->
                            val isSelected = selectedType == type
                            Surface(
                                onClick = { viewModel.setSelectedType(type) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = AbsenceTypes.getEmoji(type),
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (type) {
                                            AbsenceTypes.NIGHT_OUT -> "Notte Fuori"
                                            AbsenceTypes.HOME_RETURN -> "Casa"
                                            AbsenceTypes.TRAVEL -> "Viaggio"
                                            else -> AbsenceTypes.getDisplayName(type)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Note Input
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { viewModel.setReasonText(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("absence_reason_input"),
                        label = { Text("Note (opzionale)") },
                        placeholder = { Text("es. Weekend a casa, studio...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Medical Justification Toggle
                    Surface(
                        color = if (isMedicalJustified) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocalHospital,
                                        contentDescription = "Certificato Medico",
                                        tint = if (isMedicalJustified) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Certificato Medico",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isMedicalJustified) Color(0xFF1D4ED8) else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Switch(
                                    checked = isMedicalJustified,
                                    onCheckedChange = { viewModel.setMedicalJustified(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2563EB)
                                    )
                                )
                            }

                            AnimatedVisibility(visible = isMedicalJustified) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    OutlinedTextField(
                                        value = medicalCertificateNote,
                                        onValueChange = { viewModel.setMedicalCertificateNote(it) },
                                        label = { Text("Dettagli Certificato") },
                                        placeholder = { Text("es. Visita medica, n. certificato...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ACTION BUTTON: "REGISTRA ASSENZA"
                    Button(
                        onClick = { viewModel.saveAbsence() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_absence_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Aggiungi", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            val btnLabel = if (selectedDaysCount == 1) "Registra Assenza" else "Registra Intervallo ($selectedDaysCount notti)"
                            Text(
                                text = btnLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // RECENT ABSENCES SECTION
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ultime Assenze",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToHistory) {
                    Text("Vedi Tutte (${allAbsences.size})")
                }
            }
        }

        if (allAbsences.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "Nessuna assenza registrata finora.",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(allAbsences.take(3)) { record ->
                ExpressiveAbsenceCard(record = record, onClick = onNavigateToHistory)
            }
        }
    }
}

data class CalendarDayItem(
    val dayNumber: Int,
    val dateMillis: Long
)

private fun computeMonthGrid(calendar: Calendar): List<CalendarDayItem?> {
    val result = mutableListOf<CalendarDayItem?>()
    val cal = Calendar.getInstance().apply {
        timeInMillis = calendar.timeInMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    for (i in 0 until firstDayOfWeek) {
        result.add(null)
    }

    for (day in 1..daysInMonth) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        result.add(CalendarDayItem(dayNumber = day, dateMillis = cal.timeInMillis))
    }

    // Always pad the trailing cells to complete the 7-column grid
    while (result.size % 7 != 0) {
        result.add(null)
    }

    return result
}

@Composable
fun UniformCalendarDay(
    dayNumber: Int,
    isPastInAcademicYear: Boolean,
    isExcluded: Boolean,
    matchingAbsence: AbsenceRecord?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Exactly 34dp circle for EVERY day
    val cellColor = when {
        matchingAbsence != null && matchingAbsence.isJustified -> Color(0xFF2563EB) // Blue
        matchingAbsence != null -> Color(0xFFEF4444)                            // Red
        isSelected -> MaterialTheme.colorScheme.primary                         // Selected Purple
        isExcluded -> Color(0xFFCBD5E1)                                         // Grey Excluded
        isPastInAcademicYear -> Color(0xFF10B981)                              // Green (Past Presence)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)     // Future or pre-academic
    }

    val textColor = when {
        matchingAbsence != null || isPastInAcademicYear || isSelected -> Color.White
        isExcluded -> Color(0xFF475569)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .then(borderModifier)
            .clip(CircleShape)
            .background(cellColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isExcluded) {
            Text(
                text = "$dayNumber",
                style = MaterialTheme.typography.bodySmall.copy(
                    textDecoration = TextDecoration.LineThrough
                ),
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 11.5.sp
            )
        } else {
            Text(
                text = "$dayNumber",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ColorDotLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}

@Composable
fun ExcludedDotLegend(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFFCBD5E1)),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", fontSize = 6.sp, color = Color(0xFF475569), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}

@Composable
fun ExpressiveAbsenceCard(
    record: AbsenceRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(AbsenceTypes.getColorHex(record.absenceType)).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = record.typeEmoji, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.typeDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (record.isRange) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${record.daysCount} notti",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    if (record.isJustified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF2563EB).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Certificato",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Text(
                    text = "${record.formattedDate}${if (record.reason.isNotBlank()) " • ${record.reason}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
