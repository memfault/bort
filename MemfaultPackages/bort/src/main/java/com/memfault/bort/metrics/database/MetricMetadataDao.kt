package com.memfault.bort.metrics.database

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

interface MetricMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metadata: DbMetricMetadata): Long

    @Query("SELECT * FROM metric_metadata")
    suspend fun getMetricMetadata(): List<DbMetricMetadata>

    @Query("SELECT * FROM metric_metadata WHERE reportId = :reportId AND eventName = :eventName")
    suspend fun getMetadata(
        reportId: Long,
        eventName: String,
    ): DbMetricMetadata?

    @Query("SELECT * FROM metric_metadata WHERE reportId = :reportId AND carryOver = 1")
    suspend fun getMetricMetadataWithCarryOver(reportId: Long): List<DbMetricMetadata>

    @Query("SELECT * FROM metric_metadata WHERE reportId IN (:reportIds)")
    suspend fun getMetricMetadata(reportIds: List<Long>): List<DbMetricMetadata>

    @Query("DELETE FROM metric_metadata WHERE reportId = :reportId")
    suspend fun deleteMetricMetadata(reportId: Long): Int

    @Query("DELETE FROM metric_metadata WHERE id in (:metadataIds)")
    suspend fun deleteMetricMetadataIds(metadataIds: List<Long>): Int

    @Query(
        "DELETE FROM metric_metadata WHERE NOT EXISTS " +
            "(SELECT * FROM metric_values WHERE metric_metadata.id = metric_values.metadataId)",
    )
    suspend fun deleteOrphanedMetricMetadata(): Int
}
