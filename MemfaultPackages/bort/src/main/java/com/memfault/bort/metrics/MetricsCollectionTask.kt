package com.memfault.bort.metrics

import android.os.RemoteException
import androidx.work.Data
import com.memfault.bort.DevMode
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DumpsterClient
import com.memfault.bort.FileUploadToken
import com.memfault.bort.HeartbeatFileUploadPayload
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.fileExt.md5Hex
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.MetricsCollection
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.takeSimple
import com.memfault.bort.uploader.EnqueueUpload
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetricsCollectionTask @Inject constructor(
    private val batteryStatsHistoryCollector: BatteryStatsHistoryCollector,
    private val enqueueUpload: EnqueueUpload,
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val lastHeartbeatEndTimeProvider: LastHeartbeatEndTimeProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    override val metrics: BuiltinMetricsStore,
    @MetricsCollection private val tokenBucketStore: TokenBucketStore,
    private val packageManagerClient: PackageManagerClient,
    private val systemPropertiesCollector: SystemPropertiesCollector,
    private val devicePropertiesStore: DevicePropertiesStore,
    private val heartbeatReportCollector: HeartbeatReportCollector,
    private val storageStatsCollector: StorageStatsCollector,
    private val appVersionsCollector: AppVersionsCollector,
    private val dumpsterClient: DumpsterClient,
    private val devMode: DevMode,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }
    override fun convertAndValidateInputData(inputData: Data) = Unit

    private suspend fun enqueueHeartbeatUpload(
        now: CombinedTime,
        heartbeatInterval: Duration,
        batteryStatsFile: File?,
    ) {
        storageStatsCollector.collectStorageStats()

        val heartbeatReport = heartbeatReportCollector.finishAndCollectHeartbeatReport()
        val heartbeatReportMetrics = heartbeatReport?.metrics ?: emptyMap()
        val heartbeatReportInternalMetrics = heartbeatReport?.internalMetrics ?: emptyMap()

        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        systemPropertiesCollector.updateSystemProperties()
        appVersionsCollector.updateAppVersions()
        updateBuiltinProperties(packageManagerClient, devicePropertiesStore, dumpsterClient)
        enqueueUpload.enqueue(
            // Note: this is the only upload type which may not have a file. To avoid changing the entire PreparedUpload
            // stack to support this (which is only needed until we migrate the mar), use a fake file.
            batteryStatsFile ?: File("/dev/null"),
            HeartbeatFileUploadPayload(
                hardwareVersion = deviceInfo.hardwareVersion,
                deviceSerial = deviceInfo.deviceSerial,
                softwareVersion = deviceInfo.softwareVersion,
                collectionTime = now,
                heartbeatIntervalMs = heartbeatInterval.inWholeMilliseconds,
                customMetrics = devicePropertiesStore.collectDeviceProperties(internal = false) +
                    heartbeatReportMetrics,
                builtinMetrics = devicePropertiesStore.collectDeviceProperties(internal = true) +
                    metrics.collectMetrics() + heartbeatReportInternalMetrics,
                attachments = HeartbeatFileUploadPayload.Attachments(
                    batteryStats = batteryStatsFile?.let {
                        HeartbeatFileUploadPayload.Attachments.BatteryStats(
                            file = FileUploadToken(
                                md5 = withContext(Dispatchers.IO) {
                                    batteryStatsFile.md5Hex()
                                },
                                name = "batterystats.txt",
                            )
                        )
                    }
                ),
                cidReference = nextLogcatCidProvider.cid,
            ),
            DEBUG_TAG,
            collectionTime = now,
        )
    }

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult {
        if (!devMode.isEnabled() && !tokenBucketStore.takeSimple(tag = "metrics")) {
            return TaskResult.FAILURE
        }

        val now = combinedTimeProvider.now()
        val heartbeatInterval =
            (now.elapsedRealtime.duration - lastHeartbeatEndTimeProvider.lastEnd.elapsedRealtime.duration)

        // The batteryStatsHistoryCollector will use the NEXT time from the previous run and use that as starting
        // point for the data to collect. In practice, this roughly matches the start of the current heartbeat period.
        // But, in case that got screwy for some reason, impose a somewhat arbitrary limit on how much batterystats data
        // we collect, because the history can grow *very* large. In the backend, any extra data before it, will get
        // clipped when aggregating, so it doesn't matter if there's more.
        val batteryStatsLimit = heartbeatInterval * 2

        val batteryStatsFile = try {
            batteryStatsHistoryCollector.collect(
                limit = batteryStatsLimit
            )
        } catch (e: RemoteException) {
            Logger.w("Unable to connect to ReporterService to run batterystats")
            null
        } catch (e: Exception) {
            Logger.e("Failed to collect batterystats", mapOf(), e)
            metrics.increment(BATTERYSTATS_FAILED)
            null
        }

        enqueueHeartbeatUpload(now, heartbeatInterval, batteryStatsFile)

        lastHeartbeatEndTimeProvider.lastEnd = now
        return TaskResult.SUCCESS
    }
}

private const val DEBUG_TAG = "UPLOAD_HEARTBEAT"
