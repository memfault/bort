package com.memfault.bort.clientserver

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.FileUploader
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
import com.memfault.bort.shared.Logger
import com.memfault.bort.uploader.BACKOFF_DURATION
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.minutes
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Can be either periodic or one-time.
 *
 * Always uploads whatever the mar holding area has ready (if anything).
 */
class MarUploadTask @Inject constructor(
    override val getMaxAttempts: MaxUploadAttempts,
    private val marFileHoldingArea: MarFileHoldingArea,
    private val fileUploader: FileUploader,
    private val deviceInfoProvider: DeviceInfoProvider,
    override val metrics: BuiltinMetricsStore,
) : Task<Unit>() {
    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult = withContext(Dispatchers.IO) {
        // Avoid re-batching a mar file that another task is currently uploading (if e.g. a one-time task happens to
        // overlap a periodic task).
        uploadMutex.withLock {
            Logger.d("MarUploadTask")
            val marFileToUpload = marFileHoldingArea.getMarForUpload()
            if (marFileToUpload == null) {
                Logger.d("No mar files to upload")
                return@withContext TaskResult.SUCCESS
            }

            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            val payload = MarFileUploadPayload(
                file = FileUploadToken(
                    md5 = marFileToUpload.md5Hex(),
                    name = marFileToUpload.name,
                ),
                hardwareVersion = deviceInfo.hardwareVersion,
                deviceSerial = deviceInfo.deviceSerial,
                softwareVersion = deviceInfo.softwareVersion,
            )
            when (
                val result = fileUploader.upload(marFileToUpload, MarPayload(payload), shouldCompress = false)
            ) {
                TaskResult.RETRY -> return@withContext result
                TaskResult.FAILURE -> return@withContext result.also {
                    Logger.w("mar.upload.failed", mapOf("file" to marFileToUpload.name))
                }
            }

            marFileToUpload.delete()
            TaskResult.SUCCESS
        }
    }

    override fun convertAndValidateInputData(inputData: Data) = Unit

    companion object {
        private const val WORK_UNIQUE_NAME_PERIODIC = "mar_upload_periodic"
        private const val WORK_UNIQUE_NAME_ONE_TIME = "mar_upload_onetime"
        private val uploadMutex = Mutex()

        fun enqueueOneTimeMarUpload(
            context: Context,
            constraints: Constraints,
        ) {
            oneTimeWorkRequest<MarUploadTask>(workDataOf()) {
                addTag(WORK_UNIQUE_NAME_ONE_TIME)
                setConstraints(constraints)
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

        fun schedulePeriodicUpload(
            context: Context,
            period: Duration,
            constraints: Constraints,
            initialDelay: Duration,
        ) {
            periodicWorkRequest<MarUploadTask>(period, workDataOf()) {
                addTag(WORK_UNIQUE_NAME_PERIODIC)
                setConstraints(constraints)
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

        val bootDelay = if (justBooted) 5.minutes else ZERO
        val jitter = jitterDelayProvider.randomJitterDelay()
        MarUploadTask.schedulePeriodicUpload(
            context = context,
            period = httpApiSettings.batchedMarUploadPeriod,
            constraints = httpApiSettings.uploadConstraints,
            initialDelay = bootDelay + jitter.toKotlinDuration(),
        )
    }

    override fun cancelPeriodic() {
        MarUploadTask.cancelPeriodic(context)
    }

    override fun restartRequired(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.httpApiSettings.batchMarUploads != new.httpApiSettings.batchMarUploads ||
            old.httpApiSettings.batchedMarUploadPeriod != new.httpApiSettings.batchedMarUploadPeriod
}
