package com.memfault.bort.metrics.custom

import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.battery.BATTERY_CHARGING_METRIC
import com.memfault.bort.battery.BATTERY_LEVEL_METRIC
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
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.StartReport
import com.memfault.bort.settings.DailyHeartbeatEnabled
import com.memfault.bort.settings.HighResMetricsEnabled
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
    suspend fun add(metric: MetricValue): Long
    suspend fun start(start: StartReport): Long
    suspend fun finish(finish: FinishReport): Long
    suspend fun startedHeartbeatOrNull(): DbReport?

    suspend fun collectHeartbeat(
        endTimestampMs: Long,
        forceEndAllReports: Boolean = false,
    ): CustomReport
}

private val SYNC_METRICS = setOf(
    "sync_memfault_failure",
    "sync_memfault_successful",
    "sync_failure",
    "sync_successful",
)

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
) : CustomMetrics {
    private val mutex = Mutex()

    private val dbReportBuilder = DbReportBuilder { report ->
        report.copy(
            softwareVersion = deviceInfoProvider.getDeviceInfo().softwareVersion,
        )
    }

    override suspend fun add(metric: MetricValue): Long = mutex.withLock {
        if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName == OPERATIONAL_CRASHES_METRIC_KEY
        ) {
            db.dao().insertAllReports(metric, dbReportBuilder)
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName == CONNECTIVITY_TYPE_METRIC
        ) {
            db.dao().insertAllReports(metric, dbReportBuilder)
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName in SYNC_METRICS
        ) {
            db.dao().insertAllReports(metric, dbReportBuilder)
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName in BATTERY_METRICS
        ) {
            db.dao().insert(metric, dbReportBuilder, overrideReportType = DAILY_HEARTBEAT_REPORT_TYPE)
        } else if (metric.reportType == SESSION_REPORT_TYPE) {
            db.dao().insertSessionMetric(metric)
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE) {
            db.dao().insert(metric, dbReportBuilder)
        } else {
            -1
        }
    }

    override suspend fun start(start: StartReport): Long = mutex.withLock {
        when (start.reportType) {
            SESSION_REPORT_TYPE -> {
                val allowedByRateLimit = sessionMetricsTokenBucketStore.takeSimple(tag = "session")
                if (allowedByRateLimit) {
                    db.dao().startWithLatestMetricValues(
                        startReport = start,
                        hourlyHeartbeatReportType = HOURLY_HEARTBEAT_REPORT_TYPE,
                        latestMetricKeys = listOf(CONNECTIVITY_TYPE_METRIC),
                        dbReportBuilder = dbReportBuilder,
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

    override suspend fun finish(finish: FinishReport): Long = mutex.withLock {
        if (finish.reportType == SESSION_REPORT_TYPE) {
            db.dao().finish(finish)
        } else {
            -1L
        }
    }

    override suspend fun startedHeartbeatOrNull(): DbReport? =
        db.dao().singleStartedReport(reportType = HOURLY_HEARTBEAT_REPORT_TYPE)

    override suspend fun collectHeartbeat(
        endTimestampMs: Long,
        forceEndAllReports: Boolean,
    ): CustomReport = mutex.withLock {
        val report = db.dao().collectHeartbeat(
            dailyHeartbeatReportType = if (dailyHeartbeatEnabled()) {
                DAILY_HEARTBEAT_REPORT_TYPE
            } else {
                null
            },
            endTimestampMs = endTimestampMs,
            hrtFileFactory = if (highResMetricsEnabled()) {
                HrtFileFactory {
                    temporaryFileFactory.createTemporaryFile(suffix = "hrt").useFile { file, preventDeletion ->
                        preventDeletion()
                        file
                    }
                }
            } else {
                null
            },
            calculateDerivedAggregations = { reportType, dbReport, endTimestamp, metrics, internalMetrics ->
                derivedAggregations.flatMap { aggregation ->
                    aggregation.calculate(
                        reportType = reportType,
                        startTimestampMs = dbReport,
                        endTimestampMs = endTimestamp,
                        metrics = metrics,
                        internalMetrics = internalMetrics,
                    )
                }
            },
            dailyHeartbeatReportMetricsForSessions = BATTERY_METRICS.toList(),
            dbReportBuilder = dbReportBuilder,
            forceEndAllReports = forceEndAllReports,
        )

        report.copy(
            hourlyHeartbeatReport = report.hourlyHeartbeatReport.filterAndRenameMetrics(Hourly),
            dailyHeartbeatReport = report.dailyHeartbeatReport?.filterAndRenameMetrics(Daily),
            sessions = report.sessions.map { it.filterAndRenameMetrics(Session) },
        )
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
)

enum class ReportType {
    Hourly,
    Daily,
    Session,
}
