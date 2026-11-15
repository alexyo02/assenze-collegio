package com.example.formeit

import android.content.Context
import com.example.util.CivilHolidaysUtil
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * FormeIt exposes no absence history to students: only the current month/year counters,
 * the last absence day and the last gate transit. This store records a snapshot of those
 * values every time they change and rebuilds a history from the sequence of snapshots.
 */
data class OfficialSnapshot(
    val firstSeen: Long,
    val lastSeen: Long,
    val giustAnno: Int?,
    val ingiustAnno: Int?,
    val giustMese: Int?,
    val ingiustMese: Int?,
    val ultimaAssenza: String,
    val transito: String,
    val transitoData: String
) {
    fun sameValues(o: OfficialSnapshot) =
        giustAnno == o.giustAnno && ingiustAnno == o.ingiustAnno && giustMese == o.giustMese &&
            ingiustMese == o.ingiustMese && ultimaAssenza == o.ultimaAssenza &&
            transito == o.transito && transitoData == o.transitoData

    companion object {
        fun of(p: FormeitProfile, now: Long) = OfficialSnapshot(
            now, now, p.assenzeGiustificateAnno, p.assenzeIngiustificateAnno, p.assenzeGiustificateMese,
            p.assenzeIngiustificateMese, p.ultimaAssenzaRaw, p.ultimoTransito, p.ultimoTransitoDataRaw
        )
    }
}

enum class Certainty { EXACT, INFERRED }

/** An absence night reconstructed from the official counters. */
data class OfficialAbsenceDay(
    val dayMillis: Long,
    /** true = reported among "giustificate", false = "non giustificate", null = unknown. */
    val justified: Boolean?,
    val certainty: Certainty,
    /** Non-null when the day is a civil holiday / closure, i.e. not computed by the regulation. */
    val excludedReason: String?
)

data class OfficialTransit(val state: String, val atMillis: Long?, val raw: String)

data class OfficialMonth(val key: String, val giust: Int?, val ingiust: Int?, val asOf: Long)

data class OfficialHistoryView(
    val days: List<OfficialAbsenceDay>,
    val transits: List<OfficialTransit>,
    val months: List<OfficialMonth>,
    /** Absences of the current year that happened before the current month (dates unknown). */
    val earlierThisYearGiust: Int?,
    val earlierThisYearIngiust: Int?,
    val trackingSince: Long?
)

object OfficialHistoryInference {

    private fun monthKey(millis: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(millis))

    private fun dayBefore(day: Long, n: Int): Long = Calendar.getInstance().apply {
        timeInMillis = day; add(Calendar.DAY_OF_MONTH, -n)
    }.timeInMillis

    /**
     * Rebuilds absence days from snapshots (sorted by [OfficialSnapshot.firstSeen]):
     * - when the last-absence day changes and the monthly counters grow by exactly one,
     *   that day and its classification are certain;
     * - when they grow by k > 1, the k nights ending on the new last-absence day are assumed
     *   (a continuous absence between two observations) and marked as inferred;
     * - for the very first snapshot, the current month's absences are assumed to be the
     *   nights ending on the last-absence day (inferred), the last one being certain.
     * Within a block, nights on excluded days (holidays/closures) are the ones FormeIt
     * reports as "giustificate"; the rest are assigned to the unjustified counter first.
     */
    fun infer(snapshots: List<OfficialSnapshot>): OfficialHistoryView {
        val days = linkedMapOf<Long, OfficialAbsenceDay>()
        val transits = linkedMapOf<String, OfficialTransit>()
        val months = linkedMapOf<String, OfficialMonth>()

        var prev: OfficialSnapshot? = null
        for (s in snapshots) {
            if (s.transitoData.isNotBlank()) {
                transits[s.transito + "|" + s.transitoData] =
                    OfficialTransit(s.transito, FormeitDates.parseDateTime(s.transitoData), s.transitoData)
            }
            val snapMonth = monthKey(s.lastSeen)
            months[snapMonth] = OfficialMonth(snapMonth, s.giustMese, s.ingiustMese, s.lastSeen)

            val last = FormeitDates.parseDay(s.ultimaAssenza)
            val p = prev
            if (last != null) {
                val sameMonthAsSnapshot = monthKey(last) == monthKey(s.firstSeen)
                if (p == null) {
                    if (sameMonthAsSnapshot) {
                        val n = (s.giustMese ?: 0) + (s.ingiustMese ?: 0)
                        addBlock(days, last, n.coerceAtLeast(1), s.giustMese ?: 0, s.ingiustMese ?: 0, firstIsExact = true)
                    } else {
                        put(days, OfficialAbsenceDay(last, null, Certainty.EXACT, CivilHolidaysUtil.getExclusionReason(last)))
                    }
                } else if (s.ultimaAssenza != p.ultimaAssenza) {
                    val monthChanged = monthKey(s.firstSeen) != monthKey(p.lastSeen)
                    val dG = if (monthChanged) (s.giustMese ?: 0) else (s.giustMese ?: 0) - (p.giustMese ?: 0)
                    val dI = if (monthChanged) (s.ingiustMese ?: 0) else (s.ingiustMese ?: 0) - (p.ingiustMese ?: 0)
                    val n = (dG + dI).coerceAtLeast(1)
                    addBlock(days, last, n, dG.coerceAtLeast(0), dI.coerceAtLeast(0), firstIsExact = true)
                }
            }
            prev = s
        }

        val latest = snapshots.lastOrNull()
        val earlierG = latest?.let { s -> s.giustAnno?.let { a -> (a - (s.giustMese ?: 0)).coerceAtLeast(0) } }
        val earlierI = latest?.let { s -> s.ingiustAnno?.let { a -> (a - (s.ingiustMese ?: 0)).coerceAtLeast(0) } }

        return OfficialHistoryView(
            days = days.values.sortedByDescending { it.dayMillis },
            transits = transits.values.sortedByDescending { it.atMillis ?: 0L },
            months = months.values.sortedByDescending { it.key },
            earlierThisYearGiust = earlierG,
            earlierThisYearIngiust = earlierI,
            trackingSince = snapshots.firstOrNull()?.firstSeen
        )
    }

    private fun addBlock(
        days: MutableMap<Long, OfficialAbsenceDay>, last: Long, n: Int, giust: Int, ingiust: Int, firstIsExact: Boolean
    ) {
        val block = (0 until n).map { dayBefore(last, it) }
        val excluded = block.filter { CivilHolidaysUtil.isExcludedDay(it) }.toSet()
        // Assign "giustificate" first to excluded days (holidays), then remaining ones unknown;
        // "non giustificate" go to the remaining non-excluded days.
        var remainingG = giust
        var remainingI = ingiust
        val classification = mutableMapOf<Long, Boolean?>()
        block.filter { it in excluded }.forEach { d -> classification[d] = if (remainingG > 0) { remainingG--; true } else null }
        block.filter { it !in excluded }.sortedDescending().forEach { d ->
            classification[d] = when {
                remainingI > 0 -> { remainingI--; false }
                remainingG > 0 -> { remainingG--; true }
                else -> null
            }
        }
        block.forEach { d ->
            val certain = firstIsExact && d == last && n == 1
            put(
                days,
                OfficialAbsenceDay(
                    d, classification[d], if (certain) Certainty.EXACT else Certainty.INFERRED,
                    CivilHolidaysUtil.getExclusionReason(d)
                ).let { if (d == last) it.copy(certainty = Certainty.EXACT) else it }
            )
        }
    }

    /** Never downgrade a day already known with certainty. */
    private fun put(days: MutableMap<Long, OfficialAbsenceDay>, d: OfficialAbsenceDay) {
        val existing = days[d.dayMillis]
        if (existing == null || existing.certainty == Certainty.INFERRED) {
            days[d.dayMillis] = if (d.justified == null && existing?.justified != null) d.copy(justified = existing.justified) else d
        }
    }
}

class OfficialHistoryStore private constructor(context: Context) {
    private val file = File(context.applicationContext.filesDir, "formeit_history.json")
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)
    private val lock = Any()
    private var snapshots: MutableList<OfficialSnapshot> = load()

    private val _view = MutableStateFlow(OfficialHistoryInference.infer(snapshots))
    val view: StateFlow<OfficialHistoryView> = _view.asStateFlow()

    fun record(p: FormeitProfile, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (!p.hasPresenceData && p.ultimoTransitoDataRaw.isBlank()) return
        val snap = OfficialSnapshot.of(p, now)
        val last = snapshots.lastOrNull()
        if (last != null && last.sameValues(snap)) {
            snapshots[snapshots.lastIndex] = last.copy(lastSeen = now)
        } else {
            snapshots.add(snap)
            if (snapshots.size > MAX) snapshots = snapshots.takeLast(MAX).toMutableList()
        }
        save()
        _view.value = OfficialHistoryInference.infer(snapshots)
    }

    fun clear() = synchronized(lock) {
        snapshots.clear(); file.delete()
        _view.value = OfficialHistoryInference.infer(snapshots)
    }

    private fun save() {
        val list = snapshots.map {
            mapOf(
                "firstSeen" to it.firstSeen, "lastSeen" to it.lastSeen,
                "giustAnno" to it.giustAnno, "ingiustAnno" to it.ingiustAnno,
                "giustMese" to it.giustMese, "ingiustMese" to it.ingiustMese,
                "ultimaAssenza" to it.ultimaAssenza, "transito" to it.transito, "transitoData" to it.transitoData
            )
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(adapter.toJson(list))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    private fun load(): MutableList<OfficialSnapshot> = runCatching {
        if (!file.exists()) return mutableListOf()
        JObj.asObjList(adapter.fromJson(file.readText())).map {
            OfficialSnapshot(
                it.long("firstSeen") ?: 0L, it.long("lastSeen") ?: 0L,
                it.int("giustAnno"), it.int("ingiustAnno"), it.int("giustMese"), it.int("ingiustMese"),
                it.text("ultimaAssenza"), it.text("transito"), it.text("transitoData")
            )
        }.sortedBy { it.firstSeen }.toMutableList()
    }.getOrElse { mutableListOf() }

    companion object {
        private const val MAX = 5000
        @Volatile private var instance: OfficialHistoryStore? = null
        fun get(context: Context): OfficialHistoryStore =
            instance ?: synchronized(this) { instance ?: OfficialHistoryStore(context).also { instance = it } }
    }
}
