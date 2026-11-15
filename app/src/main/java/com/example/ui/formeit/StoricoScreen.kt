package com.example.ui.formeit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.formeit.Certainty
import com.example.formeit.FormeitDates
import com.example.formeit.FormeitSyncWorker
import com.example.formeit.FormeitViewModel
import com.example.formeit.OfficialAbsenceDay
import com.example.ui.AbsenceViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * History rebuilt from official snapshots (FormeIt offers no history to students).
 */
@Composable
internal fun StoricoPage(formeit: FormeitViewModel, absences: AbsenceViewModel) {
    val history by formeit.history.collectAsStateWithLifecycle()
    val ledger by absences.allAbsences.collectAsStateWithLifecycle()
    val missing = history.days.filter { d -> ledger.none { it.containsDate(d.dayMillis) } }

    PageList {
        item {
            AcCard(container = MaterialTheme.colorScheme.secondaryContainer, elevation = 0) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "FormeIt mostra solo i contatori del mese e dell'anno, l'ultima assenza e l'ultimo transito. " +
                            "Questa pagina ricostruisce lo storico registrando quei valori ogni ${FormeitSyncWorker.INTERVAL_HOURS} ore e a ogni apertura." +
                            (history.trackingSince?.let { "\nRaccolta attiva dal ${FormeitDates.formatDateTime(it)}." } ?: "") +
                            "\n\nRegolamento art. 11 c.2: le notti nelle festività civili e nei periodi di chiusura (Natale, agosto) non contano: " +
                            "FormeIt le elenca tra le \"giustificate\". Sabato e domenica invece contano.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (history.months.isNotEmpty() || history.earlierThisYearIngiust != null) {
            item {
                AcCard {
                    SectionHeader("Riepilogo ufficiale per mese", icon = Icons.Default.History)
                    Spacer(Modifier.height(10.dp))
                    history.months.forEach { m ->
                        MonthRow(monthTitle(m.key), m.ingiust, m.giust)
                    }
                    if ((history.earlierThisYearIngiust ?: 0) + (history.earlierThisYearGiust ?: 0) > 0) {
                        MonthRow("Mesi precedenti (anno)", history.earlierThisYearIngiust, history.earlierThisYearGiust)
                        Text(
                            "Date non disponibili: avvenute prima dell'inizio della raccolta.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            AcCard {
                SectionHeader(
                    "Notti di assenza rilevate",
                    subtitle = if (missing.isEmpty()) "Tutte presenti nel registro locale" else "${missing.size} non presenti nel registro locale",
                    icon = Icons.Default.NightsStay
                )
                if (history.days.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Nessuna assenza rilevata finora.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Spacer(Modifier.height(8.dp))
                    history.days.forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        AbsenceDayRow(d, inLedger = ledger.any { it.containsDate(d.dayMillis) })
                    }
                }
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { absences.importOfficialDays(missing) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Aggiungi ${missing.size} notti al registro locale")
                    }
                }
            }
        }

        if (history.transits.isNotEmpty()) {
            item {
                AcCard {
                    SectionHeader("Transiti badge osservati", subtitle = "Solo l'ultimo transito di ogni rilevazione", icon = Icons.Default.DoorFront)
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(history.transits.take(60), key = { it.state + it.raw }) { t ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(t.state.ifBlank { "?" }, if (t.state.contains("FUORI", true)) StatusAmber else StatusGreen)
                    Spacer(Modifier.width(10.dp))
                    Text(t.atMillis?.let { FormeitDates.formatDateTime(it) } ?: t.raw, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MonthRow(title: String, ingiust: Int?, giust: Int?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        StatusPill("${ingiust ?: "—"} non giust.", StatusRed)
        Spacer(Modifier.width(6.dp))
        StatusPill("${giust ?: "—"} giust.", androidx.compose.ui.graphics.Color(0xFF2563EB))
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
                if (inLedger) "nel registro" else null
            ).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            d.justified == true && d.excludedReason != null -> StatusPill("Non conteggiata", StatusGray)
            d.justified == true -> StatusPill("Giustificata", androidx.compose.ui.graphics.Color(0xFF2563EB))
            d.justified == false -> StatusPill("Non giustificata", StatusRed)
            else -> StatusPill("Da verificare", StatusAmber)
        }
    }
}

private fun monthTitle(key: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(key)!!
    SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(d).replaceFirstChar { it.uppercase() }
}.getOrDefault(key)
