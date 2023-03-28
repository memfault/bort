package com.memfault.bort.logcat

import androidx.work.Data
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.fileExt.md5Hex
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.LogcatCollectionMode.PERIODIC
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.Logcat
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.FileUploadHoldingArea
import com.memfault.bort.uploader.PendingFileUploadEntry
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogcatCollectionTask @Inject constructor(
    private val logcatSettings: LogcatSettings,
    private val logcatCollector: LogcatCollector,
    private val fileUploadHoldingArea: FileUploadHoldingArea,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    @Logcat private val tokenBucketStore: TokenBucketStore,
    override val metrics: BuiltinMetricsStore,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }
    override fun convertAndValidateInputData(inputData: Data) = Unit

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult {
        if (logcatSettings.collectionMode != PERIODIC) {
            return TaskResult.SUCCESS
        }

        if (!tokenBucketStore.takeSimple(tag = "logcat")) {
            return TaskResult.FAILURE
        }

        val now = combinedTimeProvider.now()
        val result = logcatCollector.collect()
        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        fileUploadHoldingArea.add(
            PendingFileUploadEntry(
                timeSpan = PendingFileUploadEntry.TimeSpan.from(
                    // NOTE: it would be more accurate to derive the "start" & "end" from the logs themselves,
                    // but that's more involved and this is approximation is probably good enough:
                    start = now.elapsedRealtime.duration - logcatSettings.collectionInterval,
                    end = now.elapsedRealtime.duration,
                ),
                payload = LogcatFileUploadPayload(
                    hardwareVersion = deviceInfo.hardwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    softwareVersion = deviceInfo.softwareVersion,
                    collectionTime = now,
                    file = FileUploadToken(
                        md5 = withContext(Dispatchers.IO) {
                            result.file.md5Hex()
                        },
                        name = "logcat.txt",
                    ),
                    command = result.command.toList(),
                    cid = result.cid,
                    nextCid = result.nextCid,
                    containsOops = result.containsOops,
                    collectionMode = PERIODIC,
                ),
                file = result.file,
                debugTag = DEBUG_TAG,
            )
        )
        return TaskResult.SUCCESS
    }
}

private const val DEBUG_TAG = "UPLOAD_LOGCAT"
