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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.formeit.FormeitDates
import com.example.formeit.FormeitViewModel
import com.example.formeit.HybridAbsences
import com.example.formeit.SyncStatus
import com.example.util.DateUtil

/**
 * Dashboard card with the official FormeIt presence data (hybrid mode):
 * official counters, last absence / gate transit, and reconciliation with the local ledger.
 */
@Composable
fun OfficialStatusCard(
    formeit: FormeitViewModel,
    comparison: HybridAbsences.Comparison?,
    lastAbsenceInLedger: (Long) -> Boolean,
    onAddLastAbsence: (Long) -> Unit,
    onOpenHistory: (() -> Unit)? = null
) {
    val monthName = remember {
        java.text.SimpleDateFormat("MMMM", java.util.Locale.ITALIAN).format(java.util.Date()).replaceFirstChar { it.uppercase() }
    }
    val session by formeit.session.collectAsStateWithLifecycle()
    val sync by formeit.sync.collectAsStateWithLifecycle()
    val syncError by formeit.syncError.collectAsStateWithLifecycle()
    var showLogin by remember { mutableStateOf(false) }

    if (showLogin) LoginDialog(formeit, onDismiss = { showLogin = false })

    val s = session
    if (s == null) {
        AcCard(container = MaterialTheme.colorScheme.secondaryContainer, elevation = 0) {
            SectionHeader(
                title = "Dati ufficiali FormeIt",
                subtitle = "Collega l'account della residenza per vedere assenze e transiti ufficiali.",
                icon = Icons.Default.CloudSync
            )
            if (sync == SyncStatus.SYNCING) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            } else {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showLogin = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Accedi con FormeIt")
                }
            }
        }
        return
    }

    val p = s.profile
    AcCard {
        SectionHeader(
            title = "Dati ufficiali FormeIt",
            subtitle = "Aggiornati: " + if (s.fetchedAtMillis > 0) FormeitDates.formatDateTime(s.fetchedAtMillis) else "—",
            icon = when (sync) {
                SyncStatus.OFFLINE, SyncStatus.ERROR -> Icons.Default.CloudOff
                else -> Icons.Default.CloudDone
            }
        ) {
            if (sync == SyncStatus.SYNCING) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { formeit.refresh() }) { Icon(Icons.Default.Refresh, "Aggiorna") }
            }
        }

        if ((sync == SyncStatus.OFFLINE || sync == SyncStatus.ERROR) && syncError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Dati in cache. $syncError",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            if (sync == SyncStatus.ERROR && !formeit.repo.hasStoredLogin()) {
                TextButton(onClick = { showLogin = true }) { Text("Accedi di nuovo") }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (!p.hasPresenceData) {
            Text(
                "Il server non ha restituito contatori di presenza per questo utente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounterTile("Non giustificate", monthName, p.assenzeIngiustificateMese, Color(0xFFEF4444), Modifier.weight(1f))
                CounterTile("Giustificate", monthName, p.assenzeGiustificateMese, Color(0xFF2563EB), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CounterTile("Non giustificate", "anno", p.assenzeIngiustificateAnno, Color(0xFFEF4444), Modifier.weight(1f))
                CounterTile("Giustificate", "anno", p.assenzeGiustificateAnno, Color(0xFF2563EB), Modifier.weight(1f))
            }
            Text(
                "Le notti in festività civili e chiusure risultano \"giustificate\" perché non conteggiate (Regolamento art. 11).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DoorFront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            val transitWhen = p.ultimoTransitoMillis?.let { FormeitDates.formatDateTime(it) } ?: FormeitDates.pretty(p.ultimoTransitoDataRaw)
            Text(
                "Ultimo transito: " + listOf(p.ultimoTransito, transitWhen).filter { it.isNotBlank() && it != "—" }.joinToString(" · ").ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(4.dp))
        val lastAbs = p.ultimaAssenzaMillis
        Text(
            "Ultima assenza: " + (lastAbs?.let { FormeitDates.formatDay(it) } ?: p.ultimaAssenzaRaw.ifBlank { "—" }),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 26.dp)
        )

        if (lastAbs != null && !lastAbsenceInLedger(lastAbs)) {
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { onAddLastAbsence(lastAbs) }, modifier = Modifier.fillMaxWidth()) {
                Text("Aggiungi ${DateUtil.formatShortDate(lastAbs)} al registro locale")
            }
        }

        if (onOpenHistory != null) {
            TextButton(onClick = onOpenHistory, modifier = Modifier.align(Alignment.End)) { Text("Storico presenze →") }
        }

        if (comparison != null && (comparison.monthMismatch || comparison.yearMismatch)) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF59E0B).copy(alpha = 0.12f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Registro e dati ufficiali differiscono", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        if (comparison.monthMismatch) {
                            Text(
                                "Questo mese: registro ${comparison.localUnjustifiedMonth} non giust. / ${comparison.localJustifiedMonth} giust. — " +
                                    "FormeIt ${comparison.officialUnjustifiedMonth ?: "—"} / ${comparison.officialJustifiedMonth ?: "—"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (comparison.yearMismatch) {
                            Text(
                                "Anno: registro ${comparison.localUnjustifiedYear} — FormeIt ${comparison.officialUnjustifiedYear}" +
                                    if (comparison.usesOfficial) " (le statistiche usano il valore ufficiale)" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterTile(label: String, period: String, value: Int?, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.10f)) {
        Column(Modifier.padding(12.dp)) {
            Text(value?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = color)
            Text("$label · $period", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LoginDialog(formeit: FormeitViewModel, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf(formeit.repo.savedEmail().orEmpty()) }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val error by formeit.syncError.collectAsStateWithLifecycle()
    var showError by remember { mutableStateOf(false) }
    val autofill = LocalAutofillManager.current

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Accedi con FormeIt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Usa le stesse credenziali dell'app ufficiale FormeIt Residenze. La password resta cifrata su questo telefono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username + ContentType.EmailAddress }
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("Password / key") }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password }
                )
                if (showError && error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    busy = true; showError = false
                    formeit.login(email, password) { ok ->
                        busy = false
                        if (ok) {
                            // Lets Bitwarden & co. offer to save/update the login.
                            autofill?.commit()
                            onDismiss()
                        } else showError = true
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Accedi")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Annulla") } }
    )
}
