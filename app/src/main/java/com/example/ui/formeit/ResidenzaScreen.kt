package com.example.ui.formeit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.formeit.FormeitDates
import com.example.formeit.FormeitViewModel
import com.example.formeit.JObj
import com.example.formeit.valueOrNull

enum class ResidenzaPage(val title: String, val icon: ImageVector) {
    HUB("Residenza", Icons.Default.Apartment),
    STORICO("Storico presenze", Icons.Default.History),
    PROFILO("Profilo e alloggio", Icons.Default.Person),
    BACHECA("Bacheca", Icons.Default.Campaign),
    NOTIFICHE("Notifiche", Icons.Default.Notifications),
    DOCUMENTI("Documenti", Icons.Default.Description),
    TICKET("Ticket manutenzione", Icons.Default.Build),
    NUOVO_TICKET("Nuovo ticket", Icons.Default.Build),
    PRENOTAZIONI("Prenotazioni servizi", Icons.Default.Event),
    NUOVA_PRENOTAZIONE("Nuova prenotazione", Icons.Default.Event),
    MENU("Mensa e menu", Icons.Default.Restaurant),
    INFO("Contatti residenza", Icons.Default.Apartment)
}

/** Tab hosting every FormeIt feature, restyled with the Assenze Collegio design language. */
@Composable
fun ResidenzaScreen(
    formeit: FormeitViewModel,
    absences: com.example.ui.AbsenceViewModel,
    initialPage: ResidenzaPage? = null,
    onInitialPageConsumed: () -> Unit = {}
) {
    val session by formeit.session.collectAsStateWithLifecycle()
    var stack by rememberSaveable { mutableStateOf(listOf(ResidenzaPage.HUB.name)) }
    LaunchedEffect(initialPage) {
        if (initialPage != null) {
            stack = listOf(ResidenzaPage.HUB.name, initialPage.name)
            onInitialPageConsumed()
        }
    }
    val page = ResidenzaPage.valueOf(stack.last())
    fun open(p: ResidenzaPage) { stack = stack + p.name }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { back() }

    if (session == null) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            OfficialStatusCard(formeit, comparison = null, lastAbsenceInLedger = { true }, onAddLastAbsence = {})
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (page != ResidenzaPage.HUB) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
                IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                ResidenzaPage.HUB -> HubPage(formeit, ::open)
                ResidenzaPage.STORICO -> StoricoPage(formeit, absences)
                ResidenzaPage.PROFILO -> ProfiloPage(formeit)
                ResidenzaPage.BACHECA -> BachecaPage(formeit)
                ResidenzaPage.NOTIFICHE -> NotifichePage(formeit)
                ResidenzaPage.DOCUMENTI -> DocumentiPage(formeit)
                ResidenzaPage.TICKET -> TicketListPage(formeit, onNew = { open(ResidenzaPage.NUOVO_TICKET) })
                ResidenzaPage.NUOVO_TICKET -> NuovoTicketPage(formeit, onSent = { back() })
                ResidenzaPage.PRENOTAZIONI -> PrenotazioniPage(formeit, onNew = { open(ResidenzaPage.NUOVA_PRENOTAZIONE) })
                ResidenzaPage.NUOVA_PRENOTAZIONE -> NuovaPrenotazionePage(formeit, onBooked = { back() })
                ResidenzaPage.MENU -> MenuPage(formeit)
                ResidenzaPage.INFO -> InfoResidenzaPage(formeit)
            }
        }
    }
}

@Composable
internal fun PageList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = ListSpacing,
        content = content
    )
}

@Composable
private fun HubPage(formeit: FormeitViewModel, open: (ResidenzaPage) -> Unit) {
    val session by formeit.session.collectAsStateWithLifecycle()
    val notifiche by formeit.notifiche.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { formeit.loadNotifiche() }
    val unread = notifiche.valueOrNull?.count { !it.isRead } ?: 0
    val p = session?.profile ?: return

    PageList {
        item {
            AcCard(container = MaterialTheme.colorScheme.primaryContainer, elevation = 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Text(
                            (p.nome.take(1) + p.cognome.take(1)).uppercase().ifBlank { "?" },
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            listOf(p.stanza, p.letto).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { p.email },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
        val entries = listOf(
            ResidenzaPage.STORICO to "Assenze per mese, notti rilevate, transiti",
            ResidenzaPage.BACHECA to "Comunicazioni della residenza",
            ResidenzaPage.NOTIFICHE to if (unread > 0) "$unread da leggere" else "Avvisi ricevuti",
            ResidenzaPage.DOCUMENTI to "Regolamenti e documenti personali",
            ResidenzaPage.TICKET to "Segnalazioni e richieste",
            ResidenzaPage.PRENOTAZIONI to "Sale, campi, lavanderia…",
            ResidenzaPage.MENU to "Menu del giorno e prenotazione pasti",
            ResidenzaPage.PROFILO to "Anagrafica, alloggio, dati presenza",
            ResidenzaPage.INFO to "Indirizzo, telefono, email"
        )
        items(entries, key = { it.first.name }) { (pg, sub) ->
            HubRow(pg, sub, badge = if (pg == ResidenzaPage.NOTIFICHE && unread > 0) unread else null) { open(pg) }
        }
        item {
            TextButton(onClick = { formeit.logout() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Disconnetti account FormeIt")
            }
        }
    }
}

@Composable
private fun HubRow(page: ResidenzaPage, subtitle: String, badge: Int?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(page.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(page.title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge != null) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary) {
                    Text("$badge", color = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ profilo

@Composable
private fun ProfiloPage(formeit: FormeitViewModel) {
    val session by formeit.session.collectAsStateWithLifecycle()
    val p = session?.profile ?: return
    PageList {
        item {
            AcCard {
                SectionHeader("Anagrafica", icon = Icons.Default.Person)
                Spacer(Modifier.height(12.dp))
                Row { LabelValue("Nome", p.nome, Modifier.weight(1f)); LabelValue("Cognome", p.cognome, Modifier.weight(1f)) }
                Spacer(Modifier.height(10.dp))
                LabelValue("Email", p.email)
            }
        }
        item {
            AcCard {
                SectionHeader("Alloggio", icon = Icons.Default.Apartment)
                Spacer(Modifier.height(12.dp))
                Row { LabelValue("Stanza", p.stanza, Modifier.weight(1f)); LabelValue("Letto", p.letto, Modifier.weight(1f)) }
                Spacer(Modifier.height(10.dp))
                Row { LabelValue("Ala", p.ala, Modifier.weight(1f)); LabelValue("Piano", p.piano, Modifier.weight(1f)) }
                Spacer(Modifier.height(10.dp))
                Row { LabelValue("Tipo", p.tipoAlloggio, Modifier.weight(1f)); LabelValue("Codifica letto", p.codificaLetto, Modifier.weight(1f)) }
                Spacer(Modifier.height(10.dp))
                Row {
                    LabelValue("Arrivo", FormeitDates.pretty(p.dataArrivo, withTime = false), Modifier.weight(1f))
                    LabelValue("Partenza", FormeitDates.pretty(p.dataPartenza, withTime = false), Modifier.weight(1f))
                }
            }
        }
        item {
            AcCard {
                SectionHeader("Dati presenza", icon = Icons.Default.Event)
                Spacer(Modifier.height(12.dp))
                Row {
                    LabelValue("Giustificate (anno)", p.assenzeGiustificateAnno?.toString() ?: "—", Modifier.weight(1f))
                    LabelValue("Non giustificate (anno)", p.assenzeIngiustificateAnno?.toString() ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    LabelValue("Giustificate (mese)", p.assenzeGiustificateMese?.toString() ?: "—", Modifier.weight(1f))
                    LabelValue("Non giustificate (mese)", p.assenzeIngiustificateMese?.toString() ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                LabelValue("Ultima assenza", p.ultimaAssenzaMillis?.let { FormeitDates.formatDay(it) } ?: p.ultimaAssenzaRaw)
                Spacer(Modifier.height(10.dp))
                Row {
                    LabelValue("Ultimo transito", p.ultimoTransito, Modifier.weight(1f))
                    LabelValue("Data transito", p.ultimoTransitoMillis?.let { FormeitDates.formatDateTime(it) } ?: p.ultimoTransitoDataRaw, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                LabelValue("Certificato medico", p.certificatoMedico)
            }
        }
    }
}

// ------------------------------------------------------------------ bacheca

@Composable
private fun BachecaPage(formeit: FormeitViewModel) {
    val state by formeit.comunicazioni.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { formeit.loadComunicazioni() }
    PageList {
        item {
            RemoteContent(state, onRetry = { formeit.loadComunicazioni() }, emptyText = "Nessuna comunicazione.") { list ->
                Column(verticalArrangement = ListSpacing) { list.forEach { ComunicazioneCard(it) } }
            }
        }
    }
}

@Composable
private fun ComunicazioneCard(c: JObj) {
    var expanded by remember { mutableStateOf(false) }
    val body = remember(c) { plainText(c.text("testo")) }
    AcCard(modifier = Modifier.animateContentSize().clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(FormeitDates.pretty(c.text("data")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (c.text("destinatario") == "personale") StatusPill("Personale", MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.height(4.dp))
        Text(c.text("titolo").ifBlank { "Comunicazione" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            body.ifBlank { "Testo non disponibile" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )
        if (!expanded && body.length > 140) {
            Text("Leggi tutto", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ------------------------------------------------------------------ notifiche

@Composable
private fun NotifichePage(formeit: FormeitViewModel) {
    val state by formeit.notifiche.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { formeit.loadNotifiche() }
    PageList {
        item {
            Text(
                "Le notifiche push arrivano solo all'app ufficiale; qui trovi lo storico sincronizzato.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            RemoteContent(state, onRetry = { formeit.loadNotifiche() }, emptyText = "Nessuna notifica.") { list ->
                Column(verticalArrangement = ListSpacing) {
                    list.forEach { n ->
                        var expanded by remember(n) { mutableStateOf(false) }
                        AcCard(modifier = Modifier.animateContentSize().clickable {
                            expanded = !expanded
                            formeit.segnaLetta(n)
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!n.isRead) {
                                    Box(Modifier.size(8.dp).padding(0.dp)) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxSize()) {}
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(FormeitDates.pretty(n.dateRaw), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(n.title.ifBlank { "Notifica" }, fontWeight = if (n.isRead) FontWeight.SemiBold else FontWeight.Bold)
                            Text(
                                plainText(n.body),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ documenti

@Composable
private fun DocumentiPage(formeit: FormeitViewModel) {
    val state by formeit.documenti.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { formeit.loadDocumenti() }
    PageList {
        item {
            RemoteContent(state, onRetry = { formeit.loadDocumenti() }, emptyText = "Nessun documento disponibile.") { list ->
                Column(verticalArrangement = ListSpacing) {
                    list.forEach { d ->
                        AcCard(modifier = Modifier.clickable {
                            formeit.apriFile(d.text("url"), d.text("nomefile")) { openWithViewer(context, it) }
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.text("titolo").ifBlank { d.text("nomefile") }, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOf(d.text("nomefile"), FormeitDates.pretty(d.text("lastupdate"))).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    if (d.text("destinatario") == "personale") "Personale" else "Tutti",
                                    if (d.text("destinatario") == "personale") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ info residenza

@Composable
private fun InfoResidenzaPage(formeit: FormeitViewModel) {
    val state by formeit.residenza.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    LaunchedEffect(Unit) { formeit.loadResidenza() }
    PageList {
        item {
            RemoteContent(state, onRetry = { formeit.loadResidenza() }, emptyText = "Dati residenza non disponibili.") { r ->
                r ?: return@RemoteContent
                AcCard {
                    SectionHeader(r.text("RES_DESCRIZIONE").ifBlank { "Residenza" }, icon = Icons.Default.Apartment)
                    Spacer(Modifier.height(12.dp))
                    LabelValue("Indirizzo", listOf(r.text("RES_INDIRIZZO"), r.text("RES_COMUNE")).filter { it.isNotBlank() }.joinToString(", "))
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    listOf(r.text("RES_TELEFONO1"), r.text("RES_TELEFONO2")).filter { it.isNotBlank() }.forEach { tel ->
                        ContactRow("Telefono", tel) { uri.openUri("tel:${tel.replace(" ", "")}") }
                    }
                    r.text("RES_EMAIL1").takeIf { it.isNotBlank() }?.let { mail -> ContactRow("Email", mail) { uri.openUri("mailto:$mail") } }
                    r.text("RES_URL").takeIf { it.isNotBlank() }?.let { url ->
                        ContactRow("Sito", url) { uri.openUri(if (url.startsWith("http")) url else "https://$url") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LabelValue(label, value, Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AttachmentChip(name: String, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            trailing?.invoke()
        }
    }
}

internal val StatusGreen = Color(0xFF10B981)
internal val StatusAmber = Color(0xFFF59E0B)
internal val StatusRed = Color(0xFFEF4444)
internal val StatusGray = Color(0xFF6B7280)
