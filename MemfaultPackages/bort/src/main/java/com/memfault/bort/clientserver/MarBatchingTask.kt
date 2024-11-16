package com.memfault.bort.clientserver

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.IO
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.Payload.MarPayload
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.diagnostics.BortErrorType.FileCleanupError
import com.memfault.bort.diagnostics.BortErrors
import com.memfault.bort.fileExt.md5Hex
import com.memfault.bort.oneTimeWorkRequest
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.BortWorkInfo
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.requester.asBortWorkInfo
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.requester.directorySize
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.MaxUploadAttempts
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.StorageSettings
import com.memfault.bort.settings.UploadConstraints
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.uploader.BACKOFF_DURATION
import com.memfault.bort.uploader.EnqueuePreparedUploadTask
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Can be either periodic or one-time.
 *
 * Batches up mar files in the holding area, placing batched files in the upload queue.
 */
class MarBatchingTask @Inject constructor(
    private val maxUploadAttempts: MaxUploadAttempts,
    private val marFileHoldingArea: MarFileHoldingArea,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val enqueuePreparedUploadTask: EnqueuePreparedUploadTask,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val storageSettings: StorageSettings,
    private val bortErrors: BortErrors,
    @IO private val ioCoroutineContext: CoroutineContext,
) : Task<Unit> {
    private val bortTempDeletedMetric = Reporting.report().counter(
        name = "bort_temp_deleted",
        internal = true,
    )
    private val bortTempStorageUsedMetric = Reporting.report().distribution(
        name = "bort_temp_storage_bytes",
        aggregations = listOf(NumericAgg.LATEST_VALUE),
        internal = true,
    )

    override fun getMaxAttempts(input: Unit): Int = maxUploadAttempts()

    override suspend fun doWork(input: Unit): TaskResult = withContext(ioCoroutineContext) {
        try {
            temporaryFileFactory.temporaryFileDirectory?.let { tempDir ->
                bortTempStorageUsedMetric.record(tempDir.directorySize())
                val result = cleanupFiles(
                    dir = tempDir,
                    maxDirStorageBytes = storageSettings.bortTempMaxStorageBytes,
                    maxFileAge = storageSettings.bortTempMaxStorageAge,
                )
                val deleted = result.deletedForStorageCount + result.deletedForAgeCount
                if (deleted > 0) {
                    Logger.d("Deleted $deleted bort temp files to stay under storage limit")
                    bortTempDeletedMetric.incrementBy(deleted)
                }
            }
            // Don't allow any errors in cleanup to prevent us uploading files.
        } catch (e: Exception) {
            Logger.e("Error cleaning up files", e)
            bortErrors.add(type = FileCleanupError, error = e)
        }

        // Enqueue all Bort error Chronicler entries, right before upload.
        try {
            bortErrors.enqueueBortErrorsForUpload()
        } catch (e: Exception) {
            Logger.e("Error enqueuing Bort Chronicler entries", e)
        }

        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        marFileHoldingArea.bundleMarFilesForUpload().forEach { marFile ->
            enqueuePreparedUploadTask.upload(
                file = marFile,
                metadata = MarPayload(createMarPayload(marFile, deviceInfo)),
                debugTag = UPLOAD_TAG_MAR,
                shouldCompress = false,
                // Jitter was applied when batching - don't apply again for the upload.
                applyJitter = false,
            )
        }
        TaskResult.SUCCESS
    }

    override fun convertAndValidateInputData(inputData: Data) = Unit

    companion object {
        const val WORK_UNIQUE_NAME_PERIODIC = "mar_upload_periodic"
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
                        workRequest,
                    )
            }
        }

        fun schedulePeriodicMarBatching(
            context: Context,
            period: Duration,
            initialDelay: Duration,
            constraints: Constraints,
        ) {
            periodicWorkRequest<MarBatchingTask>(period, workDataOf()) {
                addTag(WORK_UNIQUE_NAME_PERIODIC)
                setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DURATION.toJavaDuration())
                setInitialDelay(initialDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                setConstraints(constraints)
            }.also { workRequest ->
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        WORK_UNIQUE_NAME_PERIODIC,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest,
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
    private val application: Application,
    private val httpApiSettings: HttpApiSettings,
    private val jitterDelayProvider: JitterDelayProvider,
    private val constraints: UploadConstraints,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        // Jitter is based on the mar batching period.
        val maxJitterDelay = httpApiSettings.batchedMarUploadPeriod
        val jitter: Duration
        val bootDelay = if (justBooted) 5.minutes else ZERO
        jitter = bootDelay + jitterDelayProvider.randomJitterDelay(maxDelay = maxJitterDelay)
        MarBatchingTask.schedulePeriodicMarBatching(
            context = application,
            period = httpApiSettings.batchedMarUploadPeriod,
            initialDelay = jitter,
            constraints = constraints(),
        )
    }

    override fun cancelPeriodic() {
        MarBatchingTask.cancelPeriodic(application)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean = settings.httpApiSettings.batchMarUploads

    override suspend fun diagnostics(): BortWorkInfo = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(MarBatchingTask.WORK_UNIQUE_NAME_PERIODIC)
        .asBortWorkInfo("marbatching")

    override suspend fun parametersChanged(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.httpApiSettings.batchedMarUploadPeriod != new.httpApiSettings.batchedMarUploadPeriod
}
