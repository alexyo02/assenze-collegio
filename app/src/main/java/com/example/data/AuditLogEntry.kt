package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.util.DateUtil
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "audit_logs")
data class AuditLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val details: String,
    val recordIdRef: Long? = null
) {
    val formattedTimestamp: String
        get() = DateUtil.formatDateTime(timestamp)
}
