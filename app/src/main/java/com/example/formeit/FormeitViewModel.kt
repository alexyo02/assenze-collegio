package com.example.formeit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.widget.CollegeWidgetCompactProvider
import com.example.widget.CollegeWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Load state of a remote list/section. */
sealed interface Remote<out T> {
    data object Idle : Remote<Nothing>
    data object Loading : Remote<Nothing>
    data class Ready<T>(val value: T) : Remote<T>
    data class Failed(val message: String) : Remote<Nothing>
}

val <T> Remote<T>.valueOrNull: T? get() = (this as? Remote.Ready<T>)?.value

enum class SyncStatus { IDLE, SYNCING, OK, OFFLINE, ERROR, LOGGED_OUT }

class FormeitViewModel(application: Application) : AndroidViewModel(application) {

    val repo = FormeitRepository.get(application)
    val session: StateFlow<FormeitSession?> = repo.session

    private val _sync = MutableStateFlow(if (repo.hasStoredLogin()) SyncStatus.IDLE else SyncStatus.LOGGED_OUT)
    val sync: StateFlow<SyncStatus> = _sync.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    val comunicazioni = MutableStateFlow<Remote<List<JObj>>>(Remote.Idle)
    val notifiche = MutableStateFlow<Remote<List<FormeitNotification>>>(Remote.Idle)
    val documenti = MutableStateFlow<Remote<List<JObj>>>(Remote.Idle)
    val tickets = MutableStateFlow<Remote<List<JObj>>>(Remote.Idle)
    val prenotazioni = MutableStateFlow<Remote<List<JObj>>>(Remote.Idle)
    val servizi = MutableStateFlow<Remote<List<JObj>>>(Remote.Idle)
    val menu = MutableStateFlow<Remote<JObj?>>(Remote.Idle)
    val residenza = MutableStateFlow<Remote<JObj?>>(Remote.Idle)
    val priorita = MutableStateFlow<Remote<List<JObj>>>(Remote.Idle)
    val tipologie = MutableStateFlow<Remote<List<JObj>>>(Remote.Idle)

    private var refreshJob: Job? = null

    /** History rebuilt from official snapshots. */
    val history: StateFlow<OfficialHistoryView> = OfficialHistoryStore.get(application).view

    init {
        if (repo.hasStoredLogin()) FormeitSyncWorker.schedule(application)
        refreshIfStale()
    }

    // ---------------------------------------------------------------- session

    /** Silent re-login when the cached data is older than [maxAgeMs] (called at start/resume). */
    fun refreshIfStale(maxAgeMs: Long = 5 * 60_000L) {
        if (!repo.hasStoredLogin()) { _sync.value = SyncStatus.LOGGED_OUT; return }
        val age = System.currentTimeMillis() - (session.value?.fetchedAtMillis ?: 0L)
        if (age > maxAgeMs) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _sync.value = SyncStatus.SYNCING
            try {
                repo.refresh()
                _sync.value = SyncStatus.OK
                _syncError.value = null
                updateWidgets()
            } catch (e: FormeitException) {
                _sync.value = if (e.isNetwork) SyncStatus.OFFLINE else SyncStatus.ERROR
                _syncError.value = e.message
            } catch (e: Exception) {
                _sync.value = SyncStatus.ERROR
                _syncError.value = e.message ?: "Errore sconosciuto"
            }
        }
    }

    fun login(email: String, password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _sync.value = SyncStatus.SYNCING
            try {
                repo.login(email, password)
                FormeitSyncWorker.schedule(getApplication())
                _sync.value = SyncStatus.OK
                _syncError.value = null
                _message.value = "Collegato a FormeIt."
                updateWidgets()
                onDone(true)
            } catch (e: Exception) {
                _sync.value = SyncStatus.LOGGED_OUT
                _syncError.value = e.message ?: "Login non riuscito"
                onDone(false)
            }
        }
    }

    fun logout() {
        FormeitSyncWorker.cancel(getApplication())
        repo.logout()
        comunicazioni.value = Remote.Idle
        notifiche.value = Remote.Idle
        documenti.value = Remote.Idle
        tickets.value = Remote.Idle
        prenotazioni.value = Remote.Idle
        servizi.value = Remote.Idle
        priorita.value = Remote.Idle
        tipologie.value = Remote.Idle
        menu.value = Remote.Idle
        residenza.value = Remote.Idle
        _sync.value = SyncStatus.LOGGED_OUT
        _message.value = "Disconnesso da FormeIt."
        updateWidgets()
    }

    private fun updateWidgets() {
        val app = getApplication<Application>()
        CollegeWidgetProvider.updateAllWidgets(app)
        CollegeWidgetCompactProvider.updateAllWidgets(app)
    }

    // ---------------------------------------------------------------- loaders

    private fun <T> load(target: MutableStateFlow<Remote<T>>, block: suspend () -> T) {
        if (target.value is Remote.Loading) return
        viewModelScope.launch {
            target.value = Remote.Loading
            target.value = try {
                Remote.Ready(block())
            } catch (e: Exception) {
                Remote.Failed(e.message ?: "Errore sconosciuto")
            }
        }
    }

    fun loadComunicazioni() = load(comunicazioni) { repo.comunicazioni() }
    fun loadNotifiche() = load(notifiche) { repo.notifiche() }
    fun loadDocumenti() = load(documenti) { repo.documenti() }
    fun loadTickets() = load(tickets) { repo.tickets() }
    fun loadPrenotazioni() = load(prenotazioni) { repo.prenotazioni() }
    fun loadServizi() = load(servizi) { repo.servizi() }
    fun loadMenu() = load(menu) { repo.menu() }
    fun loadResidenza() = load(residenza) { repo.residenza() }
    fun loadTicketLookups() {
        load(priorita) { repo.priorita() }
        load(tipologie) { repo.tipologie() }
    }

    // ---------------------------------------------------------------- actions

    fun segnaLetta(n: FormeitNotification) {
        if (n.isRead) return
        viewModelScope.launch {
            try {
                repo.segnaNotificaLetta(n)
                val current = notifiche.value.valueOrNull ?: return@launch
                notifiche.value = Remote.Ready(current.map { item ->
                    if (item === n) FormeitNotification(item.rows.map { it.with("letto" to "S") }) else item
                })
            } catch (e: Exception) {
                _message.value = e.message
            }
        }
    }

    fun inviaTicket(t: FormeitRepository.NewTicket, onDone: (Boolean) -> Unit) = action(onDone, "Ticket inviato con successo.") {
        repo.inviaTicket(t)
        tickets.value = Remote.Idle
        loadTickets()
    }

    fun prenotaMenu(testo: String, onDone: (Boolean) -> Unit) = action(onDone, "Prenotazione menu inviata.") {
        repo.prenotaMenu(testo)
    }

    fun prenota(serviceId: Any?, start: String, duration: Any?, note: String, onDone: (Boolean) -> Unit) =
        action(onDone, "Prenotazione creata con successo.") {
            repo.prenota(serviceId, start, duration, note)
            prenotazioni.value = Remote.Idle
            loadPrenotazioni()
        }

    fun cancellaPrenotazione(id: String, onDone: (Boolean) -> Unit = {}) = action(onDone, "Prenotazione cancellata.") {
        repo.cancellaPrenotazione(id)
        prenotazioni.value = Remote.Idle
        loadPrenotazioni()
    }

    fun apriFile(url: String, name: String, onReady: (File) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(repo.scaricaFile(url, name))
            } catch (e: Exception) {
                _message.value = "Impossibile aprire il documento. ${e.message ?: ""}".trim()
            }
        }
    }

    private fun action(onDone: (Boolean) -> Unit, okMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _message.value = okMessage
                onDone(true)
            } catch (e: Exception) {
                _message.value = e.message ?: "Operazione non riuscita"
                onDone(false)
            }
        }
    }
}
