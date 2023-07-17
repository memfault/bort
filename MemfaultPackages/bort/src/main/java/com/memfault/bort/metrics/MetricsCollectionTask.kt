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
import com.memfault.bort.clientserver.MarMetadata.CustomDataRecordingMarMetadata
import com.memfault.bort.clientserver.MarMetadata.HeartbeatMarMetadata
import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.metrics.HighResTelemetry.Companion.mergeHrtIntoFile
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.MetricsCollection
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import java.io.File
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.toKotlinDuration
import kotlinx.serialization.json.JsonPrimitive

class MetricsCollectionTask @Inject constructor(
    private val enqueueUpload: EnqueueUpload,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val lastHeartbeatEndTimeProvider: LastHeartbeatEndTimeProvider,
    override val metrics: BuiltinMetricsStore,
    @MetricsCollection private val tokenBucketStore: TokenBucketStore,
    private val packageManagerClient: PackageManagerClient,
    private val systemPropertiesCollector: SystemPropertiesCollector,
    private val devicePropertiesStore: DevicePropertiesStore,
    private val heartbeatReportCollector: HeartbeatReportCollector,
    private val storageStatsCollector: StorageStatsCollector,
    private val appVersionsCollector: AppVersionsCollector,
    private val dumpsterClient: DumpsterClient,
    private val bortSystemCapabilities: BortSystemCapabilities,
    private val integrationChecker: IntegrationChecker,
    private val installationIdProvider: InstallationIdProvider,
    private val batteryStatsCollector: BatteryStatsCollector,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }
    override fun convertAndValidateInputData(inputData: Data) = Unit

    private suspend fun enqueueHeartbeatUpload(
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
        batteryStatsResult: BatteryStatsResult,
    ) {
        // These write to Custom Metrics - do before finishing the heartbeat report.
        storageStatsCollector.collectStorageStats()
        systemPropertiesCollector.updateSystemProperties()
        appVersionsCollector.updateAppVersions()
        val fallbackInternalMetrics =
            updateBuiltinProperties(
                packageManagerClient,
                devicePropertiesStore,
                dumpsterClient,
                integrationChecker,
                installationIdProvider,
            )

        val heartbeatReport = heartbeatReportCollector.finishAndCollectHeartbeatReport()
        val heartbeatReportMetrics = AggregateMetricFilter.filterAndRenameMetrics(
            heartbeatReport?.metricReport?.metrics ?: emptyMap(),
            internal = false,
        )
        val heartbeatReportInternalMetrics = AggregateMetricFilter.filterAndRenameMetrics(
            heartbeatReport?.metricReport?.internalMetrics ?: emptyMap(),
            internal = true,
        )

        // If there were no heartbeat internal metrics, then fallback to include some core values.
        val internalMetrics = heartbeatReportInternalMetrics.ifEmpty { fallbackInternalMetrics }
        uploadHeartbeat(
            batteryStatsFile = batteryStatsResult.batteryStatsFileToUpload,
            collectionTime = collectionTime,
            heartbeatInterval = heartbeatInterval,
            heartbeatReportMetrics = heartbeatReportMetrics + batteryStatsResult.aggregatedMetrics,
            heartbeatReportInternalMetrics = internalMetrics,
        )
        // Add batterystats to HRT file.
        heartbeatReport?.highResFile?.let { hrtFile ->
            if (batteryStatsResult.batteryStatsHrt.isNotEmpty()) {
                mergeHrtIntoFile(hrtFile, batteryStatsResult.batteryStatsHrt)
            }
        }
        uploadHighResMetrics(
            highResFile = heartbeatReport?.highResFile,
            metricReport = heartbeatReport?.metricReport,
        )
    }

    private fun uploadHighResMetrics(
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

    private fun uploadHeartbeat(
        batteryStatsFile: File?,
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
        heartbeatReportMetrics: Map<String, JsonPrimitive>,
        heartbeatReportInternalMetrics: Map<String, JsonPrimitive>,
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
            ),
            collectionTime = collectionTime,
        )
    }

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult {
        if (!tokenBucketStore.takeSimple(tag = "metrics")) {
            return TaskResult.FAILURE
        }

        val now = combinedTimeProvider.now()
        val heartbeatInterval =
            (now.elapsedRealtime.duration - lastHeartbeatEndTimeProvider.lastEnd.elapsedRealtime.duration)

        val supportsCaliperMetrics = bortSystemCapabilities.supportsCaliperMetrics()
        devicePropertiesStore.upsert(
            name = SUPPORTS_CALIPER_METRICS_KEY,
            value = supportsCaliperMetrics,
            internal = true
        )
        if (!supportsCaliperMetrics) {
            Logger.d("!supportsCaliperMetrics, only uploading internal metrics")
            // Include some really basic information.
            val failureMetrics = mapOf(
                SUPPORTS_CALIPER_METRICS_KEY to JsonPrimitive(supportsCaliperMetrics),
                BORT_UPSTREAM_VERSION_CODE to JsonPrimitive(BuildConfig.UPSTREAM_VERSION_CODE),
                BORT_UPSTREAM_VERSION_NAME to JsonPrimitive(BuildConfig.UPSTREAM_VERSION_NAME),
            )
            uploadHeartbeat(
                batteryStatsFile = null,
                collectionTime = now,
                heartbeatInterval = heartbeatInterval,
                heartbeatReportMetrics = emptyMap(),
                heartbeatReportInternalMetrics = failureMetrics,
            )
            return TaskResult.SUCCESS
        }

        val batteryStatsResult = batteryStatsCollector.collect(heartbeatInterval)
        enqueueHeartbeatUpload(now, heartbeatInterval, batteryStatsResult)

        lastHeartbeatEndTimeProvider.lastEnd = now
        return TaskResult.SUCCESS
    }
}

private const val HIGH_RES_METRICS_MIME_TYPE = "application/vnd.memfault.hrt.v1"
private const val HIGH_RES_METRICS_REASON = "memfault-high-res-metrics"
private const val SUPPORTS_CALIPER_METRICS_KEY = "supports_caliper_metrics"
