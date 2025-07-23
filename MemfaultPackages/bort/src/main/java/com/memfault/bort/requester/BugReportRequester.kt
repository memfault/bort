package com.memfault.bort.requester

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.memfault.bort.bugreport.StartBugReportUseCase
import com.memfault.bort.diagnostics.BortJobReporter
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.BugReportSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.runAndTrackExceptions
import com.memfault.bort.tokenbucket.BugReportPeriodic
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.DurationUnit

private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.REQUEST_PERIODIC_BUGREPORT"
private const val WORK_TAG = "BUGREPORT_PERIODIC"

private const val MINIMAL_INPUT_DATA_KEY = "minimal"

private fun BugReportRequest.toInputData(): Data = workDataOf(
    MINIMAL_INPUT_DATA_KEY to options.minimal,
)

private fun Data.toBugReportOptions(): BugReportRequest = BugReportRequest(
    options = BugReportOptions(minimal = getBoolean(MINIMAL_INPUT_DATA_KEY, false)),
)

@ContributesMultibinding(SingletonComponent::class)
class BugReportRequester @Inject constructor(
    private val application: Application,
    private val bugReportSettings: BugReportSettings,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        val requestInterval = bugReportSettings.requestInterval
        val initialDelay = if (justBooted) bugReportSettings.firstBugReportDelayAfterBoot else null

        val request = PeriodicWorkRequestBuilder<BugReportRequestWorker>(
            requestInterval.toDouble(DurationUnit.HOURS).toLong(),
            TimeUnit.HOURS,
        ).apply {
            addTag(WORK_TAG)
            setInputData(
                BugReportRequest(
                    options = bugReportSettings.defaultOptions,
                    requestId = null,
                    replyReceiver = null,
                ).toInputData(),
            )
            initialDelay?.let { delay ->
                setInitialDelay(delay.toLong(DurationUnit.MINUTES), TimeUnit.MINUTES)
            }
            Logger.test("Requesting bug report every ${requestInterval.toDouble(DurationUnit.HOURS)} hours")
        }.build()

        val existingWorkPolicy =
            if (settingsChanged) {
                ExistingPeriodicWorkPolicy.UPDATE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

        WorkManager.getInstance(application)
            .enqueueUniquePeriodicWork(
                WORK_UNIQUE_NAME_PERIODIC,
                existingWorkPolicy,
                request,
            )
    }

    override fun cancelPeriodic() {
        Logger.test("Cancelling $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(application)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean = settings.bugReportSettings.dataSourceEnabled

    override suspend fun diagnostics(): BortWorkInfo = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(WORK_UNIQUE_NAME_PERIODIC)
        .asBortWorkInfo("bugreport")

    override suspend fun parametersChanged(old: SettingsProvider, new: SettingsProvider): Boolean =
        // Note: not including firstBugReportDelayAfterBoot because that is only used immediately after booting.
        old.bugReportSettings.requestInterval != new.bugReportSettings.requestInterval ||
            old.bugReportSettings.defaultOptions != new.bugReportSettings.defaultOptions
}

@HiltWorker
class BugReportRequestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    @BugReportPeriodic private val tokenBucketStore: TokenBucketStore,
    private val bortEnabledProvider: BortEnabledProvider,
    private val startBugReportUseCase: StartBugReportUseCase,
    private val bortJobReporter: BortJobReporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runAndTrackExceptions(jobName = JOB_NAME, bortJobReporter) {
        if (bortEnabledProvider.isEnabled() &&
            tokenBucketStore.takeSimple(tag = BUGREPORT_RATE_LIMITING_TAG) &&
            startBugReportUseCase.startBugReport(request = inputData.toBugReportOptions())
        ) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    companion object {
        const val BUGREPORT_RATE_LIMITING_TAG = "bugreport_periodic"
        const val JOB_NAME = "BugReportRequestWorker"
    }
}
