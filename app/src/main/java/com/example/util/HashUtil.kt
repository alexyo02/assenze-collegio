package com.example.util

import java.security.MessageDigest

object HashUtil {
    /**
     * Computes a deterministic SHA-256 seal for an absence record.
     * Ensures records are rigorous, immutable, and tamper-evident.
     */
    fun computeRecordHash(
        entryNumber: Long,
        dateMillis: Long,
        createdTimestamp: Long,
        absenceType: String,
        reason: String,
        isJustified: Boolean = false,
        medicalCertificateNote: String? = null
    ): String {
        val payload = "ENTRY=$entryNumber|DATE=$dateMillis|CREATED=$createdTimestamp|TYPE=$absenceType|REASON=${reason.trim()}|JUSTIFIED=$isJustified|MED=${medicalCertificateNote?.trim() ?: ""}"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "HASH_ERROR_${System.currentTimeMillis()}"
        }
    }

    fun verifyRecordHash(
        entryNumber: Long,
        dateMillis: Long,
        createdTimestamp: Long,
        absenceType: String,
        reason: String,
        isJustified: Boolean = false,
        medicalCertificateNote: String? = null,
        expectedHash: String
    ): Boolean {
        val calculated = computeRecordHash(
            entryNumber,
            dateMillis,
            createdTimestamp,
            absenceType,
            reason,
            isJustified,
            medicalCertificateNote
        )
        return calculated.equals(expectedHash, ignoreCase = true)
    }
}
