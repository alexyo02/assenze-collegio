package com.example.ui.formeit

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.formeit.FormeitAttachment
import com.example.formeit.FormeitDates
import com.example.formeit.FormeitRepository
import com.example.formeit.FormeitViewModel
import com.example.formeit.JObj
import com.example.formeit.valueOrNull
import java.io.File

private const val MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TicketListPage(formeit: FormeitViewModel, onNew: () -> Unit) {
    val state by formeit.tickets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { formeit.loadTickets() }
    PageList {
        item {
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Crea nuovo ticket")
            }
        }
        item {
            RemoteContent(state, onRetry = { formeit.loadTickets() }, emptyText = "Nessun ticket aperto.") { list ->
                Column(verticalArrangement = ListSpacing) {
                    list.forEach { t ->
                        var expanded by remember(t) { mutableStateOf(false) }
                        AcCard(modifier = Modifier.animateContentSize().clickable { expanded = !expanded }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(FormeitDates.pretty(t.text("data")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                val stato = t.text("stato")
                                if (stato.isNotBlank()) StatusPill(stato, ticketStateColor(stato))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(t.text("oggetto").ifBlank { "Ticket" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                listOf(t.text("desctipologia"), t.text("descpriorita"), t.text("stanza").let { if (it.isNotBlank()) "Stanza $it" else "" })
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (expanded) {
                                if (t.text("testo").isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(plainText(t.text("testo")), style = MaterialTheme.typography.bodyMedium)
                                }
                                val allegati = t.list("allegati")
                                if (allegati.isNotEmpty()) {
                                    HorizontalDivider(Modifier.height(1.dp).fillMaxWidth())
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        allegati.forEach { a ->
                                            AttachmentChip(a.text("nome_file"), onClick = {
                                                formeit.apriFile(a.text("path_file"), a.text("nome_file")) { openWithViewer(context, it) }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ticketStateColor(stato: String) = when {
    stato.contains("chius", true) || stato.contains("risolt", true) || stato.contains("evas", true) -> StatusGreen
    stato.contains("lavoraz", true) || stato.contains("corso", true) || stato.contains("assegn", true) -> StatusAmber
    stato.contains("aperto", true) || stato.contains("nuovo", true) -> StatusRed
    else -> StatusGray
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NuovoTicketPage(formeit: FormeitViewModel, onSent: () -> Unit) {
    val context = LocalContext.current
    val session by formeit.session.collectAsStateWithLifecycle()
    val prioritaState by formeit.priorita.collectAsStateWithLifecycle()
    val tipologieState by formeit.tipologie.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { formeit.loadTicketLookups() }

    var oggetto by rememberSaveable { mutableStateOf("") }
    var stanza by rememberSaveable { mutableStateOf(session?.profile?.stanza.orEmpty()) }
    var testo by rememberSaveable { mutableStateOf("") }
    var tipologia by remember { mutableStateOf<JObj?>(null) }
    var priorita by remember { mutableStateOf<JObj?>(null) }
    val allegati = remember { mutableStateListOf<FormeitAttachment>() }
    var sending by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            readAttachment(context, uri)?.let { allegati.add(it) } ?: run { localError = "File troppo grande o non leggibile (max 15 MB)." }
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPhoto
        if (ok && f != null && f.exists()) {
            allegati.add(FormeitAttachment(f.name, "image/jpeg", f.readBytes()))
        }
        f?.delete()
        pendingPhoto = null
    }

    val valid = oggetto.isNotBlank() && oggetto.length <= 100 && stanza.isNotBlank() && stanza.length <= 20 &&
        tipologia != null && priorita != null

    PageList {
        item {
            AcCard {
                OutlinedTextField(
                    value = oggetto, onValueChange = { if (it.length <= 100) oggetto = it },
                    label = { Text("Oggetto *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${oggetto.length}/100") }
                )
                OutlinedTextField(
                    value = stanza, onValueChange = { if (it.length <= 20) stanza = it },
                    label = { Text("Stanza *") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LookupDropdown("Tipologia *", tipologieState.valueOrNull.orEmpty(), tipologia) { tipologia = it }
                Spacer(Modifier.height(8.dp))
                LookupDropdown("Priorità *", prioritaState.valueOrNull.orEmpty(), priorita) { priorita = it }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = testo, onValueChange = { testo = it },
                    label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
            }
        }
        item {
            AcCard {
                SectionHeader("Allegati", subtitle = "Foto o documenti utili all'intervento", icon = Icons.Default.AttachFile)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFiles.launch("*/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("File")
                    }
                    OutlinedButton(onClick = {
                        val dir = File(context.cacheDir, "formeit_photos").apply { mkdirs() }
                        val f = File(dir, "foto_${System.currentTimeMillis()}.jpg")
                        pendingPhoto = f
                        takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Foto")
                    }
                }
                if (allegati.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allegati.forEachIndexed { i, a ->
                            AttachmentChip(a.name, onClick = {}) {
                                IconButton(onClick = { allegati.removeAt(i) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, "Rimuovi", Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                localError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            FilledTonalButton(
                enabled = valid && !sending,
                onClick = {
                    sending = true
                    formeit.inviaTicket(
                        FormeitRepository.NewTicket(
                            oggetto = oggetto.trim(),
                            stanza = stanza.trim(),
                            idTipologia = tipologia?.raw?.get("id"),
                            idPriorita = priorita?.raw?.get("id"),
                            codPriorita = priorita?.raw?.get("codpriorita"),
                            testo = testo,
                            allegati = allegati.toList()
                        )
                    ) { ok -> sending = false; if (ok) onSent() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Invia la richiesta") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LookupDropdown(label: String, options: List<JObj>, selected: JObj?, onSelect: (JObj) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.text("descrizione").orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("Caricamento…") }, onClick = {}, enabled = false)
            }
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o.text("descrizione")) }, onClick = { onSelect(o); expanded = false })
            }
        }
    }
}

private fun readAttachment(context: Context, uri: Uri): FormeitAttachment? = runCatching {
    val cr = context.contentResolver
    var name = "allegato"
    var size = -1L
    cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    if (size > MAX_ATTACHMENT_BYTES) return null
    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > MAX_ATTACHMENT_BYTES) return null
    FormeitAttachment(name, cr.getType(uri) ?: "application/octet-stream", bytes)
}.getOrNull()
