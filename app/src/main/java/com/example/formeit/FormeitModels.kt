package com.example.formeit

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Logged-in state as built by the official client's post-login flow:
 * `login` → key, `utenti/verifica` → user record, `utenti/assegnazioni` → room assignments.
 */
data class FormeitSession(
    val email: String,
    val createdAt: String,
    val utente: JObj,
    val assegnazioni: List<JObj>,
    val fetchedAtMillis: Long
) {
    val profile: FormeitProfile get() = FormeitProfile(utente, assegnazioni.firstOrNull(), email)
}

/** Typed accessors over the user record and its first assignment (the official client always uses index 0). */
class FormeitProfile(val utente: JObj, val assegnazione: JObj?, private val loginEmail: String) {
    val uteId: Long get() = utente.long("UTE_ID") ?: 0L
    val nome: String get() = utente.text("UTE_NOME")
    val cognome: String get() = utente.text("UTE_COGNOME")
    val fullName: String get() = "$cognome $nome".trim()
    val displayName: String get() = "$nome $cognome".trim().ifBlank { email }
    val email: String get() = utente.text("UTE_EMAIL").ifBlank { loginEmail }

    // Official client fields copied into configurationData after `verifica`.
    val resEmail3: String get() = utente.text("RES_EMAIL3")
    val stanza: String get() = utente.text("LOC_DESC")
    val idMac: String get() = utente.text("LOC_ZM")
    val cdunop: String get() = utente.text("CDUNOP")
    val certificatoMedico: String get() = utente.text("UTE_CERTIFICATO")
    val numseg: String get() = fullName

    val letto: String get() = utente.text("LET_NOME")

    // Assignment (listaAssegnazioni[0]).
    val org: Long? get() = assegnazione?.long("ASS_ORG")
    val resId: Long? get() = assegnazione?.long("RES_ID")
    val rep: Long? get() = assegnazione?.long("ASS_REP")
    val ala: String get() = assegnazione?.text("LOC_ALA").orEmpty()
    val piano: String get() = assegnazione?.text("LOC_PIANO").orEmpty()
    val tipoAlloggio: String get() = assegnazione?.text("LOC_TIPO").orEmpty()
    val codificaLetto: String get() = assegnazione?.text("LET_CODIFICA").orEmpty()
    val dataArrivo: String get() = (assegnazione?.text("ASS_PIAN_DATA_ARRIVO") ?: "").ifBlank { utente.text("ASS_PIAN_DATA_ARRIVO") }
    val dataPartenza: String get() = (assegnazione?.text("ASS_PIAN_DATA_PARTENZA") ?: "").ifBlank { utente.text("ASS_PIAN_DATA_PARTENZA") }

    val hasAssignment: Boolean get() = org != null && resId != null && rep != null

    // Presence data shown by the official "Dati presenza" page.
    val assenzeGiustificateAnno: Int? get() = utente.int("AssenzeGiustificateAnno")
    val assenzeIngiustificateAnno: Int? get() = utente.int("AssenzeIngiustificateAnno")
    val assenzeGiustificateMese: Int? get() = utente.int("AssenzeGiustificateMese")
    val assenzeIngiustificateMese: Int? get() = utente.int("AssenzeIngiustificateMese")
    val ultimaAssenzaRaw: String get() = utente.text("UltimaAssenza")
    val ultimoTransito: String get() = utente.text("UTE_TRANSITO")
    val ultimoTransitoDataRaw: String get() = utente.text("UTE_TRANSITO_DATA")

    val ultimaAssenzaMillis: Long? get() = FormeitDates.parseDay(ultimaAssenzaRaw)
    val ultimoTransitoMillis: Long? get() = FormeitDates.parseDateTime(ultimoTransitoDataRaw)

    val hasPresenceData: Boolean
        get() = assenzeGiustificateAnno != null || assenzeIngiustificateAnno != null ||
            assenzeGiustificateMese != null || assenzeIngiustificateMese != null
}

/** Date parsing tolerant to the formats seen in the official client (ISO, SQL timestamp, dd/MM/yyyy). */
object FormeitDates {
    private val dateTimePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss.S", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy"
    )

    fun parseDateTime(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        for (p in dateTimePatterns) {
            val fmt = SimpleDateFormat(p, Locale.ITALY).apply { isLenient = false }
            val pos = java.text.ParsePosition(0)
            val d = fmt.parse(s, pos)
            if (d != null && pos.index == s.length) return d.time
        }
        return null
    }

    private val DATE_ONLY = Regex("^(\\d{4}-\\d{2}-\\d{2})(?:[T ]00:00(?::00(?:\\.0+)?)?(?:Z|[+-]00:?00)?)?$")

    /** Start of the local day of a date/datetime string. */
    fun parseDay(raw: String?): Long? {
        // Date fields (e.g. UltimaAssenza) arrive as `yyyy-MM-ddT00:00:00.000Z`: the calendar day is the
        // date part itself, never shift it through a timezone conversion.
        val m = DATE_ONLY.find(raw?.trim().orEmpty())
        if (m != null) return parseDateTime(m.groupValues[1])?.let { startOfDay(it) }
        return parseDateTime(raw)?.let { startOfDay(it) }
    }

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun formatDay(millis: Long): String = SimpleDateFormat("EEE d MMM yyyy", Locale.ITALIAN).format(Date(millis))
        .replaceFirstChar { it.uppercase() }

    fun formatDateTime(millis: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(Date(millis))

    /** Pretty-print a server date string; falls back to the raw text. */
    fun pretty(raw: String?, withTime: Boolean = true): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return "—"
        if (DATE_ONLY.matches(s)) {
            return parseDay(s)?.let { SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN).format(Date(it)) } ?: s
        }
        val millis = parseDateTime(s) ?: return s
        val hasTime = Regex("\\d{1,2}:\\d{2}").containsMatchIn(s)
        return if (withTime && hasTime) formatDateTime(millis) else SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN).format(Date(millis))
    }

    /** `dd/MM/yyyy HH:mm:ss`, the format the official client puts in the `data` field of tickets and menu requests. */
    fun officialNow(): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY).format(Date())
}

data class FormeitAttachment(val name: String, val mimeType: String, val bytes: ByteArray)

/** A notification may be stored once per registered device; group copies so each appears once. */
data class FormeitNotification(val rows: List<JObj>) {
    private val first get() = rows.first()
    val ids: List<String> get() = rows.mapNotNull { it.str("idnotifica") }
    val title: String get() = first.text("title")
    val body: String get() = first.text("body")
    val dateRaw: String get() = first.text("data")
    val isRead: Boolean get() = rows.all { it.str("letto") == "S" }
}
