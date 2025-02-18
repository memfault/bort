package com.memfault.bort.metrics.database

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

interface MetricValuesDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(metric: DbMetricValue): Long

    @Query(
        """SELECT
        *
        FROM metric_values v
        WHERE v.metadataId IN (:metadataIds)
        AND v.timestampMs >= :startTimestampMs
        AND v.timestampMs <= :endTimestampMs
        ORDER BY v.timestampMs DESC
        LIMIT 1""",
    )
    suspend fun getLatestMetricValueInRange(
        metadataIds: List<Long>,
        startTimestampMs: Long,
        endTimestampMs: Long,
    ): DbMetricValue?

    @Query(
        """SELECT
        *
        FROM metric_values v
        WHERE v.metadataId IN (:metadataIds)
        AND v.timestampMs <= :endTimestampMs
        ORDER BY v.timestampMs DESC
        LIMIT 1""",
    )
    suspend fun getLatestMetricValue(
        metadataIds: List<Long>,
        endTimestampMs: Long,
    ): DbMetricValue?

    @Query(
        "SELECT * FROM metric_values v WHERE metadataId IN (:metadataIds) " +
            "AND v.timestampMs <= :endTimestampMs " +
            "ORDER BY v.timestampMs ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun getMetricValuesPage(
        metadataIds: List<Long>,
        limit: Long,
        offset: Long,
        endTimestampMs: Long,
    ): List<DbMetricValue>

    @Query(
        "SELECT * FROM metric_values v WHERE metadataId IN (:metadataIds) " +
            "AND v.timestampMs <= :endTimestampMs " +
            "ORDER BY v.timestampMs DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun getMetricValuesPageDesc(
        metadataIds: List<Long>,
        limit: Long,
        offset: Long,
        endTimestampMs: Long,
    ): List<DbMetricValue>

    @Query("SELECT * FROM metric_values")
    suspend fun getMetricValues(): List<DbMetricValue>

    @Query(
        "DELETE FROM metric_values WHERE metadataId in " +
            "(SELECT id FROM metric_metadata WHERE reportId = :reportId)",
    )
    suspend fun deleteMetricValues(reportId: Long): Int

    @Query(
        "DELETE FROM metric_values WHERE metadataId in " +
            "(SELECT id FROM metric_metadata WHERE reportId = :reportId) AND " +
            "timestampMs <= :expiryTimestampMs",
    )
    suspend fun deleteExpiredMetricValues(
        reportId: Long,
        expiryTimestampMs: Long,
    ): Int
}
