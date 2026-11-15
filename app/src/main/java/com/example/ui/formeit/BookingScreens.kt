package com.example.ui.formeit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.formeit.FormeitDates
import com.example.formeit.FormeitViewModel
import com.example.formeit.JObj
import com.example.formeit.valueOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ prenotazioni (list)

@Composable
internal fun PrenotazioniPage(formeit: FormeitViewModel, onNew: () -> Unit) {
    val state by formeit.prenotazioni.collectAsStateWithLifecycle()
    var toCancel by remember { mutableStateOf<JObj?>(null) }
    LaunchedEffect(Unit) { formeit.loadPrenotazioni() }

    toCancel?.let { b ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text("Cancellare la prenotazione?") },
            text = { Text("${b.text("service_name")} · ${FormeitDates.pretty(b.text("start_datetime"))}") },
            confirmButton = {
                TextButton(onClick = { formeit.cancellaPrenotazione(b.text("id")); toCancel = null }) {
                    Text("Cancella", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text("Annulla") } }
        )
    }

    PageList {
        item {
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Crea nuova prenotazione")
            }
        }
        item {
            RemoteContent(state, onRetry = { formeit.loadPrenotazioni() }, emptyText = "Nessuna prenotazione.") { list ->
                Column(verticalArrangement = ListSpacing) {
                    val now = System.currentTimeMillis()
                    list.forEach { b ->
                        val start = FormeitDates.parseDateTime(b.text("start_datetime"))
                        val status = b.text("assignment_status")
                        val cancelled = status.contains("cancel", true) || status.contains("annull", true)
                        val accent = parseColor(b.text("service_color")) ?: MaterialTheme.colorScheme.primary
                        AcCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(b.text("service_name").ifBlank { "Servizio" }, fontWeight = FontWeight.Bold)
                                    Text(
                                        FormeitDates.pretty(b.text("start_datetime")) +
                                            b.text("end_datetime").takeIf { it.isNotBlank() }?.let { " → " + (FormeitDates.parseDateTime(it)?.let { ms -> SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(ms)) } ?: it) }.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (status.isNotBlank()) StatusPill(status, if (cancelled) StatusGray else StatusGreen)
                            }
                            if (!cancelled && start != null && start > now) {
                                TextButton(onClick = { toCancel = b }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Cancella", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ nuova prenotazione

private val dayStatusColors = mapOf(
    "open" to StatusGreen,
    "partial" to StatusAmber,
    "full" to StatusRed,
    "closed" to StatusRed,
    "past" to Color(0xFFCCCCCC)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NuovaPrenotazionePage(formeit: FormeitViewModel, onBooked: () -> Unit) {
    val serviziState by formeit.servizi.collectAsStateWithLifecycle()
    val session by formeit.session.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { formeit.loadServizi() }

    var servizio by remember { mutableStateOf<JObj?>(null) }
    var month by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    var days by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var daysLoading by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<String?>(null) }
    var slots by remember { mutableStateOf<List<JObj>>(emptyList()) }
    var slotsLoading by remember { mutableStateOf(false) }
    var slot by remember { mutableStateOf<JObj?>(null) }
    var duration by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var booking by remember { mutableStateOf(false) }

    val ymFmt = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val yearMonth = ymFmt.format(month.time)

    val certMissing = servizio?.bool("service_certRQ") == true && session?.profile?.certificatoMedico != "SI"

    fun loadDays() {
        val s = servizio ?: return
        scope.launch {
            daysLoading = true; error = null
            days = try { formeit.repo.giorniDisponibili(s.text("id"), yearMonth) } catch (e: Exception) { error = e.message; emptyMap() }
            daysLoading = false
        }
    }

    fun loadSlots(day: String) {
        val s = servizio ?: return
        scope.launch {
            slotsLoading = true; error = null; slot = null
            slots = try { formeit.repo.slots(s.text("id"), day) } catch (e: Exception) { error = e.message; emptyList() }
            slotsLoading = false
        }
    }

    LaunchedEffect(servizio, yearMonth) {
        selectedDay = null; slots = emptyList(); slot = null
        if (servizio != null) loadDays()
    }

    PageList {
        item {
            AcCard {
                ServiceDropdown(serviziState.valueOrNull.orEmpty(), servizio, loading = serviziState !is com.example.formeit.Remote.Ready) { servizio = it }
                servizio?.text("service_note")?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(plainText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (certMissing) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Questo servizio richiede il certificato medico, non risulta caricato sul tuo profilo.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (servizio != null && !certMissing) {
            item {
                AcCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(month.time).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
                        )
                        if (daysLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        IconButton(onClick = { month = (month.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }) { Icon(Icons.Default.ChevronLeft, "Mese precedente") }
                        IconButton(onClick = { month = (month.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }) { Icon(Icons.Default.ChevronRight, "Mese successivo") }
                    }
                    Spacer(Modifier.height(6.dp))
                    MonthAvailabilityGrid(month, days, selectedDay) { day ->
                        val st = days[day]
                        if (st == null || st == "closed" || st == "past" || st == "full") return@MonthAvailabilityGrid
                        selectedDay = day
                        loadSlots(day)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Legend(StatusGreen, "Libero"); Legend(StatusAmber, "Parziale"); Legend(StatusRed, "Pieno/chiuso")
                    }
                }
            }
        }
        if (selectedDay != null) {
            item {
                AcCard {
                    SectionHeader("Orari del ${FormeitDates.pretty(selectedDay, withTime = false)}", icon = Icons.Default.Event)
                    Spacer(Modifier.height(10.dp))
                    when {
                        slotsLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        slots.isEmpty() -> Text("Nessuno slot disponibile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slots.forEach { sl ->
                                val enabled = isSlotBookable(servizio, selectedDay!!, sl)
                                val selected = slot === sl
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        enabled -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.clickable(enabled = enabled) {
                                        slot = sl
                                        duration = slotMinutes(sl)
                                    }
                                ) {
                                    Text(
                                        slotLabel(sl),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val sl = slot
        if (sl != null) {
            item {
                AcCard {
                    val custom = servizio?.bool("allow_custom_duration") == true
                    val step = slotMinutes(sl).coerceAtLeast(1)
                    val maxDur = servizio?.int("max_duration")?.takeIf { it >= step } ?: step
                    if (custom) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Durata", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { duration = (duration - step).coerceAtLeast(step) }) { Icon(Icons.Default.Remove, "Meno") }
                            Text("$duration min", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { duration = (duration + step).coerceAtMost(maxDur) }) { Icon(Icons.Default.Add, "Più") }
                        }
                        Text("Ora fine: ${endTime(sl, duration)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = note, onValueChange = { note = it }, label = { Text("Note (facoltative)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        enabled = !booking,
                        onClick = {
                            booking = true
                            formeit.prenota(
                                serviceId = servizio?.raw?.get("id"),
                                start = sl.text("start_dt"),
                                duration = if (custom) duration else sl.raw["max_duration"],
                                note = note
                            ) { ok -> booking = false; if (ok) onBooked() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (booking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Prenota ${slotLabel(sl)}") }
                    }
                }
            }
        }
        error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceDropdown(options: List<JObj>, selected: JObj?, loading: Boolean, onSelect: (JObj) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.text("service_name").orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(if (loading) "Caricamento servizi…" else "Servizio") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.text("service_name")) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(parseColor(o.text("service_color")) ?: MaterialTheme.colorScheme.primary)) },
                    onClick = { onSelect(o); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun MonthAvailabilityGrid(month: Calendar, days: Map<String, String>, selected: String?, onDay: (String) -> Unit) {
    val dayFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val cells = remember(month.timeInMillis) {
        val c = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val lead = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday-first
        val count = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        List(lead) { null } + (1..count).map { d -> (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) } }
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom").forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cal ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (cal != null) {
                            val key = dayFmt.format(cal.time)
                            val status = days[key]
                            val color = status?.let { dayStatusColors[it] }
                            val isSel = key == selected
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (color != null) color.copy(alpha = if (status == "past") 0.35f else 0.85f) else Color.Transparent)
                                    .then(if (isSel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable { onDay(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${cal.get(Calendar.DAY_OF_MONTH)}",
                                    color = if (color != null && status != "past") Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun slotMinutes(sl: JObj): Int {
    val s = FormeitDates.parseDateTime(sl.text("start_dt")) ?: return sl.int("max_duration") ?: 0
    val e = FormeitDates.parseDateTime(sl.text("end_dt")) ?: return sl.int("max_duration") ?: 0
    return ((e - s) / 60_000L).toInt()
}

private fun hhmm(raw: String): String = FormeitDates.parseDateTime(raw)?.let { SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(it)) } ?: raw

private fun slotLabel(sl: JObj): String =
    (sl.text("start").ifBlank { hhmm(sl.text("start_dt")) }) + "–" + (sl.text("end").ifBlank { hhmm(sl.text("end_dt")) })

private fun endTime(sl: JObj, minutes: Int): String {
    val s = FormeitDates.parseDateTime(sl.text("start_dt")) ?: return "—"
    return SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(s + minutes * 60_000L))
}

/** Same rule as the official client: same-day slots close `book_advance_timeout` minutes before start. */
private fun isSlotBookable(servizio: JObj?, day: String, sl: JObj): Boolean {
    val timeout = servizio?.int("book_advance_timeout") ?: return true
    if (timeout <= 0) return true
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    if (day != today) return true
    val start = FormeitDates.parseDateTime(sl.text("start_dt")) ?: return true
    return System.currentTimeMillis() <= start - timeout * 60_000L
}

private fun parseColor(raw: String): Color? = runCatching {
    if (raw.isBlank()) null else Color(android.graphics.Color.parseColor(if (raw.startsWith("#")) raw else "#$raw"))
}.getOrNull()

// ------------------------------------------------------------------ menu

@Composable
internal fun MenuPage(formeit: FormeitViewModel) {
    val state by formeit.menu.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var testo by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { formeit.loadMenu() }

    PageList {
        item {
            AcCard(container = MaterialTheme.colorScheme.primaryContainer, elevation = 0) {
                SectionHeader("Menu del giorno", icon = Icons.Default.MenuBook)
                Spacer(Modifier.height(10.dp))
                when (val s = state) {
                    is com.example.formeit.Remote.Ready -> {
                        val m = s.value
                        if (m == null || m.text("nomefile").isBlank()) {
                            Text("Il menu al momento non è disponibile.")
                        } else {
                            Text("Ultimo aggiornamento: ${FormeitDates.pretty(m.text("lastupdate"))}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { formeit.apriFile(m.text("url"), m.text("nomefile")) { openWithViewer(context, it) } }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Restaurant, null); Spacer(Modifier.width(8.dp)); Text("Apri il menu")
                            }
                        }
                    }
                    is com.example.formeit.Remote.Failed -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { formeit.loadMenu() }) { Text("Riprova") }
                    }
                    else -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
        item {
            AcCard {
                SectionHeader("Prenota pasto", subtitle = "La richiesta arriva alla mensa della residenza", icon = Icons.Default.Restaurant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = testo, onValueChange = { testo = it },
                    label = { Text("Dettaglio della prenotazione *") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp)
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    enabled = testo.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        formeit.prenotaMenu(testo.trim()) { ok -> sending = false; if (ok) testo = "" }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Invia la prenotazione") }
                }
            }
        }
    }
}
