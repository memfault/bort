package com.memfault.bort.metrics

import androidx.work.Data
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DumpsterClient
import com.memfault.bort.InstallationIdProvider
import com.memfault.bort.IntegrationChecker
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
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
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.custom.softwareVersionChanged
import com.memfault.bort.metrics.database.DAILY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.HOURLY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.SESSION_REPORT_TYPE
import com.memfault.bort.networkstats.NetworkStatsCollector
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.MetricType.COUNTER
import com.memfault.bort.reporting.MetricType.EVENT
import com.memfault.bort.reporting.MetricType.GAUGE
import com.memfault.bort.reporting.MetricType.PROPERTY
import com.memfault.bort.settings.Resolution
import com.memfault.bort.shared.Logger
import com.memfault.bort.storage.AppStorageStatsCollector
import com.memfault.bort.storage.DatabaseSizeCollector
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseLinuxBootRelativeTime
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.time.toAbsoluteTime
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
import kotlin.time.TimeSource
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
    @MetricsCollection private val tokenBucketStore: TokenBucketStore,
    private val packageManagerClient: PackageManagerClient,
    private val systemPropertiesCollector: SystemPropertiesCollector,
    private val customMetrics: CustomMetrics,
    private val storageStatsCollector: StorageStatsCollector,
    private val networkStatsCollector: NetworkStatsCollector,
    private val appVersionsCollector: AppVersionsCollector,
    private val dumpsterClient: DumpsterClient,
    private val bortSystemCapabilities: BortSystemCapabilities,
    private val integrationChecker: IntegrationChecker,
    private val installationIdProvider: InstallationIdProvider,
    private val batteryStatsCollector: BatteryStatsCollector,
    private val crashHandler: CrashHandler,
    private val clientRateLimitCollector: ClientRateLimitCollector,
    private val appStorageStatsCollector: AppStorageStatsCollector,
    private val databaseSizeCollector: DatabaseSizeCollector,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val everCollectedMetricsPreferenceProvider: EverCollectedMetricsPreferenceProvider,
    private val usageStatsCollector: UsageStatsCollector,
) : Task<Unit> {
    override fun getMaxAttempts(input: Unit) = 1
    override fun convertAndValidateInputData(inputData: Data) = Unit

    private suspend fun enqueueHeartbeatUpload(
        initialCollectionTime: CombinedTime,
        lastHeartbeatUptime: BaseLinuxBootRelativeTime,
        propertiesStore: DevicePropertiesStore,
    ) {
        val startMark = TimeSource.Monotonic.markNow()

        val deviceSoftwareVersion = deviceInfoProvider.getDeviceInfo().softwareVersion
        val heartbeat = customMetrics.startedHeartbeatOrNull()
        val heartbeatSoftwareVersion = heartbeat?.softwareVersion
        val softwareVersionChanged = customMetrics.softwareVersionChanged(deviceSoftwareVersion)

        val batteryStatsResult = batteryStatsCollector.collect(
            collectionTime = initialCollectionTime,
            lastHeartbeatUptime = lastHeartbeatUptime,
        )
        Logger.test("Metrics: software version = $heartbeatSoftwareVersion -> $deviceSoftwareVersion")

        // These write to Custom Metrics - do before finishing the heartbeat report.
        storageStatsCollector.collectStorageStats(initialCollectionTime)
        networkStatsCollector.collectAndRecord(
            collectionTime = initialCollectionTime,
            lastHeartbeatUptime = lastHeartbeatUptime,
        )

        // If the device's software version changed, then we're finishing a heartbeat after an OTA, and don't
        // want to write metrics that would've changed as a result of that OTA, so don't update the sysprops
        // or app versions (or any other metrics that need to account for that). Instead, we will now write the
        // sysprops and app versions at the start and the end of the heartbeat (skipping the end if the
        // software version changed) (and eventually ideally when the metrics change so that you can see the
        // change in HRT). In the meantime for the first heartbeat after this Bort update, we haven't written those
        // metrics at the start of the report yet, so just write it at the end anyways (in the cases where the software
        // version is null).

        val deviceSystemProperties = systemPropertiesCollector.collect()
        val appVersions = appVersionsCollector.collect()
        val neverCollectedMetrics = !everCollectedMetricsPreferenceProvider.getValue()

        usageStatsCollector.collectUsageStats(
            from = heartbeat?.startTimeMs?.toAbsoluteTime(),
            to = initialCollectionTime.timestamp.toAbsoluteTime(),
        )

        // Write these metrics if the software version didn't change, or if the software version hasn't been persisted
        // yet (meaning we haven't written these metrics at the start already).
        if (heartbeat == null || heartbeatSoftwareVersion == null || !softwareVersionChanged || neverCollectedMetrics) {
            deviceSystemProperties?.let { systemPropertiesCollector.record(it, propertiesStore) }
            appVersions?.let { appVersionsCollector.record(it, propertiesStore) }
        }

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

        updateBuiltinProperties(
            packageManagerClient,
            propertiesStore,
            dumpsterClient,
            integrationChecker,
            installationIdProvider,
        )

        val inMemoryMetrics = listOf(
            appStorageStatsCollector,
            databaseSizeCollector,
        ).flatMap { collector -> collector.collect(initialCollectionTime) }
            .toMutableList()

        val collectorsDuration = startMark.elapsedNow()
        inMemoryMetrics.add(
            inMemoryDuration(metricName = METRICS_COLLECTORS_METRIC, duration = collectorsDuration),
        )

        val inMemoryHeartbeats = inMemoryMetrics.heartbeatMetrics().toMutableMap()
        val inMemoryInternalHeartbeats = inMemoryMetrics.internalHeartbeatMetrics().toMutableMap()
        val inMemoryHrtRollups = inMemoryMetrics.hrtRollups(initialCollectionTime).toMutableList()

        // Ensure that we set the "actual" collection time after all metrics have been collected, so that they will all
        // be included in the report.
        val actualCollectionTime = combinedTimeProvider.now()
        val heartbeatReport = customMetrics.collectHeartbeat(
            endTimestampMs = actualCollectionTime.timestamp.toEpochMilli(),
            forceEndAllReports = softwareVersionChanged,
        )

        val collectHeartbeatDuration = startMark.elapsedNow() - collectorsDuration
        val collectionDurationMetric = inMemoryDuration(
            metricName = METRICS_COLLECT_HEARTBEAT_METRIC,
            duration = collectHeartbeatDuration,
        )

        inMemoryInternalHeartbeats.putAll(listOf(collectionDurationMetric).internalHeartbeatMetrics())
        inMemoryHrtRollups.addAll(listOf(collectionDurationMetric).hrtRollups(initialCollectionTime))

        heartbeatReport.hourlyHeartbeatReport.let { hourlyHeartbeatReport ->
            // This duration should be positive unless there is clock skew (or problems with the RTC battery).
            val hourlyHeartbeatDurationFromReport = (
                hourlyHeartbeatReport.endTimestampMs -
                    hourlyHeartbeatReport.startTimestampMs
                ).toDuration(MILLISECONDS)
                .takeIf { it.isPositive() }

            // This duration can also be negative, but it's the same as we had before.
            val hourlyHeartbeatDurationFromUptime = actualCollectionTime.elapsedRealtime.duration -
                lastHeartbeatUptime.elapsedRealtime.duration

            val hourlyHeartbeatDuration = hourlyHeartbeatDurationFromReport
                ?: hourlyHeartbeatDurationFromUptime

            val heartbeatReportMetrics = hourlyHeartbeatReport.metrics
            val heartbeatReportInternalMetrics = hourlyHeartbeatReport.internalMetrics

            clientRateLimitCollector.collect(
                collectionTime = actualCollectionTime,
                internalHeartbeatReportMetrics = heartbeatReportInternalMetrics,
            )

            uploadHeartbeat(
                batteryStatsFile = batteryStatsResult.batteryStatsFileToUpload,
                collectionTime = actualCollectionTime,
                heartbeatInterval = hourlyHeartbeatDuration,
                heartbeatReportMetrics = heartbeatReportMetrics +
                    batteryStatsResult.aggregatedMetrics +
                    inMemoryHeartbeats,
                heartbeatReportInternalMetrics = heartbeatReportInternalMetrics +
                    batteryStatsResult.internalAggregatedMetrics +
                    inMemoryInternalHeartbeats,
                reportType = HOURLY_HEARTBEAT_REPORT_TYPE.lowercase(),
                reportName = null,
                overrideSoftwareVersion = hourlyHeartbeatReport.softwareVersion,
            )

            // Add batterystats and others to the HRT file.
            hourlyHeartbeatReport.hrt?.let { hrtFile ->
                val hrtMetricsToAdd = batteryStatsResult.batteryStatsHrt +
                    inMemoryHrtRollups
                if (hrtMetricsToAdd.isNotEmpty()) {
                    mergeHrtIntoFile(hrtFile, hrtMetricsToAdd)
                }
            }

            uploadHighResMetrics(
                highResFile = hourlyHeartbeatReport.hrt,
                metricReport = hourlyHeartbeatReport,
            )
        }

        heartbeatReport.dailyHeartbeatReport?.let { report ->
            uploadHeartbeat(
                batteryStatsFile = null,
                collectionTime = actualCollectionTime,
                heartbeatInterval = (report.endTimestampMs - report.startTimestampMs)
                    .toDuration(MILLISECONDS),
                heartbeatReportMetrics = report.metrics +
                    inMemoryHeartbeats,
                heartbeatReportInternalMetrics = report.internalMetrics +
                    inMemoryInternalHeartbeats,
                reportType = DAILY_HEARTBEAT_REPORT_TYPE.lowercase(),
                reportName = null,
                overrideMonitoringResolution = Resolution.LOW,
                overrideSoftwareVersion = report.softwareVersion,
            )
        }

        heartbeatReport.sessions
            .forEach { session ->
                uploadHeartbeat(
                    batteryStatsFile = null,
                    collectionTime = actualCollectionTime,
                    heartbeatInterval = (session.endTimestampMs - session.startTimestampMs).toDuration(MILLISECONDS),
                    heartbeatReportMetrics = session.metrics,
                    heartbeatReportInternalMetrics = session.internalMetrics,
                    reportType = SESSION_REPORT_TYPE.lowercase(),
                    reportName = session.reportName,
                    overrideSoftwareVersion = session.softwareVersion,
                )
            }

        // Always record sysprops and app versions at the start of the heartbeat (and the end above).
        deviceSystemProperties?.let { systemPropertiesCollector.record(it, propertiesStore) }
        appVersions?.let { appVersionsCollector.record(it, propertiesStore) }
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
        enqueueUpload.enqueue(
            highResFile,
            metadata,
            collectionTime = combinedTimeProvider.now(),
            overrideSoftwareVersion = metricReport.softwareVersion,
        )
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
        overrideSoftwareVersion: String? = null,
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
            overrideSoftwareVersion = overrideSoftwareVersion,
        )
    }

    override suspend fun doWork(input: Unit): TaskResult {
        if (!tokenBucketStore.takeSimple(tag = METRICS_RATE_LIMIT_TAG)) {
            return TaskResult.FAILURE
        }

        val now = combinedTimeProvider.now()
        val lastHeartbeatUptime = lastHeartbeatEndTimeProvider.lastEnd

        // Create a properties store, which will collect properties in-memory if that setting is enabled (instead of
        // writing them to the metrics service).
        val propertiesStore = DevicePropertiesStore()

        enqueueHeartbeatUpload(now, lastHeartbeatUptime, propertiesStore)

        lastHeartbeatEndTimeProvider.lastEnd = now
        everCollectedMetricsPreferenceProvider.setValue(true)
        return TaskResult.SUCCESS
    }

    private fun inMemoryDuration(
        metricName: String,
        duration: Duration,
    ): InMemoryMetric = InMemoryMetric(
        metricName = metricName,
        metricValue = JsonPrimitive(duration.inWholeMilliseconds),
        metricType = GAUGE,
        internal = true,
    )
}

private const val HIGH_RES_METRICS_MIME_TYPE = "application/vnd.memfault.hrt.v1"
private const val HIGH_RES_METRICS_REASON = "memfault-high-res-metrics"
private const val SUPPORTS_CALIPER_METRICS_KEY = "supports_caliper_metrics"
const val BORT_LITE_METRIC_KEY = "bort_lite"

private const val METRICS_COLLECT_HEARTBEAT_METRIC = "metrics_collect_heartbeat_duration"
private const val METRICS_COLLECTORS_METRIC = "metrics_collectors_duration"
private const val METRICS_RATE_LIMIT_TAG = "metrics"
