package com.memfault.bort.diagnostics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.memfault.bort.time.toAbsoluteTime

@Dao
abstract class BortErrorsDao {
    @Insert
    protected abstract suspend fun insert(error: DbBortError): Long

    suspend fun insert(
        error: BortError,
        uploaded: Boolean,
    ): Long = insert(error.asDbError(uploaded = uploaded))

    @Query("SELECT * FROM errors ORDER BY timeMs ASC")
    protected abstract suspend fun getAllErrorsForDiagnostics(): List<DbBortError>

    suspend fun getAllBortErrorsForDiagnostics(): List<BortError> =
        getAllErrorsForDiagnostics().map { it.asBortError() }

    @Transaction
    open suspend fun getErrorsForUpload(): List<BortError> {
        val errorsToUpload = getErrorsToUpload()
        markAllErrorsUploaded()
        return errorsToUpload.map { it.asBortError() }
    }

    @Query("SELECT * FROM errors WHERE uploaded = 0 ORDER BY timeMs ASC")
    protected abstract suspend fun getErrorsToUpload(): List<DbBortError>

    @Query("UPDATE errors SET uploaded = 1")
    protected abstract suspend fun markAllErrorsUploaded(): Int

    @Query("DELETE FROM errors WHERE timeMs < :timeMs")
    abstract suspend fun deleteErrorsEarlierThan(timeMs: Long): Int

    @Insert
    abstract suspend fun addJob(job: DbJob): Long

    @Query("UPDATE jobs SET endTimeMs = :endTimeMs, result = :result WHERE rowid = :id")
    abstract suspend fun updateJob(id: Long, endTimeMs: Long, result: String)

    @Query("DELETE FROM jobs WHERE startTimeMs < :timeMs")
    abstract suspend fun deleteJobsEarlierThan(timeMs: Long): Int

    @Query("SELECT * FROM jobs ORDER BY startTimeMs DESC")
    abstract suspend fun getAllJobsMostRecentFirst(): List<DbJob>

    companion object {
        private fun BortError.asDbError(uploaded: Boolean) =
            DbBortError(
                id = 0,
                timeMs = timestamp.timestamp.toEpochMilli(),
                type = type,
                eventData = eventData,
                uploaded = uploaded,
            )

        private fun DbBortError.asBortError() =
            BortError(timestamp = timeMs.toAbsoluteTime(), type = type, eventData = eventData)
    }
}
