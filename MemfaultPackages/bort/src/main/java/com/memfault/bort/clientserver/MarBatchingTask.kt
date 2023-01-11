package com.memfault.bort.clientserver

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.Payload.MarPayload
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.fileExt.md5Hex
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.oneTimeWorkRequest
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.MaxUploadAttempts
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.uploader.BACKOFF_DURATION
import com.memfault.bort.uploader.EnqueuePreparedUploadTask
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Can be either periodic or one-time.
 *
 * Batches up mar files in the holding area, placing batched files in the upload queue.
 */
class MarBatchingTask @Inject constructor(
    override val getMaxAttempts: MaxUploadAttempts,
    private val marFileHoldingArea: MarFileHoldingArea,
    private val deviceInfoProvider: DeviceInfoProvider,
    override val metrics: BuiltinMetricsStore,
    private val enqueuePreparedUploadTask: EnqueuePreparedUploadTask,
) : Task<Unit>() {
    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult = withContext(Dispatchers.IO) {
        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        marFileHoldingArea.bundleMarFilesForUpload().forEach { marFile ->
            enqueuePreparedUploadTask.upload(
                file = marFile,
                metadata = MarPayload(createMarPayload(marFile, deviceInfo)),
                debugTag = UPLOAD_TAG_MAR,
                continuation = null,
                shouldCompress = false,
                // Jitter was applied when batching - don't apply again for the upload.
                applyJitter = false,
            )
        }
        TaskResult.SUCCESS
    }

    override fun convertAndValidateInputData(inputData: Data) = Unit

    companion object {
        private const val WORK_UNIQUE_NAME_PERIODIC = "mar_upload_periodic"
        private const val WORK_UNIQUE_NAME_ONE_TIME = "mar_upload_onetime"
        const val UPLOAD_TAG_MAR = "mar"

        /**
         * For internal use only (from TestReceiver - and in the future, in dev mode on debug devices):
         *
         * Run a one-time task to batch+upload mar files currently in holding area.
         */
        fun enqueueOneTimeBatchMarFiles(context: Context) {
            oneTimeWorkRequest<MarBatchingTask>(workDataOf()) {
                addTag(WORK_UNIQUE_NAME_ONE_TIME)
                setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DURATION.toJavaDuration())
            }.also { workRequest ->
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        WORK_UNIQUE_NAME_ONE_TIME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        workRequest
                    )
            }
        }

        fun schedulePeriodicMarBatching(
            context: Context,
            period: Duration,
            initialDelay: Duration,
        ) {
            periodicWorkRequest<MarBatchingTask>(period, workDataOf()) {
                addTag(WORK_UNIQUE_NAME_PERIODIC)
                setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DURATION.toJavaDuration())
                setInitialDelay(initialDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }.also { workRequest ->
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        WORK_UNIQUE_NAME_PERIODIC,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest
                    )
            }
        }

        fun createMarPayload(marFileToUpload: File, deviceInfo: DeviceInfo) = MarFileUploadPayload(
            file = FileUploadToken(
                md5 = marFileToUpload.md5Hex(),
                name = marFileToUpload.name,
            ),
            hardwareVersion = deviceInfo.hardwareVersion,
            deviceSerial = deviceInfo.deviceSerial,
            softwareVersion = deviceInfo.softwareVersion,
        )

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
        }
    }
}

@ContributesMultibinding(SingletonComponent::class)
class PeriodicMarUploadRequester @Inject constructor(
    private val context: Context,
    private val httpApiSettings: HttpApiSettings,
    private val jitterDelayProvider: JitterDelayProvider,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        if (!httpApiSettings.batchMarUploads) return

        // Jitter is based on the mar batching period.
        val maxJitterDelay = httpApiSettings.batchedMarUploadPeriod.toJavaDuration()
        val jitter: Duration
        val bootDelay = if (justBooted) 5.minutes else ZERO
        jitter = bootDelay + jitterDelayProvider.randomJitterDelay(maxDelay = maxJitterDelay).toKotlinDuration()
        MarBatchingTask.schedulePeriodicMarBatching(
            context = context,
            period = httpApiSettings.batchedMarUploadPeriod,
            initialDelay = jitter,
        )
    }

    override fun cancelPeriodic() {
        MarBatchingTask.cancelPeriodic(context)
    }

    override suspend fun restartRequired(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.httpApiSettings.useMarUpload() != new.httpApiSettings.useMarUpload() ||
            old.httpApiSettings.batchMarUploads != new.httpApiSettings.batchMarUploads ||
            old.httpApiSettings.batchedMarUploadPeriod != new.httpApiSettings.batchedMarUploadPeriod
}
