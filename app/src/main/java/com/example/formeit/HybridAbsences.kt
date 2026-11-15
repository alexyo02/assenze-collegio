package com.example.formeit

import android.content.Context

/**
 * Hybrid reconciliation between the local ledger and the official FormeIt counters.
 *
 * The official record (gate transits processed by the residence) is the source of truth, but
 * it only exposes aggregate counters. The local ledger keeps day-level detail. For every risk
 * computation the beta uses the larger of the two unjustified counts, so a missing local entry
 * can never make the residency look safer than the official record says.
 */
object HybridAbsences {

    data class Comparison(
        val localUnjustifiedYear: Int,
        val officialUnjustifiedYear: Int?,
        val localUnjustifiedMonth: Int,
        val officialUnjustifiedMonth: Int?,
        val localJustifiedMonth: Int,
        val officialJustifiedMonth: Int?
    ) {
        val effectiveUnjustified: Int get() = maxOf(localUnjustifiedYear, officialUnjustifiedYear ?: 0)
        val usesOfficial: Boolean get() = (officialUnjustifiedYear ?: -1) > localUnjustifiedYear
        val monthMismatch: Boolean
            get() = (officialUnjustifiedMonth != null && officialUnjustifiedMonth != localUnjustifiedMonth) ||
                (officialJustifiedMonth != null && officialJustifiedMonth != localJustifiedMonth)
        val yearMismatch: Boolean get() = officialUnjustifiedYear != null && officialUnjustifiedYear != localUnjustifiedYear
    }

    /** Effective unjustified count for widgets, which compute their own local count. */
    fun effectiveUnjustified(context: Context, localUnjustified: Int): Int {
        val official = FormeitRepository.get(context).session.value?.profile?.assenzeIngiustificateAnno
        return maxOf(localUnjustified, official ?: 0)
    }
}
