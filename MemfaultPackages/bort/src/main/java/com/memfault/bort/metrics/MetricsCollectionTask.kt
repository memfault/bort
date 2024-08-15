package com.memfault.bort.metrics

import androidx.work.Data
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.DumpsterClient
import com.memfault.bort.InstallationIdProvider
import com.memfault.bort.IntegrationChecker
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.chronicler.ClientRateLimitCollector
import com.memfault.bort.clientserver.MarMetadata.CustomDataRecordingMarMetadata
import com.memfault.bort.clientserver.MarMetadata.HeartbeatMarMetadata
import com.memfault.bort.metrics.HighResTelemetry.Companion.mergeHrtIntoFile
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.DataType.BooleanType
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.DataType.StringType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.InMemoryMetricCollector.Companion.heartbeatMetrics
import com.memfault.bort.metrics.InMemoryMetricCollector.Companion.hrtRollups
import com.memfault.bort.metrics.InMemoryMetricCollector.Companion.internalHeartbeatMetrics
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.database.DAILY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.HOURLY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.SESSION_REPORT_TYPE
import com.memfault.bort.networkstats.NetworkStatsCollector
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.MetricType.COUNTER
import com.memfault.bort.reporting.MetricType.EVENT
import com.memfault.bort.reporting.MetricType.GAUGE
import com.memfault.bort.reporting.MetricType.PROPERTY
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.Resolution
import com.memfault.bort.shared.Logger
import com.memfault.bort.storage.AppStorageStatsCollector
import com.memfault.bort.storage.DatabaseSizeCollector
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseLinuxBootRelativeTime
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.MetricsCollection
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration
import kotlin.time.toKotlinDuration

data class InMemoryMetric(
    val metricName: String,
    val metricValue: JsonPrimitive,
    val metricType: MetricType,
    val internal: Boolean,
)

/**
 * Common interface to make collecting in-memory metrics easier. In-memory metrics are single metrics that we generate
 * during the [MetricsCollectionTask] execution, that don't need to be aggregated for daily heartbeats. These are
 * most often property-like values.
 *
 * For example, used storage space is a good example, because aggregating the used storage space over time doesn't
 * really produce more value than just its most current value.
 *
 * Their exact values will be placed in both hourly and daily heartbeats (if available).
 *
 * In-memory metrics are a way for us to save some database writes, though we incur some penalties loading the HRT
 * file just to edit it.
 */
interface InMemoryMetricCollector {
    suspend fun collect(
        collectionTime: CombinedTime,
    ): List<InMemoryMetric>

    companion object {
        fun List<InMemoryMetric>.heartbeatMetrics(): Map<String, JsonPrimitive> =
            filterNot { it.internal }
                .associate { it.metricName to it.metricValue }

        fun List<InMemoryMetric>.internalHeartbeatMetrics(): Map<String, JsonPrimitive> =
            filter { it.internal }
                .associate { it.metricName to it.metricValue }

        fun List<InMemoryMetric>.hrtRollups(collectionTime: CombinedTime): List<Rollup> =
            map { metric ->
                Rollup(
                    metadata = RollupMetadata(
                        stringKey = metric.metricName,
                        metricType = metric.metricType.toHrtMetricType(),
                        dataType = metric.metricValue.dataType(),
                        internal = metric.internal,
                    ),
                    data = listOf(
                        Datum(t = collectionTime.timestamp.toEpochMilli(), value = metric.metricValue),
                    ),
                )
            }

        private fun MetricType.toHrtMetricType(): HighResTelemetry.MetricType = when (this) {
            COUNTER -> HighResTelemetry.MetricType.Counter
            GAUGE -> HighResTelemetry.MetricType.Gauge
            PROPERTY -> HighResTelemetry.MetricType.Property
            EVENT -> HighResTelemetry.MetricType.Event
        }

        private fun JsonPrimitive.dataType(): DataType = when {
            this.jsonPrimitive.booleanOrNull != null -> BooleanType
            this.jsonPrimitive.doubleOrNull != null -> DoubleType
            this.jsonPrimitive.isString -> StringType
            else -> StringType
        }
    }
}

class MetricsCollectionTask @Inject constructor(
    private val enqueueUpload: EnqueueUpload,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val lastHeartbeatEndTimeProvider: LastHeartbeatEndTimeProvider,
    override val metrics: BuiltinMetricsStore,
    @MetricsCollection private val tokenBucketStore: TokenBucketStore,
    private val packageManagerClient: PackageManagerClient,
    private val systemPropertiesCollector: SystemPropertiesCollector,
    private val heartbeatReportCollector: HeartbeatReportCollector,
    private val storageStatsCollector: StorageStatsCollector,
    private val networkStatsCollector: NetworkStatsCollector,
    private val appVersionsCollector: AppVersionsCollector,
    private val dumpsterClient: DumpsterClient,
    private val bortSystemCapabilities: BortSystemCapabilities,
    private val integrationChecker: IntegrationChecker,
    private val installationIdProvider: InstallationIdProvider,
    private val batteryStatsCollector: BatteryStatsCollector,
    private val metricsSettings: MetricsSettings,
    private val crashHandler: CrashHandler,
    private val clientRateLimitCollector: ClientRateLimitCollector,
    private val appStorageStatsCollector: AppStorageStatsCollector,
    private val databaseSizeCollector: DatabaseSizeCollector,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }
    override fun convertAndValidateInputData(inputData: Data) = Unit

    private suspend fun enqueueHeartbeatUpload(
        collectionTime: CombinedTime,
        lastHeartbeatUptime: BaseLinuxBootRelativeTime,
        propertiesStore: DevicePropertiesStore,
    ) {
        val batteryStatsResult = batteryStatsCollector.collect(
            collectionTime = collectionTime,
            lastHeartbeatUptime = lastHeartbeatUptime,
        )
        Logger.test("Metrics: properties_use_service = ${metricsSettings.propertiesUseMetricService}")
        // These write to Custom Metrics - do before finishing the heartbeat report.
        storageStatsCollector.collectStorageStats(collectionTime)
        networkStatsCollector.collectAndRecord(
            collectionTime = collectionTime,
            lastHeartbeatUptime = lastHeartbeatUptime,
        )

        systemPropertiesCollector.updateSystemProperties(propertiesStore)
        appVersionsCollector.updateAppVersions(propertiesStore)
        if (bortSystemCapabilities.isBortLite()) {
            propertiesStore.upsert(BORT_LITE_METRIC_KEY, true, internal = false)
        }
        val supportsCaliperMetrics = bortSystemCapabilities.supportsCaliperMetrics()
        propertiesStore.upsert(
            name = SUPPORTS_CALIPER_METRICS_KEY,
            value = supportsCaliperMetrics,
            internal = true,
        )

        crashHandler.process()

        val fallbackInternalMetrics =
            updateBuiltinProperties(
                packageManagerClient,
                propertiesStore,
                dumpsterClient,
                integrationChecker,
                installationIdProvider,
            )

        val heartbeatReport = heartbeatReportCollector.finishAndCollectHeartbeatReport(now = collectionTime)

        // This duration should be positive unless there is clock skew (or problems with the RTC battery).
        val hourlyHeartbeatDurationFromReport = (
            heartbeatReport.hourlyHeartbeatReport.endTimestampMs -
                heartbeatReport.hourlyHeartbeatReport.startTimestampMs
            ).toDuration(MILLISECONDS)
            .takeIf { it.isPositive() }

        // This duration can also be negative, but it's the same as we had before.
        val hourlyHeartbeatDurationFromUptime = collectionTime.elapsedRealtime.duration -
            lastHeartbeatUptime.elapsedRealtime.duration

        val hourlyHeartbeatDuration = hourlyHeartbeatDurationFromReport
            ?: hourlyHeartbeatDurationFromUptime

        val heartbeatReportMetrics = heartbeatReport.hourlyHeartbeatReport.metrics
        val heartbeatReportInternalMetrics = heartbeatReport.hourlyHeartbeatReport.internalMetrics

        val propertiesStoreMetrics =
            AggregateMetricFilter.filterAndRenameMetrics(propertiesStore.metrics(), internal = false)
        val propertiesStoreInternalMetrics =
            AggregateMetricFilter.filterAndRenameMetrics(propertiesStore.internalMetrics(), internal = true)

        clientRateLimitCollector.collect(
            collectionTime = collectionTime,
            internalHeartbeatReportMetrics = heartbeatReportInternalMetrics,
        )

        val inMemoryMetrics = listOf(
            appStorageStatsCollector,
            databaseSizeCollector,
        )
            .flatMap { collector ->
                collector.collect(collectionTime)
            }
        val inMemoryHeartbeats = inMemoryMetrics.heartbeatMetrics()
        val inMemoryInternalHeartbeats = inMemoryMetrics.internalHeartbeatMetrics()
        val inMemoryHrtRollups = inMemoryMetrics.hrtRollups(collectionTime)

        // If there were no heartbeat internal metrics, then fallback to include some core values.
        val internalMetrics = heartbeatReportInternalMetrics.ifEmpty { fallbackInternalMetrics }
        uploadHeartbeat(
            batteryStatsFile = batteryStatsResult.batteryStatsFileToUpload,
            collectionTime = collectionTime,
            heartbeatInterval = hourlyHeartbeatDuration,
            heartbeatReportMetrics = heartbeatReportMetrics +
                batteryStatsResult.aggregatedMetrics +
                inMemoryHeartbeats +
                propertiesStoreMetrics,
            heartbeatReportInternalMetrics = internalMetrics +
                batteryStatsResult.internalAggregatedMetrics +
                inMemoryInternalHeartbeats +
                propertiesStoreInternalMetrics,
            reportType = HOURLY_HEARTBEAT_REPORT_TYPE.lowercase(),
            reportName = null,
        )

        heartbeatReport.dailyHeartbeatReport?.let { report ->
            uploadHeartbeat(
                batteryStatsFile = null,
                collectionTime = collectionTime,
                heartbeatInterval = (report.endTimestampMs - report.startTimestampMs).toDuration(MILLISECONDS),
                heartbeatReportMetrics = report.metrics +
                    inMemoryHeartbeats +
                    propertiesStoreMetrics,
                heartbeatReportInternalMetrics = report.internalMetrics +
                    inMemoryMetrics.internalHeartbeatMetrics() +
                    propertiesStoreInternalMetrics,
                reportType = DAILY_HEARTBEAT_REPORT_TYPE.lowercase(),
                reportName = null,
                overrideMonitoringResolution = Resolution.LOW,
            )
        }

        heartbeatReport.sessions
            .forEach { session ->
                uploadHeartbeat(
                    batteryStatsFile = null,
                    collectionTime = collectionTime,
                    heartbeatInterval = (session.endTimestampMs - session.startTimestampMs).toDuration(MILLISECONDS),
                    heartbeatReportMetrics = session.metrics,
                    heartbeatReportInternalMetrics = session.internalMetrics,
                    reportType = SESSION_REPORT_TYPE.lowercase(),
                    reportName = session.reportName,
                )
            }

        // Add batterystats and others to the HRT file.
        heartbeatReport.hrt?.let { hrtFile ->
            val hrtMetricsToAdd = batteryStatsResult.batteryStatsHrt +
                inMemoryHrtRollups +
                propertiesStore.hrtRollups(timestampMs = collectionTime.timestamp.toEpochMilli())
            if (hrtMetricsToAdd.isNotEmpty()) {
                mergeHrtIntoFile(hrtFile, hrtMetricsToAdd)
            }
        }

        uploadHighResMetrics(
            highResFile = heartbeatReport.hrt,
            metricReport = heartbeatReport.hourlyHeartbeatReport,
        )
    }

    private suspend fun uploadHighResMetrics(
        highResFile: File?,
        metricReport: MetricReport?,
    ) {
        highResFile ?: return
        metricReport ?: return
        val startTime = Instant.ofEpochMilli(metricReport.startTimestampMs)
        val endTime = Instant.ofEpochMilli(metricReport.endTimestampMs)
        val duration = java.time.Duration.between(startTime, endTime).toKotlinDuration()
        val metadata = CustomDataRecordingMarMetadata(
            recordingFileName = highResFile.name,
            startTime = AbsoluteTime(startTime),
            durationMs = duration.inWholeMilliseconds,
            mimeTypes = listOf(HIGH_RES_METRICS_MIME_TYPE),
            reason = HIGH_RES_METRICS_REASON,
        )
        enqueueUpload.enqueue(highResFile, metadata, collectionTime = combinedTimeProvider.now())
    }

    private suspend fun uploadHeartbeat(
        batteryStatsFile: File?,
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
        heartbeatReportMetrics: Map<String, JsonPrimitive>,
        heartbeatReportInternalMetrics: Map<String, JsonPrimitive>,
        reportType: String,
        reportName: String?,
        overrideMonitoringResolution: Resolution? = null,
    ) {
        enqueueUpload.enqueue(
            // Note: this is the only upload type which may not have a file. To avoid changing the entire PreparedUpload
            // stack to support this (which is only needed until we migrate the mar), use a fake file.
            file = batteryStatsFile,
            metadata = HeartbeatMarMetadata(
                batteryStatsFileName = batteryStatsFile?.name,
                heartbeatIntervalMs = heartbeatInterval.inWholeMilliseconds,
                customMetrics = heartbeatReportMetrics,
                builtinMetrics = heartbeatReportInternalMetrics,
                reportType = reportType,
                reportName = reportName,
            ),
            collectionTime = collectionTime,
            overrideMonitoringResolution = overrideMonitoringResolution,
        )
    }

    override suspend fun doWork(
        worker: TaskRunnerWorker,
        input: Unit,
    ): TaskResult {
        if (!tokenBucketStore.takeSimple(tag = "metrics")) {
            return TaskResult.FAILURE
        }

        val now = combinedTimeProvider.now()
        val lastHeartbeatUptime = lastHeartbeatEndTimeProvider.lastEnd

        // Create a properties store, which will collect properties in-memory if that setting is enabled (instead of
        // writing them to the metrics service).
        val propertiesStore = DevicePropertiesStore(collectionTime = now, metricsSettings)

        enqueueHeartbeatUpload(now, lastHeartbeatUptime, propertiesStore)

        lastHeartbeatEndTimeProvider.lastEnd = now
        return TaskResult.SUCCESS
    }
}

private const val HIGH_RES_METRICS_MIME_TYPE = "application/vnd.memfault.hrt.v1"
private const val HIGH_RES_METRICS_REASON = "memfault-high-res-metrics"
private const val SUPPORTS_CALIPER_METRICS_KEY = "supports_caliper_metrics"
const val BORT_LITE_METRIC_KEY = "bort_lite"
