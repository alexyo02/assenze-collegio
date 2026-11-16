package com.example.ui.formeit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import com.example.data.AbsenceRecord
import com.example.data.AbsenceTypes
import com.example.util.CivilHolidaysUtil
import com.example.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.formeit.Certainty
import com.example.formeit.FormeitDates
import com.example.formeit.OfficialAbsenceDay

/** One absence night chosen for import, with the nature picked by the user. */
data class ImportChoice(val day: OfficialAbsenceDay, val absenceType: String)

/**
 * Nights the user chose not to import: they are not proposed again on the dashboard
 * (they stay visible, and importable, in "Storico presenze").
 */
object ImportPrefs {
    private const val FILE = "formeit_import_prefs"
    private const val KEY = "ignored_days"
    private val _ignored = MutableStateFlow<Set<Long>?>(null)

    fun ignored(context: Context): StateFlow<Set<Long>?> {
        if (_ignored.value == null) {
            _ignored.value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        }
        return _ignored.asStateFlow()
    }

    fun ignore(context: Context, days: Collection<Long>) {
        if (days.isEmpty()) return
        val updated = ignored(context).value.orEmpty() + days.map { DateUtil.getStartOfDay(it) }
        // Keep only the last ~13 months: older nights can no longer be proposed anyway.
        val cutoff = System.currentTimeMillis() - 400L * 24 * 60 * 60 * 1000
        val pruned = updated.filter { it >= cutoff }.toSet()
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, pruned.map { it.toString() }.toSet()).apply()
        _ignored.value = pruned
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        _ignored.value = emptySet()
    }
}

/**
 * Detected absence nights that are not yet in the local ledger. The official "last absence" is
 * included even if the reconstructed history does not contain it.
 */
fun missingFromLedger(
    detected: List<OfficialAbsenceDay>,
    ledger: List<AbsenceRecord>,
    lastAbsenceMillis: Long? = null,
    ignored: Set<Long> = emptySet()
): List<OfficialAbsenceDay> {
    val all = detected.toMutableList()
    if (lastAbsenceMillis != null && all.none { DateUtil.getStartOfDay(it.dayMillis) == DateUtil.getStartOfDay(lastAbsenceMillis) }) {
        all += OfficialAbsenceDay(lastAbsenceMillis, null, Certainty.EXACT, CivilHolidaysUtil.getExclusionReason(lastAbsenceMillis))
    }
    return all.filter { d ->
        val day = DateUtil.getStartOfDay(d.dayMillis)
        day !in ignored && ledger.none { it.containsDate(day) }
    }.sortedBy { it.dayMillis }
}

private fun shortTypeLabel(type: String) = when (type) {
    AbsenceTypes.NIGHT_OUT -> "Notte fuori"
    AbsenceTypes.HOME_RETURN -> "Casa"
    AbsenceTypes.TRAVEL -> "Viaggio"
    else -> AbsenceTypes.getDisplayName(type)
}

/**
 * Lets the user pick, before importing, which detected nights to add and the nature of each one
 * (notte fuori / casa / viaggio).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportAbsencesDialog(
    days: List<OfficialAbsenceDay>,
    /** Selected nights with their type, and the nights the user unchecked. */
    onConfirm: (selected: List<ImportChoice>, skipped: List<OfficialAbsenceDay>) -> Unit,
    onDismiss: () -> Unit
) {
    val sorted = remember(days) { days.sortedBy { it.dayMillis } }
    val types = remember(days) { mutableStateMapOf<Long, String>().apply { sorted.forEach { put(it.dayMillis, AbsenceTypes.NIGHT_OUT) } } }
    val included = remember(days) { mutableStateMapOf<Long, Boolean>().apply { sorted.forEach { put(it.dayMillis, true) } } }
    val selectedCount = sorted.count { included[it.dayMillis] == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sorted.size == 1) "Aggiungi assenza allo storico" else "Aggiungi assenze allo storico") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Scegli la natura di ogni assenza rilevata da FormeIt. Le notti consecutive dello stesso tipo vengono unite in un unico periodo." +
                        if (sorted.size > 1) " Le notti deselezionate non verranno più proposte." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sorted.size > 1) {
                    Spacer(Modifier.padding(top = 10.dp))
                    Text("Imposta per tutte", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    TypeChips(selected = sorted.map { types[it.dayMillis] }.distinct().singleOrNull()) { t ->
                        sorted.forEach { types[it.dayMillis] = t }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                sorted.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sorted.size > 1) {
                            Checkbox(checked = included[d.dayMillis] == true, onCheckedChange = { included[d.dayMillis] = it })
                        }
                        Column(Modifier.weight(1f)) {
                            Text(FormeitDates.formatDay(d.dayMillis), fontWeight = FontWeight.SemiBold)
                            val detail = listOfNotNull(
                                when {
                                    d.justified == true && d.excludedReason != null -> "non conteggiata (${d.excludedReason})"
                                    d.justified == true -> "giustificata"
                                    d.justified == false -> "non giustificata"
                                    else -> null
                                },
                                if (d.certainty == Certainty.INFERRED) "data dedotta" else null
                            ).joinToString(" · ")
                            if (detail.isNotEmpty()) {
                                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (included[d.dayMillis] == true) {
                        TypeChips(selected = types[d.dayMillis]) { types[d.dayMillis] = it }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        sorted.filter { included[it.dayMillis] == true }
                            .map { ImportChoice(it, types[it.dayMillis] ?: AbsenceTypes.NIGHT_OUT) },
                        sorted.filter { included[it.dayMillis] != true }
                    )
                }
            ) {
                Text(
                    when (selectedCount) {
                        0 -> "Ignora"
                        1 -> "Aggiungi"
                        else -> "Aggiungi $selectedCount"
                    }
                )
            }
        },
        dismissButton = {
            Row {
                if (sorted.size == 1) {
                    TextButton(onClick = { onConfirm(emptyList(), sorted) }) { Text("Ignora") }
                }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeChips(selected: String?, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AbsenceTypes.allTypes.forEach { t ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(t) },
                label = { Text("${AbsenceTypes.getEmoji(t)} ${shortTypeLabel(t)}", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
