package com.memfault.bort.metrics.database

import android.util.JsonWriter
import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.memfault.bort.json.useJsonWriter
import com.memfault.bort.metrics.AggregateMetricFilter
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.database.DerivedAggregation.Companion.asMetrics
import com.memfault.bort.reporting.AggregationType
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.FinishReport
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.StartReport
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration
import kotlin.time.toKotlinDuration

const val DAILY_HEARTBEAT_REPORT_TYPE = "Daily-Heartbeat"
const val HOURLY_HEARTBEAT_REPORT_TYPE = "Heartbeat"
const val SESSION_REPORT_TYPE = "Session"

data class ReportAggregations(
    val reports: List<DbReport>,
    val metadata: List<DbMetricMetadata>,
    val metrics: Map<String, JsonPrimitive>,
    val internalMetrics: Map<String, JsonPrimitive>,
)

/**
 * There are currently 3 kinds of reports in the database:
 *  * [HOURLY_HEARTBEAT_REPORT_TYPE]
 *  * [SESSION_REPORT_TYPE]
 *  * [DAILY_HEARTBEAT_REPORT_TYPE]
 *
 * [HOURLY_HEARTBEAT_REPORT_TYPE] are about 1-2 hours in length. There is only 1 active hourly report at any time.
 * When the hourly metric report is generated, this report is deleted, unless there are carryOver values to bring over
 * for the next hour, or unless daily reports are enabled.
 *
 * [SESSION_REPORT_TYPE] are about 1-2 hours in length. There can be multiple sessions with different [DbReport.name]s
 * active at the same time, but only 1 session with the same name can be active at once. A session is active if it
 * has a null [DbReport.endTimeMs]. Calling [finish] on a session will set the [DbReport.endTimeMs]. When the session
 * report is generated, the report is deleted entirely.
 *
 * To generate a Daily Heartbeat, we simply wait for 24 hours worth of [HOURLY_HEARTBEAT_REPORT_TYPE]s
 * to be stored in the database, and then we generate the hourly report as well as the daily report using all
 * 24 hourly reports.
 *
 * The [DAILY_HEARTBEAT_REPORT_TYPE] that's stored in the database is currently only used for generating metrics to
 * be added to sessions. Instead of storing metrics in every session, we can store it once under the
 * [DAILY_HEARTBEAT_REPORT_TYPE], and then include the them when generating the Session report. This avoids creating
 * duplicate copies of the metric metadata and values in every session, but means we have to manage metrics that
 * can't get deleted until overlapping sessions are also deleted.
 */
@Dao
abstract class MetricsDao : MetricReportsDao, MetricMetadataDao, MetricValuesDao {
    @Transaction
    open suspend fun insertAllReports(
        metric: MetricValue,
    ): Long {
        var inserts = 0L

        inserts += if (insert(metric) != -1L) 1L else 0L

        inserts += insertAllSessions(
            metric = metric,
        ).takeIf { it != -1L } ?: 0L

        return inserts
    }

    @Transaction
    open suspend fun insertAllSessions(
        metric: MetricValue,
    ): Long {
        var inserts = 0L

        getReports(reportType = SESSION_REPORT_TYPE).forEach { session ->
            val metricTime = metric.timeMs

            val appliesToSession = if (session.endTimeMs != null) {
                session.startTimeMs <= metricTime && metricTime <= session.endTimeMs
            } else {
                session.startTimeMs <= metricTime
            }

            if (appliesToSession) {
                val metadataId = insertOrUpdateMetadataIfNeeded(
                    DbMetricMetadata(
                        reportId = session.id,
                        eventName = metric.eventName,
                        metricType = metric.metricType,
                        dataType = metric.dataType,
                        carryOver = metric.carryOverValue,
                        aggregations = Aggs(metric.aggregations),
                        internal = metric.internal,
                    ),
                )
                inserts += if (insert(
                        DbMetricValue(
                            metadataId = metadataId,
                            version = metric.version,
                            timestampMs = metric.timeMs,
                            stringVal = metric.stringVal,
                            numberVal = metric.numberVal,
                            boolVal = metric.boolVal,
                        ),
                    ) != 1L
                ) {
                    1L
                } else {
                    0L
                }
            }
        }

        return inserts
    }

    @Transaction
    open suspend fun insertSessionMetric(metric: MetricValue): Long {
        val sessionName = metric.reportName
        if (sessionName == null) {
            return -1
        }

        val session = singleStartedReport(
            reportType = metric.reportType,
            reportName = sessionName,
        )
        if (session == null) {
            return -1
        }

        val metadataId = insertOrUpdateMetadataIfNeeded(
            DbMetricMetadata(
                reportId = session.id,
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

    @Transaction
    open suspend fun insert(
        metric: MetricValue,
        overrideReportType: String? = null,
    ): Long {
        return insertOrCreateReport(
            reportType = overrideReportType ?: metric.reportType,
            reportName = metric.reportName,
            reportStartTimestampMs = metric.timeMs,
            eventTimestampMs = metric.timeMs,
            eventName = metric.eventName,
            metricType = metric.metricType,
            dataType = metric.dataType,
            carryOver = metric.carryOverValue,
            aggs = metric.aggregations,
            internal = metric.internal,
            version = metric.version,
            stringVal = metric.stringVal,
            numberVal = metric.numberVal,
            boolVal = metric.boolVal,
        )
    }

    private suspend fun insertOrCreateReport(
        reportType: String,
        reportName: String?,
        reportStartTimestampMs: Long,
        eventTimestampMs: Long,
        eventName: String,
        metricType: MetricType,
        dataType: DataType,
        carryOver: Boolean,
        aggs: List<AggregationType>,
        internal: Boolean,
        version: Int,
        stringVal: String?,
        numberVal: Double?,
        boolVal: Boolean?,
    ): Long {
        val reportId = insertOrGetStartedReport(
            DbReport(
                type = reportType,
                name = reportName,
                startTimeMs = reportStartTimestampMs,
            ),
        )
        val metadataId = insertOrUpdateMetadataIfNeeded(
            DbMetricMetadata(
                reportId = reportId,
                eventName = eventName,
                metricType = metricType,
                dataType = dataType,
                carryOver = carryOver,
                aggregations = Aggs(aggs),
                internal = internal,
            ),
        )
        return insert(
            DbMetricValue(
                metadataId = metadataId,
                version = version,
                timestampMs = eventTimestampMs,
                stringVal = stringVal,
                numberVal = numberVal,
                boolVal = boolVal,
            ),
        )
    }

    @Transaction
    open suspend fun startWithLatestMetricValues(
        startReport: StartReport,
        hourlyHeartbeatReportType: String = HOURLY_HEARTBEAT_REPORT_TYPE,
        latestMetricKeys: List<String>,
    ): Long {
        val reportId = insertOrGetStartedReport(
            DbReport(
                type = startReport.reportType,
                name = startReport.reportName,
                startTimeMs = startReport.timestampMs,
            ),
        )

        val hourlyHeartbeatDbReport = singleStartedReport(reportType = hourlyHeartbeatReportType)
        if (hourlyHeartbeatDbReport != null) {
            latestMetricKeys.forEach { key ->
                val metadata = getMetadata(reportId = hourlyHeartbeatDbReport.id, eventName = key)
                if (metadata != null) {
                    val metric = getLatestMetricValue(
                        metadataIds = listOf(metadata.id),
                        endTimestampMs = startReport.timestampMs,
                    )

                    if (metric != null) {
                        val metadataId = insertOrUpdateMetadataIfNeeded(
                            DbMetricMetadata(
                                reportId = reportId,
                                eventName = metadata.eventName,
                                metricType = metadata.metricType,
                                dataType = metadata.dataType,
                                carryOver = metadata.carryOver,
                                aggregations = metadata.aggregations,
                                internal = metadata.internal,
                            ),
                        )

                        insert(
                            DbMetricValue(
                                metadataId = metadataId,
                                version = metric.version,
                                // Use the current timestamp.
                                timestampMs = startReport.timestampMs,
                                stringVal = metric.stringVal,
                                numberVal = metric.numberVal,
                                boolVal = metric.boolVal,
                            ),
                        )
                    } else {
                        Logger.e("Requested insert latest ${metadata.eventName} but no value found in range")
                    }
                }
            }
        }

        return reportId
    }

    @Transaction
    open suspend fun finish(finishReport: FinishReport): Long {
        val report = singleStartedReport(
            reportType = finishReport.reportType,
            reportName = finishReport.reportName,
        )

        if (report == null) {
            return -1
        }

        return updateReportEndTimestamp(report.id, endTimestampMs = finishReport.timestampMs).toLong()
    }

    /**
     * Insert report. If a report of the same type and name already exists, return it instead.
     *
     * @return ID.
     */
    @Transaction
    protected open suspend fun insertOrGetStartedReport(report: DbReport): Long {
        val reportOrNull = if (report.name != null) {
            singleStartedReport(reportType = report.type, reportName = report.name)
        } else {
            singleStartedReport(reportType = report.type)
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
        return if (existing != null) {
            insertOrReplace(metadata.copy(id = existing.id))
        } else {
            insertOrReplace(metadata)
        }
    }

    private suspend fun calculateAggregations(
        metadata: List<DbMetricMetadata>,
        aggs: List<AggregationType>,
        carryOver: Boolean,
        key: String,
        reportStartMs: Long,
        reportEndMs: Long,
    ): Map<String, JsonPrimitive> {
        val uniqueMetadataKeys = metadata.mapTo(mutableSetOf()) { it.eventName }
        if (uniqueMetadataKeys.size != 1) {
            Logger.w("Tried to calculate aggregations across different metadata keys: $uniqueMetadataKeys")
            // This shouldn't be possible, but usually better to no-op than crash.
            return emptyMap()
        }

        val metrics = mutableMapOf<String, JsonPrimitive>()
        val numericAggs = aggs.filterIsInstance<NumericAgg>()
        val metadataIds = metadata.map { it.id }.distinct()
        if (numericAggs.isNotEmpty()) {
            val results = getNumericAggregations(
                metadataIds = metadataIds,
                startTimestampMs = reportStartMs,
                endTimestampMs = reportEndMs,
            )
            for (numericAgg in numericAggs) {
                when (numericAgg) {
                    NumericAgg.COUNT -> metrics["$key.count"] = JsonPrimitive(results.count)
                    NumericAgg.LATEST_VALUE -> Unit // handled below
                    NumericAgg.MAX -> metrics["$key.max"] = JsonPrimitive(results.max)
                    NumericAgg.MEAN -> metrics["$key.mean"] = JsonPrimitive(results.mean)
                    NumericAgg.MIN -> metrics["$key.min"] = JsonPrimitive(results.min)
                    NumericAgg.SUM -> metrics["$key.sum"] = JsonPrimitive(results.sum)
                    NumericAgg.VALUE_DROP -> Unit // handled below
                }
            }
        }
        if (aggs.any { it == NumericAgg.LATEST_VALUE || it == StateAgg.LATEST_VALUE }) {
            // If there is carryOver, then the metric that's "carried over" won't have a timestamp that's within
            // the bounds of the report, because it has the original timestamp when it was created (so it can be
            // eventually expired and deleted). We trust that the logic to carry over values properly cleans up
            // other values before the report start, but since we're only looking for the latest value anyways, it
            // shouldn't be a big deal.
            val results = if (carryOver) {
                getLatestMetricValue(
                    metadataIds = metadataIds,
                    endTimestampMs = reportEndMs,
                )
            } else {
                getLatestMetricValueInRange(
                    metadataIds = metadataIds,
                    startTimestampMs = reportStartMs,
                    endTimestampMs = reportEndMs,
                )
            }
            if (results != null) {
                metrics["$key.latest"] = results.jsonValue()
            } else {
                Logger.e("Requested LATEST_VALUE but no value found in range")
            }
        }
        val valueAggregations = listOfNotNull(
            if (aggs.any { it == StateAgg.TIME_PER_HOUR || it == StateAgg.TIME_TOTALS }) {
                TimeInStateValueAggregations(aggs, key)
            } else {
                null
            },
            if (aggs.any { it == NumericAgg.VALUE_DROP }) {
                ValueDropValueAggregations(key)
            } else {
                null
            },
        )
        if (valueAggregations.isNotEmpty()) {
            val values = getMetricValuesFlow(
                metadataIds = metadataIds,
                pageSize = 250,
                bufferSize = 500,
                endTimestampMs = reportEndMs,
            )
            // This complex bit of logic makes it so that we call [valueAggregation.onEach] with the latest value
            // that is <= reportStartMs, and then again for metrics with timestamp t, where
            // reportStartMs < t <= reportEndMs.
            var last: DbMetricValue? = null
            values.collect { d ->
                if (d.timestampMs > reportStartMs) {
                    last?.let {
                        valueAggregations.forEach { valueAggregation ->
                            valueAggregation.onEach(reportStartMs, reportEndMs, it)
                        }
                    }
                }
                last = d
            }
            last?.let {
                valueAggregations.forEach { valueAggregation ->
                    valueAggregation.onEach(reportStartMs, reportEndMs, it)
                }
            }
            valueAggregations.forEach { valueAggregation ->
                metrics.putAll(valueAggregation.finish(reportStartMs, reportEndMs))
            }
        }
        return metrics
    }

    private suspend fun calculateAggregations(
        dbReports: List<DbReport>,
        metricKeysFilter: List<String>?,
        startTimestampMs: Long,
        endTimestampMs: Long,
    ): ReportAggregations {
        val reportIds = dbReports.map { it.id }

        val metrics = mutableMapOf<String, JsonPrimitive>()
        val internalMetrics = mutableMapOf<String, JsonPrimitive>()

        val metricMetadata = getMetricMetadata(reportIds = reportIds)

        val uniqueReportIds = dbReports.associateBy { it.id }
        val uniqueMetricKeys = metricMetadata.groupBy { it.eventName }
            .filterKeys { key -> metricKeysFilter == null || key in metricKeysFilter }

        // For each metric key, calculate aggregations based off of every passed in report. In most cases, only 1
        // report is passed in, but for daily heartbeats, there could be 24 hours worth of reports (24).
        for ((metricKey, commonMetadata) in uniqueMetricKeys) {
            // If there is more than one metadata, then use the latest metadata as the "canonical" metadata for the
            // (daily) aggregation. Note that there may be situations where there are fewer metadata than reports,
            // if some reports don't contain that metric.
            val commonMetadataSorted = commonMetadata
                .sortedBy { metadata -> uniqueReportIds[metadata.reportId]?.endTimeMs }
            val latestMetadata = commonMetadataSorted.last()

            // Make sure that the start and end timestamps are correct for all 3 of hourly heartbeats, daily
            // heartbeats, and sessions.
            val aggregations = calculateAggregations(
                metadata = commonMetadataSorted,
                aggs = latestMetadata.aggregations.aggregations,
                carryOver = latestMetadata.carryOver,
                key = metricKey,
                reportStartMs = startTimestampMs,
                reportEndMs = endTimestampMs,
            )
            if (latestMetadata.internal) {
                internalMetrics.putAll(aggregations)
            } else {
                metrics.putAll(aggregations)
            }
        }

        return ReportAggregations(
            reports = dbReports,
            metadata = metricMetadata,
            metrics = metrics,
            internalMetrics = internalMetrics,
        )
    }

    /**
     * Converts a list of [DbReport]s into a single [MetricReport]. The list often has only 1 element, but may
     * contain multiple reports, in the case of daily heartbeats where we merge 24 hours worth of reports into 1.
     * [DbMetricValue]s are always tied to a single [DbReport] for the hourly heartbeat, but for daily heartbeats, we
     * can simply pass all [DbReport]s in the 24 hour period to produce a single [MetricReport]. This allows us to
     * re-use the existing aggregation logic without re-structuring metric associations.
     */
    private suspend fun produceMetricReport(
        dbReports: List<DbReport>,
        endTimestampMs: Long,
        calculateDerivedAggregations: CalculateDerivedAggregations,
        // If non-null, include these metrics from the daily heartbeat report type into the produced metric report.
        dailyHeartbeatReportMetrics: List<String>? = null,
        consumeMetadata: suspend (
            dbReport: DbReport,
            dbMetadata: List<DbMetricMetadata>,
            derivedAggregations: List<DerivedAggregation>,
        ) -> Unit,
    ): MetricReport {
        if (dbReports.isEmpty()) {
            Logger.w("Tried to produceMetricReport but didn't pass any reports")
            // Should never pass an empty list here, but just in case.
            return MetricReport(
                version = METRICS_VERSION,
                startTimestampMs = endTimestampMs,
                endTimestampMs = endTimestampMs,
                reportType = HOURLY_HEARTBEAT_REPORT_TYPE,
                reportName = null,
                metrics = emptyMap(),
                internalMetrics = emptyMap(),
            )
        }
        val startReport = dbReports.minBy { it.startTimeMs }
        val startTimestampMs = startReport.startTimeMs

        val metrics = mutableMapOf<String, JsonPrimitive>()
        val internalMetrics = mutableMapOf<String, JsonPrimitive>()

        val reportAggregations = calculateAggregations(
            dbReports = dbReports,
            metricKeysFilter = null,
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs,
        )

        metrics.putAll(reportAggregations.metrics)
        internalMetrics.putAll(reportAggregations.internalMetrics)

        // If [dailyHeartbeatReportType] is not null, then calculate aggregations from the daily heartbeat report type
        // where their timestamps overlap the start and end timestamps.
        if (dailyHeartbeatReportMetrics != null) {
            val dailyHeartbeatReport = singleStartedReport(reportType = DAILY_HEARTBEAT_REPORT_TYPE)
            if (dailyHeartbeatReport != null) {
                val extraDailyReportAggregations = calculateAggregations(
                    dbReports = listOf(dailyHeartbeatReport),
                    metricKeysFilter = dailyHeartbeatReportMetrics,
                    startTimestampMs = startTimestampMs,
                    endTimestampMs = endTimestampMs,
                )

                metrics.putAll(extraDailyReportAggregations.metrics)
                internalMetrics.putAll(extraDailyReportAggregations.internalMetrics)
            }
        }

        val derivedAggregations =
            calculateDerivedAggregations.calculate(startTimestampMs, endTimestampMs, metrics, internalMetrics)

        // We currently only call and use consumeMetadata for HRT, which is only supported for hourly heartbeats.
        // Perhaps there's a better way to model this with Sessions and DerivedAggregations, because we are
        // calling this unnecessarily for Daily Heartbeats.
        if (dbReports.size == 1) {
            consumeMetadata(startReport, reportAggregations.metadata, derivedAggregations)
        }

        return MetricReport(
            version = METRICS_VERSION,
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs,
            reportType = startReport.type,
            reportName = startReport.name,
            metrics = derivedAggregations.asMetrics(internal = false) +
                AggregateMetricFilter.filterAndRenameMetrics(metrics, internal = false),
            internalMetrics = derivedAggregations.asMetrics(internal = true) +
                AggregateMetricFilter.filterAndRenameMetrics(internalMetrics, internal = true),
        )
    }

    private suspend fun writeHrtRollup(
        json: JsonWriter,
        dbReport: DbReport,
        dbMetadata: DbMetricMetadata,
        dbMetricValues: Flow<DbMetricValue>,
    ) {
        var writtenMetadata = false
        dbMetricValues.collect { d ->
            if (!writtenMetadata) {
                json.beginObject()
                json.name("metadata").beginObject()
                json.name("string_key").value(dbMetadata.eventName)
                json.name("metric_type").value(dbMetadata.metricType.value)
                json.name("data_type").value(dbMetadata.dataType.value)
                json.name("internal").value(dbMetadata.internal)
                json.endObject()
                json.name("data").beginArray()
                writtenMetadata = true
            }

            json.beginObject()
            val timestamp = d.timestampMs.coerceAtLeast(dbReport.startTimeMs)
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

    private suspend fun writeHrtReport(
        json: JsonWriter,
        dbReport: DbReport,
        dbMetadata: List<DbMetricMetadata>,
        endTimestampMs: Long,
        derivedAggregations: List<DerivedAggregation>,
    ) {
        json.beginObject()
        json.name("schema_version").value(1)
        json.name("start_time").value(dbReport.startTimeMs)
        json.name("duration_ms").value(endTimestampMs - dbReport.startTimeMs)
        json.name("report_type").value(dbReport.type)

        json.name("producer").beginObject()
        json.name("version").value("1")
        json.name("id").value("bort")
        json.endObject()

        json.name("rollups").beginArray()
        dbMetadata.forEach { metadata ->
            writeHrtRollup(
                json = json,
                dbReport = dbReport,
                dbMetadata = metadata,
                dbMetricValues = getMetricValuesFlow(
                    metadataIds = listOf(metadata.id),
                    pageSize = PAGE_SIZE,
                    bufferSize = BUFFER_SIZE,
                    endTimestampMs = endTimestampMs,
                ),
            )
        }

        derivedAggregations.forEach { derivedAggregation ->
            writeHrtRollup(
                json = json,
                dbReport = dbReport,
                dbMetadata = derivedAggregation.metadata,
                dbMetricValues = flowOf(derivedAggregation.value),
            )
        }
        json.endArray()
        json.endObject()
    }

    /**
     * Generates a [CustomReport], which contains the hourly, daily, and session [MetricReport]s.
     *
     * Every time this method is called, we produce a single "hourly" [MetricReport]. Note that this hourly report
     * might not actually contain a full hour's worth of metrics. We also write all finished sessions, each into
     * its own [MetricReport]. We also check whether daily reports are enabled. If yes, then we won't delete each
     * hourly report, and instead wait until we've accumulated 24 hours worth of hourly reports, before producing
     * a daily report in the [CustomReport.dailyHeartbeatReport] slot.
     */
    @Transaction
    open suspend fun collectHeartbeat(
        hourlyHeartbeatReportType: String = HOURLY_HEARTBEAT_REPORT_TYPE,
        dailyHeartbeatReportType: String? = null,
        endTimestampMs: Long,
        hrtFile: File?,
        calculateDerivedAggregations: CalculateDerivedAggregations = CalculateDerivedAggregations { _, _, _, _ ->
            emptyList()
        },
        dailyHeartbeatReportMetricsForSessions: List<String>? = null,
    ): CustomReport {
        val hourlyHeartbeatDbReport = singleStartedReport(reportType = hourlyHeartbeatReportType)
        val hourlyHeartbeatReport = if (hourlyHeartbeatDbReport == null) {
            Logger.i("Didn't find report in database for $hourlyHeartbeatReportType")
            MetricReport(
                version = METRICS_VERSION,
                startTimestampMs = endTimestampMs,
                endTimestampMs = endTimestampMs,
                reportType = HOURLY_HEARTBEAT_REPORT_TYPE,
                reportName = null,
                metrics = emptyMap(),
                internalMetrics = emptyMap(),
            )
        } else {
            // Always end the hourly report.
            updateReportEndTimestamp(hourlyHeartbeatDbReport.id, endTimestampMs = endTimestampMs)

            val hourlyReport = produceMetricReport(
                dbReports = listOf(hourlyHeartbeatDbReport),
                endTimestampMs = endTimestampMs,
                calculateDerivedAggregations = calculateDerivedAggregations,
                consumeMetadata = { dbReport, dbMetadata, derivedAggregations ->
                    hrtFile.useJsonWriter { jsonWriter ->
                        jsonWriter?.let {
                            writeHrtReport(
                                json = it,
                                dbReport = dbReport,
                                dbMetadata = dbMetadata,
                                endTimestampMs = endTimestampMs,
                                derivedAggregations = derivedAggregations,
                            )
                        }
                    }
                },
            )

            // Delete all values, metadata, and reports. If carryOver is enabled for a metric, then carry over its
            // latest value to the next report. CarryOvers are only kept for 3 days if they're not updated.
            val allCarryOverMetricMetadata = getMetricMetadataWithCarryOver(hourlyHeartbeatDbReport.id)

            val carryOverMetricValues = allCarryOverMetricMetadata
                .mapNotNull { metadata ->
                    val latestValue = getLatestMetricValue(
                        metadataIds = listOf(metadata.id),
                        endTimestampMs = endTimestampMs,
                    )

                    if (latestValue != null) {
                        if ((endTimestampMs - latestValue.timestampMs).milliseconds <= 3.days) {
                            metadata to latestValue
                        } else {
                            Logger.v("Requested carryOver for ${metadata.eventName} but value expired")
                            null
                        }
                    } else {
                        Logger.e("Requested carryOver for ${metadata.eventName} but no value found in range")
                        null
                    }
                }

            carryOverMetricValues.forEach { (metadata, value) ->
                insertOrCreateReport(
                    reportType = hourlyHeartbeatDbReport.type,
                    reportName = hourlyHeartbeatDbReport.name,
                    reportStartTimestampMs = endTimestampMs,
                    eventTimestampMs = value.timestampMs,
                    eventName = metadata.eventName,
                    metricType = metadata.metricType,
                    dataType = metadata.dataType,
                    carryOver = metadata.carryOver,
                    aggs = metadata.aggregations.aggregations,
                    internal = metadata.internal,
                    version = value.version,
                    stringVal = value.stringVal,
                    numberVal = value.numberVal,
                    boolVal = value.boolVal,
                )
            }

            hourlyReport
        }

        // Produces the daily heartbeat report. Note that daily heartbeats are produced using 24 hours worth of
        // hourly metrics, not from the [DAILY_HEARTBEAT_REPORT_TYPE] report type.
        val dailyHeartbeatReport = if (dailyHeartbeatReportType != null) {
            val hourlyHeartbeats = getEndedReports(reportType = HOURLY_HEARTBEAT_REPORT_TYPE)
                .sortedBy { it.endTimeMs }

            val hourlyStartTimeMs = hourlyHeartbeats.minByOrNull { it.startTimeMs }?.startTimeMs ?: 0
            val hourlyEndTimeMs = hourlyHeartbeats.maxByOrNull { it.endTimeMs ?: 0 }?.endTimeMs ?: 0
            if ((hourlyEndTimeMs - hourlyStartTimeMs).toDuration(MILLISECONDS) >= 24.hours) {
                // Daily heartbeats work by reading all 24 hours worth of hourly heartbeats. By passing in all
                // 24 hourly heartbeat reports into the same function, we can reuse the [produceMetricReport]
                // function without much modification.
                val dailyReport = produceMetricReport(
                    dbReports = hourlyHeartbeats,
                    endTimestampMs = endTimestampMs,
                    calculateDerivedAggregations = calculateDerivedAggregations,
                    consumeMetadata = { _, _, _ -> },
                )

                // Once we're done producing the daily MetricReport, delete all its report and data.
                hourlyHeartbeats.forEach { report ->
                    deleteMetricValues(reportId = report.id)
                    deleteMetricMetadata(reportId = report.id)
                    deleteReport(reportId = report.id)
                }

                dailyReport
            } else {
                null
            }
        } else {
            // If the daily heartbeat is disabled, finished hourly heartbeats aren't used after the metric report is
            // produced, so make sure to delete them.
            getEndedReports(reportType = HOURLY_HEARTBEAT_REPORT_TYPE)
                .forEach { report ->
                    deleteMetricValues(reportId = report.id)
                    deleteMetricMetadata(reportId = report.id)
                    deleteReport(reportId = report.id)
                }

            null
        }

        // Cleanup Sessions.
        expireSessions(endTimestampMs = endTimestampMs)

        val sessionReports = getEndedReports(reportType = SESSION_REPORT_TYPE)
            .map { dbSession ->
                val sessionEndTimeMs = dbSession.endTimeMs
                checkNotNull(sessionEndTimeMs) {
                    "endTimeMs must not be null, is the query correct?"
                }

                val sessionReport = produceMetricReport(
                    dbReports = listOf(dbSession),
                    endTimestampMs = sessionEndTimeMs,
                    calculateDerivedAggregations = calculateDerivedAggregations,
                    dailyHeartbeatReportMetrics = dailyHeartbeatReportMetricsForSessions,
                    consumeMetadata = { _, _, _ -> },
                )

                // Delete all values, metadata, and reports.
                deleteMetricValues(reportId = dbSession.id)
                deleteMetricMetadata(reportId = dbSession.id)
                deleteReport(reportId = dbSession.id)

                sessionReport
            }

        // Cleanup Daily Heartbeats.
        val expiryTimestampMs = Instant.ofEpochMilli(endTimestampMs).minus(Duration.ofDays(3))

        // Delete metric values that might be too old.
        val dailyHeartbeat = singleStartedReport(reportType = DAILY_HEARTBEAT_REPORT_TYPE)
        if (dailyHeartbeat != null) {
            deleteExpiredMetricValues(
                reportId = dailyHeartbeat.id,
                expiryTimestampMs = expiryTimestampMs.toEpochMilli(),
            )
        }

        // Delete metadata that have no values every time we detect them.
        deleteOrphanedMetricMetadata()

        // Delete reports with no metadata, that haven't been uploaded in 2 days. Hourly and Daily heartbeats should
        // always have values and metadata. Sessions without any metadata shouldn't be longer than 1 day anyways or
        // they'll be expired automatically.
        deleteExpiredOrphanedReports(expiryTimestampMs = expiryTimestampMs.toEpochMilli())

        return CustomReport(
            hourlyHeartbeatReport = hourlyHeartbeatReport,
            dailyHeartbeatReport = dailyHeartbeatReport,
            sessions = sessionReports,
            hrt = hrtFile,
        )
    }

    private suspend fun expireSessions(endTimestampMs: Long) {
        val expiredSessionIds = getStartedReports(reportType = SESSION_REPORT_TYPE)
            .filter { session ->
                Duration.between(Instant.ofEpochMilli(session.startTimeMs), Instant.ofEpochMilli(endTimestampMs))
                    .toKotlinDuration().inWholeHours >= 1.days.inWholeHours
            }
            .map { session -> session.id }

        updateReportEndTimestamps(expiredSessionIds, endTimestampMs)
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
        WHERE v.metadataId IN (:metadataIds)
        AND v.timestampMs >= :startTimestampMs
        AND v.timestampMs <= :endTimestampMs""",
    )
    protected abstract suspend fun getNumericAggregations(
        metadataIds: List<Long>,
        startTimestampMs: Long,
        endTimestampMs: Long,
    ): DbNumericAggs

    /**
     * Create a flow of metric values. This avoids loading them all into memory at once (the emitter will block until
     * the buffer is consumed by the caller).
     */
    private fun getMetricValuesFlow(
        metadataIds: List<Long>,
        pageSize: Long,
        bufferSize: Int,
        endTimestampMs: Long,
    ): Flow<DbMetricValue> = flow {
        var offset = 0L
        while (true) {
            val values = getMetricValuesPage(
                metadataIds = metadataIds,
                limit = pageSize,
                offset = offset,
                endTimestampMs = endTimestampMs,
            )
            if (values.isEmpty()) break
            values.forEach {
                emit(it)
            }
            offset += pageSize
        }
    }.buffer(capacity = bufferSize, onBufferOverflow = BufferOverflow.SUSPEND)

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
