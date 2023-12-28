package com.memfault.bort.metrics.database

import android.util.JsonWriter
import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

private fun MetricType.otherMetricType(): HighResTelemetry.MetricType = when (this) {
    MetricType.COUNTER -> HighResTelemetry.MetricType.Counter
    MetricType.GAUGE -> HighResTelemetry.MetricType.Gauge
    MetricType.PROPERTY -> HighResTelemetry.MetricType.Property
    MetricType.EVENT -> HighResTelemetry.MetricType.Event
}

private fun DataType.otherMetricType(): HighResTelemetry.DataType = when (this) {
    DataType.DOUBLE -> HighResTelemetry.DataType.DoubleType
    DataType.STRING -> HighResTelemetry.DataType.StringType
    DataType.BOOLEAN -> HighResTelemetry.DataType.BooleanType
}

@Dao
abstract class MetricsDao {
    @Transaction
    open suspend fun insert(metric: MetricValue): Long {
        insert(
            DbReport(
                type = metric.reportType,
                startTimeMs = metric.timeMs,
            ),
        )
        val metricId = insertOrUpdateIfNeeded(
            DbMetricMetadata(
                reportType = metric.reportType,
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
                metricId = metricId,
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
    abstract suspend fun insert(metric: DbReport): Long

    /**
     * Upsert metric metadata (only change if different).
     *
     * @return ID.
     */
    @Transaction
    protected open suspend fun insertOrUpdateIfNeeded(metadata: DbMetricMetadata): Long {
        val existing = getMetadata(reportType = metadata.reportType, eventName = metadata.eventName)
        // Equals won't match unless we copy over the ID (we only care about the properties matching).
        if (existing?.equals(metadata.copy(id = existing.id)) == true) {
            return existing.id
        }
        val rowId = insertOrReplace(metadata)
        return getMetricMetadataIdForRowId(rowId)
    }

    @Query("SELECT id FROM metric_metadata WHERE rowid = :rowId")
    protected abstract suspend fun getMetricMetadataIdForRowId(rowId: Long): Long

    @Query("SELECT * FROM metric_metadata WHERE reportType = :reportType AND eventName = :eventName")
    protected abstract suspend fun getMetadata(
        reportType: String,
        eventName: String,
    ): DbMetricMetadata?

    @VisibleForTesting
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(metadata: DbMetricMetadata): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(metric: DbMetricValue): Long

    private suspend fun calculateAggregations(
        metric: DbMetricMetadata,
        reportStartMs: Long,
        reportEndMs: Long,
    ): Map<String, JsonPrimitive> {
        val metrics = mutableMapOf<String, JsonPrimitive>()
        val aggs = metric.aggregations.aggregations
        val key = metric.eventName
        val numericAggs = aggs.filter { it is NumericAgg }
        if (numericAggs.isNotEmpty()) {
            val results = getNumericAggregations(metric.id)
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
            val results = getLatestAggregations(metric.id)
            metrics["$key.latest"] = results.jsonValue()
        }
        if (aggs.any { it == StateAgg.TIME_PER_HOUR || it == StateAgg.TIME_TOTALS }) {
            val values = getMetricValuesFlow(metricId = metric.id, pageSize = 250, bufferSize = 500)
            val timeInStateMs = mutableMapOf<String, Long>()
            var last: DbMetricValue? = null
            values.collect { d ->
                last?.let { l ->
                    val state = l.jsonValue().contentOrNull ?: return@let
                    timeInStateMs[state] = timeInStateMs.getOrDefault(state, 0) + (d.timestampMs - l.timestampMs)
                }
                last = d
            }
            // End of report
            last?.let { l ->
                val state = l.jsonValue().contentOrNull ?: return@let
                timeInStateMs[state] = timeInStateMs.getOrDefault(state, 0) + (reportEndMs - l.timestampMs)
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
    open suspend fun finishReport(
        reportType: String,
        endTimestampMs: Long,
        hrtFile: File?,
    ): CustomReport {
        val dbReport = getReport(reportType = reportType)
        if (dbReport == null) {
            Logger.i("Didn't find report in database for $reportType")
            return CustomReport(
                report = MetricReport(
                    version = METRICS_VERSION,
                    startTimestampMs = endTimestampMs,
                    endTimestampMs = endTimestampMs,
                    reportType = reportType,
                    metrics = emptyMap(),
                    internalMetrics = emptyMap(),
                ),
                hrt = hrtFile,
            )
        }
        val startTimestampMs = dbReport.startTimeMs
        val metricMetadata = getMetricMetadata(reportType = reportType)
        val metrics = mutableMapOf<String, JsonPrimitive>()
        val internalMetrics = mutableMapOf<String, JsonPrimitive>()
        for (metric in metricMetadata) {
            val aggregations =
                calculateAggregations(metric, reportStartMs = startTimestampMs, reportEndMs = endTimestampMs)
            if (metric.internal) {
                internalMetrics.putAll(aggregations)
            } else {
                metrics.putAll(aggregations)
            }
        }
        // TODO carry-over values
        val report = MetricReport(
            version = METRICS_VERSION,
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs,
            reportType = reportType,
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
                    json.name("report_type").value(reportType)

                    json.name("producer").beginObject()
                    json.name("version").value("1")
                    json.name("id").value("bort")
                    json.endObject()

                    json.name("rollups").beginArray()
                    metricMetadata.forEach { metadata ->
                        val values = getMetricValuesFlow(
                            metricId = metadata.id,
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
                            json.name("t").value(d.timestampMs)
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
        deleteMetricValues(reportType)
        deleteMetricMetadata(reportType)
        deleteReport(reportType)
        return CustomReport(
            report = report,
            hrt = hrtFile,
        )
    }

    @Query(
        """SELECT
        MIN(v.numberVal) as min,
        MAX(v.numberVal) as max,
        SUM(v.numberVal) as sum,
        AVG(v.numberVal) as mean,
        COUNT(v.numberVal) as count
        FROM metric_values v
        WHERE v.metricId = :metricId""",
    )
    protected abstract suspend fun getNumericAggregations(metricId: Long): DbNumericAggs

    @Query(
        """SELECT
        *
        FROM metric_values v
        WHERE v.metricId = :metricId
        ORDER BY v.timestampMs DESC
        LIMIT 1""",
    )
    protected abstract suspend fun getLatestAggregations(metricId: Long): DbMetricValue

    @Query("SELECT * FROM reports WHERE type = :reportType")
    protected abstract suspend fun getReport(reportType: String): DbReport?

    @Query("SELECT * FROM metric_metadata WHERE reportType = :reportType")
    protected abstract suspend fun getMetricMetadata(reportType: String): List<DbMetricMetadata>

    @Query("SELECT * FROM metric_values")
    protected abstract suspend fun getAllMetricsDeleteThis(): List<DbMetricValue>

    /**
     * Create a flow of metric values. This avoids loading them all into memory at once (the emitter will block until
     * the buffer is consumed by the caller).
     */
    private fun getMetricValuesFlow(
        metricId: Long,
        pageSize: Long,
        bufferSize: Int,
    ): Flow<DbMetricValue> = flow {
        var offset = 0L
        while (true) {
            val values = getMetricValuesPage(metricId = metricId, limit = pageSize, offset = offset)
            if (values.isEmpty()) break
            values.forEach {
                emit(it)
            }
            offset += pageSize
        }
    }.buffer(capacity = bufferSize, onBufferOverflow = BufferOverflow.SUSPEND)

    @Query(
        "SELECT * FROM metric_values WHERE metricId = :metricId " +
            "ORDER BY timestampMs ASC LIMIT :limit OFFSET :offset",
    )
    protected abstract suspend fun getMetricValuesPage(
        metricId: Long,
        limit: Long,
        offset: Long,
    ): List<DbMetricValue>

    @Query(
        "DELETE FROM metric_values WHERE metricId in " +
            "(SELECT id FROM metric_metadata WHERE reportType = :reportType)",
    )
    protected abstract suspend fun deleteMetricValues(reportType: String): Int

    @Query("DELETE FROM metric_metadata WHERE reportType = :reportType")
    protected abstract suspend fun deleteMetricMetadata(reportType: String): Int

    @Query("DELETE FROM reports WHERE type = :reportType")
    protected abstract suspend fun deleteReport(reportType: String): Int

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
