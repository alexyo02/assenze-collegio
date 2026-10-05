package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ResidencyRiskLevel
import com.example.util.DateUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: AbsenceViewModel,
    modifier: Modifier = Modifier
) {
    val allAbsences by viewModel.allAbsences.collectAsStateWithLifecycle()
    val residencyStats by viewModel.residencyStats.collectAsStateWithLifecycle()
    val monthlyWindowOffset by viewModel.monthlyWindowOffset.collectAsStateWithLifecycle()

    val riskColor = when (residencyStats.riskLevel) {
        ResidencyRiskLevel.SAFE -> Color(0xFF10B981)
        ResidencyRiskLevel.WARNING -> Color(0xFFF59E0B)
        ResidencyRiskLevel.CRITICAL -> Color(0xFFEF4444)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ESSENTIAL COMPLIANCE CARD (> 56%)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "PERMANENZA ANNUALE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Regola del 56% sui giorni utili",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Presenza Stimata",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", residencyStats.projectedAnnualPresencePercentage),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = riskColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = when (residencyStats.riskLevel) {
                                    ResidencyRiskLevel.SAFE -> "Regolare (> 56%)"
                                    ResidencyRiskLevel.WARNING -> "Attenzione"
                                    ResidencyRiskLevel.CRITICAL -> "Limite Superato"
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = riskColor,
                                style = MaterialTheme.typography.labelMedium,
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
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CleanMetric(
                            title = "Giorni Utili",
                            value = "${residencyStats.totalUsefulDaysInYear}"
                        )
                        CleanMetric(
                            title = "Assenze Fatte",
                            value = "${residencyStats.totalAbsencesCount} gg"
                        )
                        CleanMetric(
                            title = "Giorni Rimanenti",
                            value = "${residencyStats.remainingAbsencesBefore56} gg",
                            highlight = residencyStats.remainingAbsencesBefore56 <= 15
                        )
                    }
                }
            }
        }

        // MONTHLY FREQUENCY CHART WITH NAVIGATION ARROWS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
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
                        Text(
                            text = "Frequenza Assenze Mensili",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.previousMonthlyWindow() }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Mesi precedenti")
                            }
                            IconButton(onClick = { viewModel.nextMonthlyWindow() }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Mesi successivi")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val monthsWindow = remember(allAbsences, monthlyWindowOffset) {
                        val list = mutableListOf<Pair<String, Int>>()
                        val sdf = SimpleDateFormat("MMM", Locale.ITALIAN)

                        for (i in 5 downTo 0) {
                            val checkCal = Calendar.getInstance().apply {
                                add(Calendar.MONTH, -i + (monthlyWindowOffset * 6))
                            }
                            val month = checkCal.get(Calendar.MONTH)
                            val year = checkCal.get(Calendar.YEAR)
                            val label = sdf.format(checkCal.time).replaceFirstChar { it.uppercase(Locale.ITALIAN) }

                            var countInMonth = 0
                            for (record in allAbsences) {
                                val dayCal = Calendar.getInstance().apply { timeInMillis = record.dateMillis }
                                while (dayCal.timeInMillis <= record.endDateMillis) {
                                    if (dayCal.get(Calendar.MONTH) == month && dayCal.get(Calendar.YEAR) == year) {
                                        countInMonth++
                                    }
                                    dayCal.add(Calendar.DAY_OF_MONTH, 1)
                                }
                            }

                            list.add(label to countInMonth)
                        }
                        list
                    }

                    val maxAbsences = remember(monthsWindow) {
                        monthsWindow.maxOfOrNull { it.second }?.coerceAtLeast(5) ?: 5
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        monthsWindow.forEach { (label, count) ->
                            val heightFraction = (count.toFloat() / maxAbsences.toFloat()).coerceIn(0.08f, 1f)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (count > 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .fillMaxWidth(0.5f)
                                        .height((80 * heightFraction).dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // CALENDAR PARAMETERS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Riepilogo Calendario",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Periodo utile: dal ${DateUtil.formatShortDate(residencyStats.academicYearStart)} al ${DateUtil.formatShortDate(residencyStats.academicYearEnd)}\n" +
                                "• Esclusi dal calcolo: 31 giorni di Agosto, 15 giorni di Natale e tutte le festività civili nazionali\n" +
                                "• Giorni minimi di presenza richiesti: ${residencyStats.minRequiredPresenceDays} giorni",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CleanMetric(
    title: String,
    value: String,
    highlight: Boolean = false
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
        )
    }
}
