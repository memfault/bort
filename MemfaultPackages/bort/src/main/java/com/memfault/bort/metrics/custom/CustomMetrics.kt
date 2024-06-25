package com.memfault.bort.metrics.custom

import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.connectivity.CONNECTIVITY_TYPE_METRIC
import com.memfault.bort.connectivity.ConnectivityTimeCalculator
import com.memfault.bort.metrics.CrashFreeHoursMetricLogger.Companion.OPERATIONAL_CRASHES_METRIC_KEY
import com.memfault.bort.metrics.database.DAILY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.DerivedAggregation
import com.memfault.bort.metrics.database.HOURLY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.metrics.database.SESSION_REPORT_TYPE
import com.memfault.bort.reporting.FinishReport
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.StartReport
import com.memfault.bort.settings.DailyHeartbeatEnabled
import com.memfault.bort.settings.HighResMetricsEnabled
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface CustomMetrics {
    suspend fun add(metric: MetricValue): Long
    suspend fun start(start: StartReport): Long
    suspend fun finish(finish: FinishReport): Long
    suspend fun collectHeartbeat(endTimestampMs: Long): CustomReport
}

private val SYNC_METRICS = setOf(
    "sync_memfault_failure",
    "sync_memfault_successful",
    "sync_failure",
    "sync_successful",
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
) : CustomMetrics {
    private val mutex = Mutex()

    override suspend fun add(metric: MetricValue): Long = mutex.withLock {
        if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName == OPERATIONAL_CRASHES_METRIC_KEY
        ) {
            db.dao().insertAllSessions(metric)
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName == CONNECTIVITY_TYPE_METRIC
        ) {
            db.dao().insertAllReports(metric)
        } else if (metric.reportType == HOURLY_HEARTBEAT_REPORT_TYPE &&
            metric.eventName in SYNC_METRICS
        ) {
            db.dao().insertAllReports(metric)
        } else {
            db.dao().insert(metric)
        }
    }

    override suspend fun start(start: StartReport): Long = mutex.withLock {
        if (start.reportType == SESSION_REPORT_TYPE) {
            db.dao().startWithLatestMetricValues(
                startReport = start,
                hourlyHeartbeatReportType = HOURLY_HEARTBEAT_REPORT_TYPE,
                latestMetricKeys = listOf(CONNECTIVITY_TYPE_METRIC),
            )
        } else {
            -1L
        }
    }

    override suspend fun finish(finish: FinishReport): Long = mutex.withLock {
        if (finish.reportType == SESSION_REPORT_TYPE) {
            db.dao().finish(finish)
        } else {
            -1L
        }
    }

    override suspend fun collectHeartbeat(endTimestampMs: Long): CustomReport = mutex.withLock {
        val report = db.dao().collectHeartbeat(
            dailyHeartbeatReportType = if (dailyHeartbeatEnabled()) {
                DAILY_HEARTBEAT_REPORT_TYPE
            } else {
                null
            },
            endTimestampMs = endTimestampMs,
            hrtFile = if (highResMetricsEnabled()) {
                temporaryFileFactory.createTemporaryFile(suffix = "hrt").useFile { file, preventDeletion ->
                    preventDeletion()
                    file
                }
            } else {
                null
            },
            calculateDerivedAggregations = { dbReport, endTimestamp, metrics, internalMetrics ->
                val aggregations = mutableListOf<DerivedAggregation>()
                aggregations += ConnectivityTimeCalculator.calculate(dbReport, endTimestamp, metrics, internalMetrics)
                aggregations
            },
        )

        // Perform additional modifications on the generated heartbeat and session reports.
        val updatedSessions = report.sessions.map { session ->
            // Always report operational_crashes even if it's 0.
            val updatedMetrics = session.metrics.toMutableMap()
            updatedMetrics.putIfAbsent(OPERATIONAL_CRASHES_METRIC_KEY, JsonPrimitive(0.0))

            // Remove the connectivity.type total time metrics.
            updatedMetrics.keys.removeIf { key -> key.startsWith(CONNECTIVITY_TYPE_METRIC) }

            val updatedInternalMetrics = session.internalMetrics.toMutableMap()

            session.copy(
                metrics = updatedMetrics,
                internalMetrics = updatedInternalMetrics,
            )
        }

        report.copy(
            sessions = updatedSessions,
        )
    }
}

data class CustomReport(
    val hourlyHeartbeatReport: MetricReport,
    val dailyHeartbeatReport: MetricReport?,
    val sessions: List<MetricReport> = emptyList(),
    val hrt: File?,
)

@Serializable
data class MetricReport(
    val version: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val reportType: String,
    val reportName: String? = null,
    val metrics: Map<String, JsonPrimitive>,
    val internalMetrics: Map<String, JsonPrimitive> = mapOf(),
)
