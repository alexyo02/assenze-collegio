package com.example.formeit

import android.content.Context
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

/**
 * Data access to the official FormeIt Residenze backend, reproducing the requests of the
 * official client one by one (same endpoints, payloads and filters).
 *
 * Deliberately NOT reproduced, so the official app keeps working exactly as before:
 *  - `registerdevice` / `disabledevice` (push registration is tied to the official app's
 *    Firebase project and device id; calling them from here would hijack or remove it);
 *  - first-access password setup (done once in the official app).
 */
class FormeitRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val client = FormeitClient()
    private val secure = SecureStore(appContext)
    private val cachePrefs = appContext.getSharedPreferences("formeit_cache", Context.MODE_PRIVATE)
    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)
    private val loginMutex = Mutex()

    private val _session = MutableStateFlow(loadCachedSession())
    /** Last known session (possibly restored from cache while offline). */
    val session: StateFlow<FormeitSession?> = _session.asStateFlow()

    val isConfigured: Boolean get() = client.isConfigured
    fun savedEmail(): String? = secure.email()
    fun hasStoredLogin(): Boolean = secure.hasCredentials()

    // ---------------------------------------------------------------- session

    /** Interactive login; on success credentials are stored (encrypted) for silent refreshes. */
    suspend fun login(email: String, password: String): FormeitSession = loginMutex.withLock {
        val s = performLogin(email.trim(), password)
        secure.saveCredentials(email.trim(), password)
        s
    }

    /** Silent re-login with stored credentials, like the official client does at each start. */
    suspend fun refresh(): FormeitSession = loginMutex.withLock {
        val email = secure.email() ?: throw FormeitException("Accedi con le credenziali FormeIt.")
        val password = secure.password() ?: throw FormeitException("Sessione scaduta: accedi di nuovo.")
        performLogin(email, password)
    }

    fun logout() {
        secure.clearPassword()
        OfficialHistoryStore.get(appContext).clear()
        cachePrefs.edit().clear().apply()
        _session.value = null
    }

    private suspend fun performLogin(email: String, password: String): FormeitSession {
        val loginResp = client.post(
            client.url(FormeitEndpoints.API, "login"),
            mapOf("login" to mapOf("email" to email, "password" to password))
        )
        val login = FormeitClient.requireOk(loginResp, "Login non riuscito")
        val result = login.obj("resultobj") ?: throw FormeitException("Risposta non valida dal server: key non presente")
        if (result.str("status") == "PRIMO ACCESSO") throw FormeitFirstAccessException()
        val key = result.str("key")?.takeIf { it.isNotBlank() } ?: throw FormeitException("KEY non presente nella risposta")
        val createdAt = result.str("created_at")?.takeIf { it.isNotBlank() }
            ?: throw FormeitException("created_at non presente nella risposta")

        val verifica = JObj.asObj(
            client.post(
                client.url(FormeitEndpoints.UTENTI, "verifica"),
                mapOf("UTE_EMAIL" to email, "UTE_KEYAPP" to key)
            )
        )
        val utente = verifica?.obj("utente")
        if (utente == null || (utente.long("UTE_ID") ?: 0L) <= 0L) throw FormeitException("Utente non valido")

        val assResp = JObj.asObj(
            client.post(
                client.url(FormeitEndpoints.UTENTI, "assegnazioni"),
                mapOf("ASS_UTE" to (utente.long("UTE_ID") ?: 0L))
            )
        )
        assResp?.str("error")?.let { throw FormeitException(it) }
        val assegnazioni = assResp?.list("data") ?: emptyList()

        val s = FormeitSession(email, createdAt, utente, assegnazioni, System.currentTimeMillis())
        persistSession(s)
        OfficialHistoryStore.get(appContext).record(s.profile)
        _session.value = s
        return s
    }

    private fun persistSession(s: FormeitSession) {
        cachePrefs.edit()
            .putString("email", s.email)
            .putString("created_at", s.createdAt)
            .putString("utente", anyAdapter.toJson(s.utente.raw))
            .putString("assegnazioni", anyAdapter.toJson(s.assegnazioni.map { it.raw }))
            .putLong("fetched_at", s.fetchedAtMillis)
            .apply()
    }

    private fun loadCachedSession(): FormeitSession? = runCatching {
        val utente = JObj.asObj(anyAdapter.fromJson(cachePrefs.getString("utente", null) ?: return null)) ?: return null
        val ass = JObj.asObjList(anyAdapter.fromJson(cachePrefs.getString("assegnazioni", "[]") ?: "[]"))
        FormeitSession(
            email = cachePrefs.getString("email", "") ?: "",
            createdAt = cachePrefs.getString("created_at", "") ?: "",
            utente = utente,
            assegnazioni = ass,
            fetchedAtMillis = cachePrefs.getLong("fetched_at", 0L)
        )
    }.getOrNull()

    private fun requireSession(): FormeitSession =
        _session.value ?: throw FormeitException("Accedi con le credenziali FormeIt.")

    private fun requireAssigned(): FormeitProfile {
        val p = requireSession().profile
        if (!p.hasAssignment) throw FormeitException("Nessuna assegnazione attiva trovata per questo utente.")
        return p
    }

    // ---------------------------------------------------------------- lists

    /** Bacheca: communications for the user's residence, newer than the account creation date. */
    suspend fun comunicazioni(): List<JObj> {
        val s = requireSession()
        val p = requireAssigned()
        val filtro = "AND (A.RES_ORG = ${p.org}  OR A.RES_ORG = 0) AND (A.RES_ID = ${p.resId} OR (A.RES_ORG = ${p.org} AND A.RES_ID = 0) ) " +
            "AND (A.RES_REP = ${p.rep} OR (A.RES_ID = ${p.resId} AND A.RES_REP = 0) ) AND (A.UTE_ID = 0 OR A.UTE_ID = ${p.uteId} ) " +
            "AND A.data >= '${s.createdAt}'"
        val resp = FormeitClient.requireOk(
            client.get(client.url(FormeitEndpoints.API, "getlistacomunicazioni", mapOf("filtro" to filtro))),
            "Errore nel caricamento della bacheca"
        )
        return resp.list("lista").map { it.with("destinatario" to if ((it.long("ute_id") ?: 0) > 0) "personale" else "tutti") }
    }

    suspend fun documenti(): List<JObj> {
        val p = requireAssigned()
        val filtro = "AND (A.RES_ORG = ${p.org}  OR A.RES_ORG = 0) AND (A.RES_ID = ${p.resId} OR (A.RES_ORG = ${p.org} AND A.RES_ID = 0) ) " +
            "AND (A.RES_REP = ${p.rep} OR (A.RES_ID = ${p.resId} AND A.RES_REP = 0) ) AND (A.UTE_ID = 0 OR A.UTE_ID = ${p.uteId} )"
        val resp = FormeitClient.requireOk(
            client.get(client.url(FormeitEndpoints.API, "getlistadocumenti", mapOf("filtro" to filtro))),
            "Errore nel caricamento dei documenti"
        )
        return resp.list("lista").map { d ->
            val uteId = d.long("ute_id") ?: 0L
            var url = "${FormeitEndpoints.REPOSITORY}documenti/${d.long("res_org") ?: 0}/${d.long("res_id") ?: 0}/${d.long("res_rep") ?: 0}"
            if (uteId > 0) url += "/$uteId"
            url += "/${d.text("nomefile")}"
            d.with("destinatario" to if (uteId > 0) "personale" else "tutti", "url" to url)
        }
    }

    suspend fun tickets(): List<JObj> {
        val p = requireAssigned()
        val filtro = "AND A.RES_ORG = ${p.org} AND A.RES_ID = ${p.resId}  AND A.RES_REP = ${p.rep}  AND A.UTE_ID = ${p.uteId} "
        val resp = FormeitClient.requireOk(
            client.get(
                client.url(FormeitEndpoints.API, "getlistaticket", mapOf("filtro" to filtro, "orderby" to " A.DATA DESC "))
            ),
            "Errore nel caricamento dei ticket"
        )
        return resp.list("lista").map { t ->
            val allegati = t.list("allegati").map { a ->
                val path = "ticket/${t.long("res_org") ?: 0}/${t.long("res_id") ?: 0}/${t.long("res_rep") ?: 0}/" +
                    "${t.long("ute_id") ?: 0}/${a.long("idticket") ?: 0}/${a.text("nome_file")}"
                a.with("path_file" to FormeitEndpoints.REPOSITORY + path)
            }
            t.with("allegati" to allegati.map { it.raw })
        }
    }

    suspend fun priorita(): List<JObj> = lookup("getlistapriorita", "Errore nel caricamento delle priorità")
    suspend fun tipologie(): List<JObj> = lookup("getlistatipologie", "Errore nel caricamento delle tipologie")

    private suspend fun lookup(path: String, err: String): List<JObj> =
        FormeitClient.requireOk(
            client.get(client.url(FormeitEndpoints.API, path, mapOf("orderby" to " A.DESCRIZIONE "))), err
        ).list("lista")

    /**
     * Notifications for this user on Android. The official client also filters by its own
     * device id (assigned at push registration); the beta has no push registration of its own,
     * so it shows the user's Android notifications and folds duplicates across devices.
     */
    suspend fun notifiche(): List<FormeitNotification> {
        val p = requireSession().profile
        val filtro = "\n      AND A.PLATFORM = 'android'\n      AND A.UTE_ID = ${p.uteId}   "
        val resp = FormeitClient.requireOk(
            client.get(client.url(FormeitEndpoints.API, "getlistanotifiche", mapOf("filtro" to filtro))),
            "Errore nel caricamento delle notifiche"
        )
        return resp.list("lista")
            .groupBy { listOf(it.text("title"), it.text("body"), it.text("data")) }
            .values.map { FormeitNotification(it) }
            .sortedByDescending { FormeitDates.parseDateTime(it.dateRaw) ?: 0L }
    }

    suspend fun segnaNotificaLetta(n: FormeitNotification) {
        for (id in n.ids) {
            val resp = client.post(client.url(FormeitEndpoints.API, "notificaletta/$id"), null)
            val ok = (resp as? String)?.trim() == "OK" || JObj.asObj(resp)?.str("retcode") == "OK"
            if (!ok) throw FormeitException("Errore nel segnare la notifica come letta")
        }
    }

    suspend fun residenza(): JObj? {
        val p = requireAssigned()
        val resp = JObj.asObj(client.post(client.url(FormeitEndpoints.UTENTI, "residenze"), mapOf("RES_ID" to (p.resId ?: 0L))))
        resp?.str("error")?.let { throw FormeitException(it) }
        return resp?.list("data")?.firstOrNull()
    }

    // ---------------------------------------------------------------- menu

    /** Latest menu file for the residence (`offset=0&pagelenght=1`, as the official client asks). */
    suspend fun menu(): JObj? {
        val p = requireAssigned()
        val url = FormeitEndpoints.API.toHttpUrl().newBuilder()
            .addPathSegment("getlistamenu")
            .addQueryParameter("filtro", "AND A.RES_ORG = ${p.org} AND A.RES_ID = ${p.resId} ")
            .addQueryParameter("offset", "0")
            .addQueryParameter("pagelenght", "1")
            .build()
        val resp = FormeitClient.requireOk(client.get(url), "Errore nel caricamento del menu")
        return resp.list("lista").map { m ->
            m.with("url" to "${FormeitEndpoints.REPOSITORY}menu/${m.long("res_org") ?: 0}/${m.long("res_id") ?: 0}/${m.long("res_rep") ?: 0}/${m.text("nomefile")}")
        }.firstOrNull()
    }

    suspend fun prenotaMenu(testo: String) {
        val s = requireSession()
        val p = requireAssigned()
        val ticket = linkedMapOf<String, Any?>(
            "id" to 0,
            "testo" to testo,
            "data" to FormeitDates.officialNow(),
            "oggetto" to "Prenotazione menu utente ${s.email}",
            "descpriorita" to p.resEmail3,
            "desctipologia" to "android",
            "idutente" to p.uteId.toString(),
            "lastuser" to p.uteId.toString(),
            "res_org" to p.org,
            "res_id" to p.resId,
            "res_rep" to p.rep,
            "ute_id" to p.uteId,
            "numseg" to p.numseg
        )
        FormeitClient.requireOk(
            client.post(client.url(FormeitEndpoints.API, "prenotamenu"), mapOf("ticket" to ticket)),
            "Errore nell'invio della prenotazione menu"
        )
    }

    // ---------------------------------------------------------------- tickets (write)

    data class NewTicket(
        val oggetto: String,
        val stanza: String,
        val idTipologia: Any?,
        val idPriorita: Any?,
        val codPriorita: Any?,
        val testo: String,
        val allegati: List<FormeitAttachment>
    )

    /**
     * Same three-step flow as the official client: `saveticket` → upload each attachment with
     * the returned ticket id → `saveticketcrm` with the completed ticket.
     */
    suspend fun inviaTicket(t: NewTicket) {
        val p = requireAssigned()
        val form = linkedMapOf<String, Any?>(
            "id" to 0,
            "oggetto" to t.oggetto,
            "stanza" to t.stanza,
            "idtipologia" to t.idTipologia,
            "idpriorita" to t.idPriorita,
            "testo" to t.testo,
            "data" to FormeitDates.officialNow(),
            "idutente" to p.uteId.toString(),
            "lastuser" to p.uteId.toString(),
            "res_org" to p.org,
            "res_id" to p.resId,
            "res_rep" to p.rep,
            "ute_id" to p.uteId,
            "stato" to "",
            "id_mac" to p.idMac,
            "cdunop" to p.cdunop,
            "numseg" to p.numseg,
            "id_rdi" to "",
            "codpriorita" to t.codPriorita
        )
        val saved = FormeitClient.requireOk(
            client.post(client.url(FormeitEndpoints.API, "saveticket"), mapOf("ticket" to form)),
            "Errore nel salvataggio del ticket"
        )
        val ticketId = saved.obj("resultobj")?.obj("ticket")?.raw?.get("id")
            ?: throw FormeitException("Il server non ha restituito l'id del ticket")
        form["id"] = ticketId

        for (a in t.allegati) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", a.name, a.bytes.toRequestBody(a.mimeType.toMediaTypeOrNull()))
                .addFormDataPart("idticket", JObj(mapOf("v" to ticketId)).text("v"))
                .addFormDataPart("nomefile", a.name)
                .addFormDataPart("mimetype", a.mimeType)
                .addFormDataPart("res_org", p.org.toString())
                .addFormDataPart("res_id", p.resId.toString())
                .addFormDataPart("res_rep", p.rep.toString())
                .addFormDataPart("ute_id", p.uteId.toString())
                .build()
            FormeitClient.requireOk(
                client.postMultipart(client.url(FormeitEndpoints.API, "saveallegatoticket"), body),
                "Errore upload allegato ${a.name}"
            )
        }

        FormeitClient.requireOk(
            client.post(client.url(FormeitEndpoints.API, "saveticketcrm"), mapOf("ticket" to form)),
            "Errore nell'inoltro del ticket"
        )
    }

    // ---------------------------------------------------------------- calendar bookings

    suspend fun servizi(): List<JObj> {
        val p = requireAssigned()
        val resp = FormeitClient.requireOk(
            client.get(
                client.url(
                    FormeitEndpoints.CALENDAR, "services",
                    mapOf("org_id" to p.org.toString(), "rep_id" to p.rep.toString(), "res_id" to p.resId.toString())
                )
            ),
            "Errore nel caricamento dei servizi"
        )
        return resp.obj("data")?.list("services") ?: emptyList()
    }

    /** Map `yyyy-MM-dd` → `open|partial|full|closed|past`. */
    suspend fun giorniDisponibili(serviceId: String, yearMonth: String): Map<String, String> {
        val resp = FormeitClient.requireOk(
            client.get(client.url(FormeitEndpoints.CALENDAR, "available-days/$serviceId", mapOf("month" to yearMonth))),
            "Errore nel caricamento delle disponibilità"
        )
        val days = resp.obj("data")?.raw?.get("days") as? Map<*, *> ?: return emptyMap()
        return days.entries.mapNotNull { (k, v) -> (k as? String)?.let { it to v.toString() } }.toMap()
    }

    suspend fun slots(serviceId: String, date: String): List<JObj> {
        val resp = FormeitClient.requireOk(
            client.get(client.url(FormeitEndpoints.CALENDAR, "slots/$serviceId", mapOf("date" to date))),
            "Errore nel caricamento degli orari"
        )
        return (resp.obj("data")?.list("slots") ?: emptyList()).map {
            it.with("start_dt" to it.text("start_dt").replace(" ", "T"), "end_dt" to it.text("end_dt").replace(" ", "T"))
        }
    }

    suspend fun prenota(serviceId: Any?, startDateTime: String, durationMinutes: Any?, note: String) {
        val p = requireSession().profile
        val body = linkedMapOf(
            "provider_service_id" to serviceId,
            "start_datetime" to startDateTime,
            "id_users_customer" to p.uteId,
            "duration" to durationMinutes,
            "notes" to note,
            "users_name" to p.numseg
        )
        FormeitClient.requireOk(client.post(client.url(FormeitEndpoints.CALENDAR, "bookings"), body), "Errore nella prenotazione")
    }

    suspend fun prenotazioni(): List<JObj> {
        val p = requireSession().profile
        val resp = FormeitClient.requireOk(
            client.get(
                client.url(
                    FormeitEndpoints.CALENDAR, "bookings",
                    mapOf("customer_id" to p.uteId.toString(), "from" to "2026-01-01", "to" to "2100-12-31")
                )
            ),
            "Errore nel caricamento delle prenotazioni"
        )
        return (resp.obj("data")?.list("services") ?: emptyList())
            .flatMap { svc ->
                svc.list("bookings").map {
                    it.with(
                        "service_name" to svc.raw["service_name"],
                        "provider_service_id" to svc.raw["provider_service_id"],
                        "service_icon" to svc.raw["service_icon"],
                        "service_color" to svc.raw["service_color"]
                    )
                }
            }
            .sortedByDescending { FormeitDates.parseDateTime(it.text("start_datetime")) ?: 0L }
    }

    suspend fun cancellaPrenotazione(id: String) {
        FormeitClient.requireOk(
            client.put(client.url(FormeitEndpoints.CALENDAR, "bookings/$id/cancel"), null),
            "Errore nella cancellazione della prenotazione"
        )
    }

    // ---------------------------------------------------------------- files

    /** Downloads a repository file into the cache (with the official download token) and returns it. */
    suspend fun scaricaFile(url: String, fileName: String): File {
        val sep = if (url.contains("?")) "&" else "?"
        val full = "$url${sep}token=${java.net.URLEncoder.encode(FormeitEndpoints.DOWNLOAD_TOKEN, "UTF-8")}".toHttpUrl()
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "documento" }
        val target = File(File(appContext.cacheDir, "formeit"), safeName)
        client.download(full, target)
        return target
    }

    companion object {
        @Volatile private var instance: FormeitRepository? = null
        fun get(context: Context): FormeitRepository =
            instance ?: synchronized(this) { instance ?: FormeitRepository(context).also { instance = it } }
    }
}
