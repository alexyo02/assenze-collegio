package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AbsenceDao {
    @Query("SELECT * FROM absence_records ORDER BY dateMillis DESC, entryNumber DESC")
    fun getAllAbsences(): Flow<List<AbsenceRecord>>

    @Query("SELECT * FROM absence_records WHERE :day >= dateMillis AND :day <= endDateMillis ORDER BY createdTimestamp DESC")
    fun getAbsencesForDate(day: Long): Flow<List<AbsenceRecord>>

    @Query("SELECT * FROM absence_records WHERE endDateMillis >= :fromMillis AND dateMillis <= :toMillis ORDER BY dateMillis ASC")
    fun getAbsencesBetween(fromMillis: Long, toMillis: Long): Flow<List<AbsenceRecord>>

    @Query("SELECT COUNT(*) FROM absence_records")
    fun getAbsenceCount(): Flow<Int>

    @Query("SELECT MAX(entryNumber) FROM absence_records")
    suspend fun getMaxEntryNumber(): Long?

    @Query("SELECT * FROM absence_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AbsenceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AbsenceRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AbsenceRecord>)

    @Update
    suspend fun update(record: AbsenceRecord)

    @Delete
    suspend fun delete(record: AbsenceRecord)

    @Query("DELETE FROM absence_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM absence_records")
    suspend fun clearAll()

    @Query("SELECT * FROM absence_records ORDER BY dateMillis ASC, entryNumber ASC")
    suspend fun getAllDirect(): List<AbsenceRecord>
}
