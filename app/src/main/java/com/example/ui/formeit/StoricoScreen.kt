package com.example.ui.formeit

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AbsenceRecord
import com.example.formeit.Certainty
import com.example.formeit.FormeitDates
import com.example.formeit.FormeitSyncWorker
import com.example.formeit.FormeitViewModel
import com.example.formeit.OfficialAbsenceDay
import com.example.formeit.OfficialHistoryInference
import com.example.formeit.OfficialMonth
import com.example.ui.AbsenceViewModel
import com.example.util.CivilHolidaysUtil
import com.example.util.DateUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PREFS = "storico_prefs"
private const val KEY_INFO_DISMISSED = "info_dismissed"
private val JustifiedBlue = Color(0xFF2563EB)

/** Per-month summary built from the local ledger, with the official counters when known. */
private data class MonthSummary(
    val key: String,
    val unjustified: Int,
    val justified: Int,
    val notCounted: Int,
    val official: OfficialMonth?
)

/**
 * Presence history: the local ledger summarised by month, the absence nights detected on
 * FormeIt (which offers no history to students) and the latest observed gate transits.
 */
@Composable
internal fun StoricoPage(formeit: FormeitViewModel, absences: AbsenceViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var infoDismissed by remember { mutableStateOf(prefs.getBoolean(KEY_INFO_DISMISSED, false)) }
    var showImport by remember { mutableStateOf(false) }

    val history by formeit.history.collectAsStateWithLifecycle()
    val ledger by absences.allAbsences.collectAsStateWithLifecycle()
    val settings by absences.settings.collectAsStateWithLifecycle()
    val missing = remember(history, ledger) { missingFromLedger(history.days, ledger) }
    val months = remember(ledger, history, settings) {
        buildMonthSummaries(ledger, history.months, settings.academicYearStartDate, settings.academicYearEndDate)
    }

    if (showImport && missing.isNotEmpty()) {
        ImportAbsencesDialog(
            days = missing,
            onConfirm = { selected, skipped ->
                ImportPrefs.ignore(context, skipped.map { it.dayMillis })
                absences.importOfficialAbsences(selected)
                showImport = false
            },
            onDismiss = { showImport = false }
        )
    }

    PageList {
        item {
            if (!infoDismissed) {
                AcCard(container = MaterialTheme.colorScheme.secondaryContainer, elevation = 0) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Come funziona", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "FormeIt mostra solo i contatori del mese corrente, l'ultima assenza e l'ultimo transito. " +
                                    "L'app li controlla ogni ${FormeitSyncWorker.INTERVAL_HOURS} ore e a ogni apertura: quando rileva una nuova assenza " +
                                    "ti propone di aggiungerla allo storico, scegliendo se si tratta di notte fuori, casa o viaggio.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Regolamento art. 11 c.2: le notti nelle festività civili e nei periodi di chiusura (Natale, agosto) non contano; " +
                                    "FormeIt le elenca tra le \"giustificate\". Sabato e domenica invece contano.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            history.trackingSince?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Rilevazione attiva dal ${FormeitDates.formatDateTime(it)}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                infoDismissed = true
                                prefs.edit().putBoolean(KEY_INFO_DISMISSED, true).apply()
                            },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.Close, "Chiudi", modifier = Modifier.size(18.dp)) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        infoDismissed = false
                        prefs.edit().putBoolean(KEY_INFO_DISMISSED, false).apply()
                    }) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Come funziona")
                    }
                }
            }
        }

        item {
            AcCard {
                SectionHeader(
                    "Riepilogo per mese",
                    subtitle = "Dal tuo storico assenze · anno ${settings.academicYear}",
                    icon = Icons.Default.CalendarMonth
                )
                Spacer(Modifier.height(10.dp))
                if (months.isEmpty()) {
                    Text("Nessuna assenza registrata in questo anno accademico.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MonthTableHeader()
                    months.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        MonthRow(m)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), thickness = 2.dp)
                    MonthTotals(months.sumOf { it.unjustified }, months.sumOf { it.justified }, months.sumOf { it.notCounted })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Non giust. = notti che contano per la permanenza · Giust. = con certificato · Escluse = festività e chiusure.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            AcCard {
                SectionHeader(
                    "Notti di assenza rilevate",
                    subtitle = when {
                        history.days.isEmpty() -> null
                        missing.isEmpty() -> "Tutte presenti nello storico"
                        missing.size == 1 -> "1 non ancora nello storico"
                        else -> "${missing.size} non ancora nello storico"
                    },
                    icon = Icons.Default.NightsStay
                )
                Spacer(Modifier.height(8.dp))
                if (history.days.isEmpty()) {
                    Text("Nessuna assenza rilevata finora.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    history.days.forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        AbsenceDayRow(d, inLedger = ledger.any { it.containsDate(d.dayMillis) })
                    }
                }
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (missing.size == 1) "Aggiungi assenza allo storico" else "Aggiungi ${missing.size} assenze allo storico")
                    }
                }
            }
        }

        if (history.transits.isNotEmpty()) {
            item {
                AcCard {
                    SectionHeader(
                        "Ultimi transiti badge",
                        subtitle = "Gli ultimi ${OfficialHistoryInference.MAX_TRANSITS} rilevati; i più vecchi vengono sovrascritti",
                        icon = Icons.Default.DoorFront
                    )
                    Spacer(Modifier.height(8.dp))
                    history.transits.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                t.atMillis?.let { FormeitDates.formatDateTime(it) } ?: t.raw,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(t.state.ifBlank { "?" }, if (t.state.contains("FUORI", true)) StatusAmber else StatusGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthTableHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Mese", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CountHeader("Non giust.")
        CountHeader("Giust.")
        CountHeader("Escluse")
    }
}

@Composable
private fun CountHeader(text: String) {
    Text(
        text, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun CountCell(value: Int, color: Color, bold: Boolean = false) {
    Text(
        value.toString(), modifier = Modifier.width(64.dp),
        color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else color,
        fontWeight = if (bold || value > 0) FontWeight.Bold else FontWeight.Normal,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun MonthRow(m: MonthSummary) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(monthTitle(m.key), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            CountCell(m.unjustified, StatusRed)
            CountCell(m.justified, JustifiedBlue)
            CountCell(m.notCounted, StatusGray)
        }
        m.official?.let { o ->
            Text(
                "FormeIt: ${o.ingiust ?: "—"} non giust. · ${o.giust ?: "—"} giust. (al ${DateUtil.formatShortDate(o.asOf)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthTotals(unjustified: Int, justified: Int, notCounted: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Totale anno", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black)
        CountCell(unjustified, StatusRed, bold = true)
        CountCell(justified, JustifiedBlue, bold = true)
        CountCell(notCounted, StatusGray, bold = true)
    }
}

@Composable
private fun AbsenceDayRow(d: OfficialAbsenceDay, inLedger: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(FormeitDates.formatDay(d.dayMillis), fontWeight = FontWeight.SemiBold)
            val detail = listOfNotNull(
                d.excludedReason,
                if (d.certainty == Certainty.INFERRED) "data dedotta dai contatori" else null,
                if (inLedger) "nello storico" else null
            ).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            d.justified == true && d.excludedReason != null -> StatusPill("Non conteggiata", StatusGray)
            d.justified == true -> StatusPill("Giustificata", JustifiedBlue)
            d.justified == false -> StatusPill("Non giustificata", StatusRed)
            else -> StatusPill("Da verificare", StatusAmber)
        }
    }
}

private fun monthKey(millis: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(millis))

/**
 * Counts each ledger night once (like the residency statistics), grouped by calendar month,
 * within the academic year. Months with official counters but no ledger entries are kept, so
 * a missing manual entry is visible at a glance.
 */
private fun buildMonthSummaries(
    ledger: List<AbsenceRecord>,
    official: List<OfficialMonth>,
    yearStart: Long,
    yearEnd: Long
): List<MonthSummary> {
    val start = DateUtil.getStartOfDay(yearStart)
    val end = DateUtil.getStartOfDay(yearEnd)
    val dayState = sortedMapOf<Long, Int>() // 0 = unjustified, 1 = justified, 2 = not counted
    for (r in ledger) {
        val cal = Calendar.getInstance().apply { timeInMillis = r.dateMillis }
        while (cal.timeInMillis <= r.endDateMillis) {
            val day = DateUtil.getStartOfDay(cal.timeInMillis)
            if (day in start..end) {
                val state = when {
                    CivilHolidaysUtil.isExcludedDay(day) -> 2
                    r.isJustified -> 1
                    else -> 0
                }
                // An unjustified night wins over a justified duplicate, as in the statistics.
                dayState[day] = minOf(dayState[day] ?: state, state)
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val counts = linkedMapOf<String, IntArray>()
    dayState.forEach { (day, state) -> counts.getOrPut(monthKey(day)) { IntArray(3) }[state]++ }

    val officialByKey = official.filter { o ->
        val k = o.key
        k >= monthKey(start) && k <= monthKey(end) && ((o.ingiust ?: 0) + (o.giust ?: 0) > 0)
    }.associateBy { it.key }

    return (counts.keys + officialByKey.keys).distinct().sortedDescending().map { k ->
        val c = counts[k] ?: IntArray(3)
        MonthSummary(k, c[0], c[1], c[2], officialByKey[k])
    }
}

private fun monthTitle(key: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(key)!!
    SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(d).replaceFirstChar { it.uppercase() }
}.getOrDefault(key)
