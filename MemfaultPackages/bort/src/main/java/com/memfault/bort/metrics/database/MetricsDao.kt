package com.memfault.bort.metrics.database

import android.util.JsonWriter
import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.metrics.HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration

@Dao
abstract class MetricsDao {
    @Transaction
    open suspend fun insert(metric: MetricValue): Long {
        val reportId = insertOrGetReport(
            DbReport(
                type = metric.reportType,
                startTimeMs = metric.timeMs,
            ),
        )
        val metadataId = insertOrUpdateMetadataIfNeeded(
            DbMetricMetadata(
                reportId = reportId,
                eventName = metric.eventName,
                metricType = metric.metricType,
                dataType = metric.dataType,
                carryOver = metric.carryOverValue,
                aggregations = Aggs(metric.aggregations),
                internal = metric.internal,
            ),
        )
        return insert(
            DbMetricValue(
                metadataId = metadataId,
                version = metric.version,
                timestampMs = metric.timeMs,
                stringVal = metric.stringVal,
                numberVal = metric.numberVal,
                boolVal = metric.boolVal,
            ),
        )
    }

    @VisibleForTesting
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(report: DbReport): Long

    /**
     * Insert report. If a report of the same type and name already exists, return it instead.
     *
     * @return ID.
     */
    @Transaction
    protected open suspend fun insertOrGetReport(report: DbReport): Long {
        val reportOrNull = if (report.name != null) {
            getReport(reportType = report.type, reportName = report.name)
        } else {
            getReport(reportType = report.type)
        }
        return reportOrNull?.id ?: insert(report)
    }

    /**
     * Upsert metric metadata (only change if different).
     *
     * @return ID.
     */
    @Transaction
    protected open suspend fun insertOrUpdateMetadataIfNeeded(metadata: DbMetricMetadata): Long {
        val existing = getMetadata(reportId = metadata.reportId, eventName = metadata.eventName)
        // Equals won't match unless we copy over the ID (we only care about the properties matching).
        if (existing?.equals(metadata.copy(id = existing.id)) == true) {
            return existing.id
        }
        return insertOrReplace(metadata)
    }

    @Query("SELECT * FROM metric_metadata WHERE reportId = :reportId AND eventName = :eventName")
    protected abstract suspend fun getMetadata(
        reportId: Long,
        eventName: String,
    ): DbMetricMetadata?

    @VisibleForTesting
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(metadata: DbMetricMetadata): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(metric: DbMetricValue): Long

    private suspend fun calculateAggregations(
        metadata: DbMetricMetadata,
        reportStartMs: Long,
        reportEndMs: Long,
    ): Map<String, JsonPrimitive> {
        val metrics = mutableMapOf<String, JsonPrimitive>()
        val aggs = metadata.aggregations.aggregations
        val key = metadata.eventName
        val numericAggs = aggs.filterIsInstance<NumericAgg>()
        if (numericAggs.isNotEmpty()) {
            val results = getNumericAggregations(metadataId = metadata.id)
            for (numericAgg in numericAggs) {
                when (numericAgg) {
                    NumericAgg.COUNT -> metrics["$key.count"] = JsonPrimitive(results.count)
                    NumericAgg.LATEST_VALUE -> Unit // handled below
                    NumericAgg.MAX -> metrics["$key.max"] = JsonPrimitive(results.max)
                    NumericAgg.MEAN -> metrics["$key.mean"] = JsonPrimitive(results.mean)
                    NumericAgg.MIN -> metrics["$key.min"] = JsonPrimitive(results.min)
                    NumericAgg.SUM -> metrics["$key.sum"] = JsonPrimitive(results.sum)
                }
            }
        }
        if (aggs.any { it == NumericAgg.LATEST_VALUE || it == StateAgg.LATEST_VALUE }) {
            val results = getLatestMetricValue(metadataId = metadata.id)
            metrics["$key.latest"] = results.jsonValue()
        }
        if (aggs.any { it == StateAgg.TIME_PER_HOUR || it == StateAgg.TIME_TOTALS }) {
            val values = getMetricValuesFlow(metadataId = metadata.id, pageSize = 250, bufferSize = 500)
            val timeInStateMs = mutableMapOf<String, Long>()
            var last: DbMetricValue? = null
            values.collect { d ->
                if (d.timestampMs <= reportEndMs) {
                    last?.let { l ->
                        val state = l.jsonValue().contentOrNull ?: return@let
                        val existingTimeInState = timeInStateMs.getOrDefault(state, 0)
                        val timeOfLastRecord = l.timestampMs.coerceAtLeast(reportStartMs)
                        val timeOfCurrentRecord = d.timestampMs.coerceAtLeast(reportStartMs)
                        val durationSinceLastRecord = timeOfCurrentRecord - timeOfLastRecord
                        timeInStateMs[state] = existingTimeInState + durationSinceLastRecord
                    }
                    last = d
                }
            }
            // End of report
            last?.let { l ->
                val state = l.jsonValue().contentOrNull ?: return@let
                val existingTimeInState = timeInStateMs.getOrDefault(state, 0)
                val timeOfLastRecord = l.timestampMs.coerceAtLeast(reportStartMs)
                val timeOfCurrentRecord = reportEndMs.coerceAtLeast(reportStartMs)
                val durationSinceLastRecord = timeOfCurrentRecord - timeOfLastRecord
                timeInStateMs[state] = existingTimeInState + durationSinceLastRecord
            }
            // Calculate totals
            val reportDurationMs = reportEndMs - reportStartMs
            timeInStateMs.forEach { (state, timeMs) ->
                if (StateAgg.TIME_PER_HOUR in aggs) {
                    metrics["${key}_$state.secs/hour"] =
                        JsonPrimitive(
                            (1.hours * (timeMs.milliseconds.div(reportDurationMs.milliseconds)))
                                .coerceAtMost(1.hours)
                                .inWholeSeconds,
                        )
                }
                if (StateAgg.TIME_TOTALS in aggs) {
                    metrics["${key}_$state.total_secs"] = JsonPrimitive(timeMs.milliseconds.inWholeSeconds)
                }
            }
        }
        return metrics
    }

    @Transaction
    open suspend fun collectHeartbeat(
        reportType: String = HEARTBEAT_REPORT_TYPE,
        endTimestampMs: Long,
        hrtFile: File?,
    ): CustomReport {
        val dbReport = getReport(reportType = reportType)
        if (dbReport == null) {
            Logger.i("Didn't find report in database for $HEARTBEAT_REPORT_TYPE")
            return CustomReport(
                report = MetricReport(
                    version = METRICS_VERSION,
                    startTimestampMs = endTimestampMs,
                    endTimestampMs = endTimestampMs,
                    reportType = HEARTBEAT_REPORT_TYPE,
                    metrics = emptyMap(),
                    internalMetrics = emptyMap(),
                ),
                hrt = hrtFile,
            )
        }
        val startTimestampMs = dbReport.startTimeMs
        val reportMetadata = getMetricMetadata(reportId = dbReport.id)
        val metrics = mutableMapOf<String, JsonPrimitive>()
        val internalMetrics = mutableMapOf<String, JsonPrimitive>()
        for (metadata in reportMetadata) {
            val aggregations = calculateAggregations(
                metadata = metadata,
                reportStartMs = startTimestampMs,
                reportEndMs = endTimestampMs,
            )
            if (metadata.internal) {
                internalMetrics.putAll(aggregations)
            } else {
                metrics.putAll(aggregations)
            }
        }
        val report = MetricReport(
            version = METRICS_VERSION,
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs,
            reportType = dbReport.type,
            metrics = metrics,
            internalMetrics = internalMetrics,
        )
        hrtFile?.let {
            hrtFile.bufferedWriter().use { writer ->
                JsonWriter(writer).use { json ->
                    json.beginObject()
                    json.name("schema_version").value(1)
                    json.name("start_time").value(startTimestampMs)
                    json.name("duration_ms").value(endTimestampMs - startTimestampMs)
                    json.name("report_type").value(dbReport.type)

                    json.name("producer").beginObject()
                    json.name("version").value("1")
                    json.name("id").value("bort")
                    json.endObject()

                    json.name("rollups").beginArray()
                    reportMetadata.forEach { metadata ->
                        val values = getMetricValuesFlow(
                            metadataId = metadata.id,
                            pageSize = PAGE_SIZE,
                            bufferSize = BUFFER_SIZE,
                        )
                        var writtenMetadata = false
                        values.collect { d ->
                            if (!writtenMetadata) {
                                json.beginObject()
                                json.name("metadata").beginObject()
                                json.name("string_key").value(metadata.eventName)
                                json.name("metric_type").value(metadata.metricType.value)
                                json.name("data_type").value(metadata.dataType.value)
                                json.name("internal").value(metadata.internal)
                                json.endObject()
                                json.name("data").beginArray()
                                writtenMetadata = true
                            }

                            json.beginObject()
                            val timestamp = d.timestampMs.coerceAtLeast(startTimestampMs)
                            json.name("t").value(timestamp)
                            if (d.boolVal != null) {
                                json.name("value").value(d.boolVal)
                            } else if (d.stringVal != null) {
                                json.name("value").value(d.stringVal)
                            } else if (d.numberVal != null) {
                                json.name("value").value(d.numberVal)
                            }
                            json.endObject()
                        }
                        if (writtenMetadata) {
                            json.endArray()
                            json.endObject()
                        }
                    }

                    json.endArray()
                    json.endObject()
                }
            }
        }

        // Delete all values, metadata, and reports. If carryOver is enabled for a metric, then carry over its latest
        // value to the next report. CarryOvers are only kept for 3 days if they're not updated.
        val allCarryOverMetricMetadataIds = getMetricMetadataIdsForCarryOver(dbReport.id)
        val carryOverMetricValues = allCarryOverMetricMetadataIds.map { id -> getLatestMetricValue(id) }
            .filter {
                Duration.between(Instant.ofEpochMilli(it.timestampMs), Instant.ofEpochMilli(endTimestampMs))
                    .toKotlinDuration() <= 3.days
            }
        val carryOverMetricMetadataIds = carryOverMetricValues.map { it.metadataId }.toSet()

        deleteMetricValues(dbReport.id)

        getMetricMetadata(dbReport.id)
            .filter { it.id !in carryOverMetricMetadataIds }
            .chunked(1_000)
            .forEach { metadataChunk -> deleteMetricMetadataIds(metadataIds = metadataChunk.map { it.id }) }

        if (carryOverMetricValues.isEmpty()) {
            deleteReport(dbReport.id)
        } else {
            updateReportTimestamp(dbReport.type, startTimestampMs = endTimestampMs)
            carryOverMetricValues.forEach { insert(it) }
        }

        return CustomReport(
            report = report,
            hrt = hrtFile,
        )
    }

    @VisibleForTesting suspend fun dump(): DbDump = DbDump(
        reports = getReports(),
        metadata = getMetricMetadata(),
        values = getMetricValues(),
    )

    @Query(
        """SELECT
        MIN(v.numberVal) as min,
        MAX(v.numberVal) as max,
        SUM(v.numberVal) as sum,
        AVG(v.numberVal) as mean,
        COUNT(v.numberVal) as count
        FROM metric_values v
        WHERE v.metadataId = :metadataId""",
    )
    protected abstract suspend fun getNumericAggregations(metadataId: Long): DbNumericAggs

    @Query(
        """SELECT
        *
        FROM metric_values v
        WHERE v.metadataId = :metadataId
        ORDER BY v.timestampMs DESC
        LIMIT 1""",
    )
    protected abstract suspend fun getLatestMetricValue(metadataId: Long): DbMetricValue

    @Query("SELECT * FROM reports")
    protected abstract suspend fun getReports(): List<DbReport>

    @Query("SELECT * FROM reports WHERE type = :reportType")
    protected abstract suspend fun getReport(reportType: String): DbReport?

    @Query("SELECT * FROM reports WHERE type = :reportType AND name = :reportName")
    protected abstract suspend fun getReport(reportType: String, reportName: String): DbReport?

    @Query("SELECT * FROM metric_metadata WHERE reportId = :reportId")
    protected abstract suspend fun getMetricMetadata(reportId: Long): List<DbMetricMetadata>

    /**
     * Create a flow of metric values. This avoids loading them all into memory at once (the emitter will block until
     * the buffer is consumed by the caller).
     */
    private fun getMetricValuesFlow(
        metadataId: Long,
        pageSize: Long,
        bufferSize: Int,
    ): Flow<DbMetricValue> = flow {
        var offset = 0L
        while (true) {
            val values = getMetricValuesPage(metadataId = metadataId, limit = pageSize, offset = offset)
            if (values.isEmpty()) break
            values.forEach {
                emit(it)
            }
            offset += pageSize
        }
    }.buffer(capacity = bufferSize, onBufferOverflow = BufferOverflow.SUSPEND)

    @Query(
        "SELECT * FROM metric_values WHERE metadataId = :metadataId " +
            "ORDER BY timestampMs ASC LIMIT :limit OFFSET :offset",
    )
    protected abstract suspend fun getMetricValuesPage(
        metadataId: Long,
        limit: Long,
        offset: Long,
    ): List<DbMetricValue>

    @Query(
        "DELETE FROM metric_values WHERE metadataId in " +
            "(SELECT id FROM metric_metadata WHERE reportId = :reportId)",
    )
    protected abstract suspend fun deleteMetricValues(reportId: Long): Int

    @Query("SELECT * FROM metric_metadata")
    protected abstract suspend fun getMetricMetadata(): List<DbMetricMetadata>

    @Query(
        "SELECT id FROM metric_metadata WHERE reportId = :reportId AND carryOver = 1",
    )
    protected abstract suspend fun getMetricMetadataIdsForCarryOver(
        reportId: Long,
    ): List<Long>

    @Query("DELETE FROM metric_metadata WHERE id in (:metadataIds)")
    protected abstract suspend fun deleteMetricMetadataIds(metadataIds: List<Long>): Int

    @Query("SELECT * FROM metric_values")
    protected abstract suspend fun getMetricValues(): List<DbMetricValue>

    @Query("DELETE FROM reports WHERE id = :reportId")
    protected abstract suspend fun deleteReport(reportId: Long): Int

    @Query("UPDATE reports SET startTimeMs = :startTimestampMs WHERE type = :reportType")
    protected abstract suspend fun updateReportTimestamp(reportType: String, startTimestampMs: Long): Int

    companion object {
        const val METRICS_VERSION = 1

        /**
         * Number of database value records to fetch in each page/query.
         *
         * I haven't spent any time trying to optimise this number.
         **/
        const val PAGE_SIZE: Long = 250

        /**
         * Flow buffer size for metric value records.
         *
         * I haven't spent any time trying to optimise this number.
         */
        const val BUFFER_SIZE = 500
    }
}
