package com.memfault.bort.metrics.custom

import androidx.room.withTransaction
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.battery.BATTERY_CHARGING_METRIC
import com.memfault.bort.battery.BATTERY_LEVEL_METRIC
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.connectivity.CONNECTIVITY_TYPE_METRIC
import com.memfault.bort.dagger.InjectSet
import com.memfault.bort.metrics.AggregateMetricFilter.filterAndRenameMetrics
import com.memfault.bort.metrics.CrashFreeHoursMetricLogger.Companion.OPERATIONAL_CRASHES_METRIC_KEY
import com.memfault.bort.metrics.custom.ReportType.Daily
import com.memfault.bort.metrics.custom.ReportType.Hourly
import com.memfault.bort.metrics.custom.ReportType.Session
import com.memfault.bort.metrics.database.CalculateDerivedAggregations
import com.memfault.bort.metrics.database.DAILY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.DbReport
import com.memfault.bort.metrics.database.DbReportBuilder
import com.memfault.bort.metrics.database.HOURLY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.HrtFileFactory
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.metrics.database.SESSION_REPORT_TYPE
import com.memfault.bort.reporting.FinishReport
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.StartReport
import com.memfault.bort.settings.CollectedData
import com.memfault.bort.settings.CollectionDecision
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.DailyHeartbeatEnabled
import com.memfault.bort.settings.HighResMetricsEnabled
import com.memfault.bort.settings.shouldCollect
import com.memfault.bort.tokenbucket.SessionMetrics
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Note that the software version isn't "changed" if it wasn't known to begin with (because it was null).
 */
suspend fun CustomMetrics.softwareVersionChanged(deviceSoftwareVersion: String): Boolean {
    val heartbeat = startedHeartbeatOrNull()
    return heartbeat != null && heartbeat.softwareVersion != deviceSoftwareVersion
}

interface CustomMetrics {
    /**
     * Returns the inserted row ID, [NOT_COLLECTED] if the current visibility level does not collect this metric, or
     * [NOT_INSERTED] if it was not written for any other reason.
     */
    suspend fun add(metric: MetricValue): Long
    suspend fun start(start: StartReport): Long
    suspend fun finish(finish: FinishReport): Long
    suspend fun startedHeartbeatOrNull(): DbReport?

    /**
     * Buffers heartbeat metrics recorded while [block] runs, writing them in one transaction instead of one
     * per value. Each commit is at least one flash page program.
     *
     * Buffered values keep their original timestamp, so aggregations are unaffected. Flushed before anything
     * reads them back ([collectHeartbeat], [start]) and when [block] throws; values still buffered when the
     * process dies are lost.
     */
    suspend fun <R> batchMetricWrites(block: suspend () -> R): R

    suspend fun collectHeartbeat(
        endTimestampMs: Long,
        endUptimeMs: Long,
        forceEndAllReports: Boolean = false,
    ): CustomReport

    companion object {
        const val NOT_COLLECTED = -2L

        const val NOT_INSERTED = -1L
    }
}

/** Returned for a buffered metric. Callers only check for [CustomMetrics.NOT_INSERTED]. */
private const val BATCHED = 1L

/** Bounds the buffer, in case a batch runs long. */
private const val MAX_BATCHED_METRICS = 500

private val SYNC_METRICS = setOf(
    "sync_memfault_failure",
    "sync_memfault_successful",
    "sync_failure",
    "sync_successful",
)

internal fun MetricValue.isDeviceAttribute(): Boolean = metricType == MetricType.PROPERTY

val BATTERY_METRICS = setOf(
    BATTERY_CHARGING_METRIC,
    BATTERY_LEVEL_METRIC,
)

/**
 * Abstraction layer between the [MetricsDb] and [MetricsDao] and the rest of the codebase. The goal of this class
 * is to not leak specific metric names into the [MetricsDb.dao].
 */
@Singleton
@ContributesBinding(SingletonComponent::class)
class RealCustomMetrics @Inject constructor(
    private val db: MetricsDb,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val dailyHeartbeatEnabled: DailyHeartbeatEnabled,
    private val highResMetricsEnabled: HighResMetricsEnabled,
    @SessionMetrics private val sessionMetricsTokenBucketStore: TokenBucketStore,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val derivedAggregations: InjectSet<CalculateDerivedAggregations>,
    private val getBootId: LinuxBootId,
    private val currentSamplingConfig: CurrentSamplingConfig,
) : CustomMetrics {
    private val dbReportBuilder = DbReportBuilder { report ->
        report.copy(
            softwareVersion = deviceInfoProvider.getDeviceInfo().softwareVersion,
        )
    }

    private suspend fun shouldCollect(
        reportType: String,
        isDeviceAttribute: Boolean = false,
    ): Boolean {
        val data = when {
            reportType == SESSION_REPORT_TYPE -> CollectedData.SESSION
            isDeviceAttribute -> CollectedData.DEVICE_PROPERTIES
            else -> CollectedData.METRICS
        }
        return currentSamplingConfig.get().shouldCollect(data) == CollectionDecision.FULL
    }

    /** Guarded by [batchLock], which is never held across a database write. */
    private val batchLock = Mutex()
    private val batchedMetrics = mutableListOf<MetricValue>()
    private var batchDepth = 0

    override suspend fun <R> batchMetricWrites(block: suspend () -> R): R {
        batchLock.withLock { batchDepth++ }
        try {
            return block()
        } finally {
            val batching = batchLock.withLock { --batchDepth > 0 }
            if (!batching) {
                flushBatchedMetrics()
            }
        }
    }

    /**
     * Writes every buffered metric in one transaction, with the lock released: metrics recorded mid-flush are
     * buffered for the next one rather than blocking their caller.
     */
    private suspend fun flushBatchedMetrics() {
        val metrics = batchLock.withLock {
            if (batchedMetrics.isEmpty()) {
                return
            }
            batchedMetrics.toList().also { batchedMetrics.clear() }
        }
        db.withTransaction {
            metrics.forEach { metric -> insert(metric) }
        }
    }

    override suspend fun add(metric: MetricValue): Long {
        if (!shouldCollect(metric.reportType, metric.isDeviceAttribute())) return CustomMetrics.NOT_COLLECTED

        // Only heartbeat metrics: other report types return NOT_INSERTED when there's nowhere to write them, and
        // the caller falls back to structuredlogd, which buffering would hide.
        if (metric.reportType != HOURLY_HEARTBEAT_REPORT_TYPE) {
            return insert(metric)
        }

        val buffered = batchLock.withLock {
            if (batchDepth == 0) {
                null
            } else {
                batchedMetrics += metric
                batchedMetrics.size
            }
        } ?: return insert(metric)

        if (buffered >= MAX_BATCHED_METRICS) {
            flushBatchedMetrics()
        }
        return BATCHED
    }

    private suspend fun insert(metric: MetricValue): Long =
        if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName == OPERATIONAL_CRASHES_METRIC_KEY
        ) {
            db.dao().insertAllReports(metric, dbReportBuilder, getBootId())
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName == CONNECTIVITY_TYPE_METRIC
        ) {
            db.dao().insertAllReports(metric, dbReportBuilder, getBootId())
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName in SYNC_METRICS
        ) {
            db.dao().insertAllReports(metric, dbReportBuilder, getBootId())
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName in BATTERY_METRICS
        ) {
            db.dao().insert(metric, dbReportBuilder, getBootId(), overrideReportType = DAILY_HEARTBEAT_REPORT_TYPE)
        } else if (metric.reportType == SESSION_REPORT_TYPE) {
            db.dao().insertSessionMetric(metric, getBootId())
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE) {
            db.dao().insert(metric, dbReportBuilder, getBootId())
        } else {
            CustomMetrics.NOT_INSERTED
        }

    override suspend fun start(start: StartReport): Long {
        // Reads back the latest heartbeat values, so flush first.
        flushBatchedMetrics()
        return when (start.reportType) {
            SESSION_REPORT_TYPE -> {
                val allowedByRateLimit = shouldCollect(start.reportType) &&
                    sessionMetricsTokenBucketStore.takeSimple(tag = "session")
                if (allowedByRateLimit) {
                    db.dao().startWithLatestMetricValues(
                        startReport = start,
                        hourlyHeartbeatReportType = HOURLY_HEARTBEAT_REPORT_TYPE,
                        latestMetricKeys = listOf(CONNECTIVITY_TYPE_METRIC),
                        dbReportBuilder = dbReportBuilder,
                        bootId = getBootId(),
                    )
                } else {
                    -1
                }
            }

            else -> {
                -1
            }
        }
    }

    override suspend fun finish(finish: FinishReport): Long =
        if (finish.reportType == SESSION_REPORT_TYPE) {
            db.dao().finish(finish)
        } else {
            -1L
        }

    override suspend fun startedHeartbeatOrNull(): DbReport? =
        db.dao().singleStartedReport(reportType = HOURLY_HEARTBEAT_REPORT_TYPE)

    override suspend fun collectHeartbeat(
        endTimestampMs: Long,
        endUptimeMs: Long,
        forceEndAllReports: Boolean,
    ): CustomReport {
        // Generated from the database, so flush first.
        flushBatchedMetrics()
        return db.dao()
            .collectHeartbeat(
                dailyHeartbeatReportType = if (dailyHeartbeatEnabled()) {
                    DAILY_HEARTBEAT_REPORT_TYPE
                } else {
                    null
                },
                endTimestampMs = endTimestampMs,
                hrtFileFactory = if (highResMetricsEnabled() &&
                    currentSamplingConfig.get().shouldCollect(CollectedData.HIGH_RES_TELEMETRY) ==
                    CollectionDecision.FULL
                ) {
                    HrtFileFactory {
                        temporaryFileFactory.createTemporaryFile(suffix = "hrt").useFile { file, preventDeletion ->
                            preventDeletion()
                            file
                        }
                    }
                } else {
                    null
                },
                calculateDerivedAggregations = {
                        reportType,
                        dbReport,
                        endTimestamp,
                        metrics,
                        internalMetrics,
                        startUptimeMs,
                        endUptimeMsNonShadow,
                    ->
                    derivedAggregations.flatMap { aggregation ->
                        aggregation.calculate(
                            reportType = reportType,
                            startTimestampMs = dbReport,
                            endTimestampMs = endTimestamp,
                            metrics = metrics,
                            internalMetrics = internalMetrics,
                            startUptimeMs = startUptimeMs,
                            endUptimeMs = endUptimeMsNonShadow,
                        )
                    }
                },
                dailyHeartbeatReportMetricsForSessions = BATTERY_METRICS.toList(),
                dbReportBuilder = dbReportBuilder,
                forceEndAllReports = forceEndAllReports,
                endUptimeMs = endUptimeMs,
                bootId = getBootId(),
            )
            .let { report ->
                report.copy(
                    hourlyHeartbeatReport = report.hourlyHeartbeatReport.filterAndRenameMetrics(Hourly),
                    dailyHeartbeatReport = report.dailyHeartbeatReport?.filterAndRenameMetrics(Daily),
                    sessions = report.sessions.map { it.filterAndRenameMetrics(Session) },
                )
            }
    }
}

data class CustomReport(
    val hourlyHeartbeatReport: MetricReport,
    val dailyHeartbeatReport: MetricReport?,
    val sessions: List<MetricReport> = emptyList(),
)

data class MetricReport(
    val version: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val reportType: String,
    val reportName: String? = null,
    val metrics: Map<String, JsonPrimitive>,
    val internalMetrics: Map<String, JsonPrimitive> = mapOf(),
    val hrt: File?,
    val softwareVersion: String?,
    val bootId: String,
    val startUptimeMs: Long,
    val endUptimeMs: Long,
)

enum class ReportType {
    Hourly,
    Daily,
    Session,
}
