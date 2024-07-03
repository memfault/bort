package com.memfault.bort.metrics.database

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

interface MetricReportsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(report: DbReport): Long

    @Query("SELECT * FROM reports")
    suspend fun getReports(): List<DbReport>

    @Query("SELECT * FROM reports WHERE type = :reportType")
    suspend fun getReports(reportType: String): List<DbReport>

    @Query("SELECT * FROM reports WHERE type = :reportType AND endTimeMs IS NULL")
    suspend fun singleStartedReport(reportType: String): DbReport?

    @Query("SELECT * FROM reports WHERE type = :reportType AND name = :reportName AND endTimeMs IS NULL")
    suspend fun singleStartedReport(
        reportType: String,
        reportName: String,
    ): DbReport?

    @Query("SELECT * FROM reports WHERE type = :reportType AND endTimeMs IS NULL")
    suspend fun getStartedReports(reportType: String): List<DbReport>

    @Query("SELECT * FROM reports WHERE type = :reportType AND endTimeMs IS NOT NULL")
    suspend fun getEndedReports(reportType: String): List<DbReport>

    @Query("DELETE FROM reports WHERE id = :reportId")
    suspend fun deleteReport(reportId: Long): Int

    @Query(
        "DELETE FROM reports WHERE NOT EXISTS " +
            "(SELECT * FROM metric_metadata WHERE reports.id = metric_metadata.reportId)" +
            "AND startTimeMs <= :expiryTimestampMs",
    )
    suspend fun deleteExpiredOrphanedReports(expiryTimestampMs: Long): Int

    @Query("UPDATE reports SET startTimeMs = :startTimestampMs WHERE id = :reportId")
    suspend fun updateReportStartTimestamp(
        reportId: Long,
        startTimestampMs: Long,
    ): Int

    @Query("UPDATE reports SET endTimeMs = :endTimestampMs WHERE id = :reportId")
    suspend fun updateReportEndTimestamp(
        reportId: Long,
        endTimestampMs: Long,
    ): Int

    @Query("UPDATE reports SET endTimeMs = :endTimestampMs WHERE id IN (:reportIds)")
    suspend fun updateReportEndTimestamps(
        reportIds: List<Long>,
        endTimestampMs: Long,
    ): Int
}
