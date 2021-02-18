package com.memfault.bort.logcat

import androidx.work.Data
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.fileExt.md5Hex
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.takeSimple
import com.memfault.bort.uploader.FileUploadHoldingArea
import com.memfault.bort.uploader.PendingFileUploadEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogcatCollectionTask(
    private val logcatSettings: LogcatSettings,
    private val logcatCollector: LogcatCollector,
    private val fileUploadHoldingArea: FileUploadHoldingArea,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val tokenBucketStore: TokenBucketStore,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }
    override fun convertAndValidateInputData(inputData: Data) = Unit

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult {
        if (!tokenBucketStore.takeSimple()) return TaskResult.FAILURE

        val now = combinedTimeProvider.now()
        val result = logcatCollector.collect() ?: return TaskResult.SUCCESS
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
                ),
                file = result.file,
                debugTag = DEBUG_TAG,
            )
        )
        return TaskResult.SUCCESS
    }
}

private const val DEBUG_TAG = "UPLOAD_LOGCAT"
