package com.memfault.bort.requester

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.BugReportRequestStatus
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.IndividualWorkerFactory
import com.memfault.bort.PendingBugReportRequestAccessor
import com.memfault.bort.broadcastReply
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.BugReportSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.shared.Logger
import com.memfault.bort.tokenbucket.BugReportPeriodic
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.takeSimple
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration

private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.REQUEST_PERIODIC_BUGREPORT"
private const val WORK_INTERVAL_PREFERENCE_KEY = "com.memfault.bort.work.BUGREPORT_INTERVAL"
private const val WORK_INTERVAL_PREFERENCE_ABSENT = -1L
private const val WORK_TAG = "BUGREPORT_PERIODIC"

private const val MINIMAL_INPUT_DATA_KEY = "minimal"

private fun BugReportRequest.toInputData(): Data = workDataOf(
    MINIMAL_INPUT_DATA_KEY to options.minimal,
)

private fun Data.toBugReportOptions(): BugReportRequest = BugReportRequest(
    options = BugReportOptions(minimal = getBoolean(MINIMAL_INPUT_DATA_KEY, false)),
)

interface StartBugReport {
    suspend fun requestBugReport(
        context: Context,
        pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
        request: BugReportRequest,
        requestTimeout: Duration = BugReportRequestTimeoutTask.DEFAULT_TIMEOUT,
        bugReportSettings: BugReportSettings,
        bortSystemCapabilities: BortSystemCapabilities,
        builtInMetricsStore: BuiltinMetricsStore,
    ): Boolean
}

@ContributesBinding(SingletonComponent::class)
class StartRealBugReport @Inject constructor() : StartBugReport {
    override suspend fun requestBugReport(
        context: Context,
        pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
        request: BugReportRequest,
        requestTimeout: Duration,
        bugReportSettings: BugReportSettings,
        bortSystemCapabilities: BortSystemCapabilities,
        builtInMetricsStore: BuiltinMetricsStore,
    ): Boolean {
        // First, cleanup the bugreport filestore.
        cleanupBugReports(
            bugReportDir = File(context.filesDir, "bugreports"),
            maxBugReportStorageBytes = bugReportSettings.maxStorageBytes,
            maxBugReportAge = bugReportSettings.maxStoredAge,
            timeNowMs = System.currentTimeMillis(),
            metrics = builtInMetricsStore,
        )

        // Dump internal metrics to logs, if SDK is not capable of uploading them as metrics.

        // Important: don't do this on devices which do support uploading metrics: calling collectMetrics is a destructive
        // operation (resets all metrics after collecting them).
        if (!bortSystemCapabilities.supportsCaliperMetrics()) {
            Logger.i("Internal metrics: ${builtInMetricsStore.collectMetrics()}")
        }

        val (success, _) = pendingBugReportRequestAccessor.compareAndSwap(request) { it == null }
        if (!success) {
            Logger.w("Ignoring bug report request: already one pending")
            // Re-schedule timeout in case it has not been scheduled AND bug report capturing is not running.
            // This could happen in the odd case where a request was written to pendingBugReportRequestAccessor but Bort
            // killed/crashed before it was able to schedule the timeout and kick off MemfaultDumpstateRunner.
            // If we would not schedule a timeout in that scenario, the pendingBugReportRequestAccessor state would never
            // get cleared.
            pendingBugReportRequestAccessor.get()?.requestId.let {
                BugReportRequestTimeoutTask.schedule(context, it, ExistingWorkPolicy.KEEP, requestTimeout)
            }
            request.broadcastReply(context, BugReportRequestStatus.ERROR_ALREADY_PENDING)
            return false
        }
        request.requestId.let {
            BugReportRequestTimeoutTask.schedule(context, it, ExistingWorkPolicy.REPLACE, requestTimeout)
        }

        Logger.v(
            "Sending $INTENT_ACTION_BUG_REPORT_START to " +
                "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER (requestId=${request.requestId}"
        )
        Intent(INTENT_ACTION_BUG_REPORT_START).apply {
            component = ComponentName(
                APPLICATION_ID_MEMFAULT_USAGE_REPORTER,
                "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER.BugReportStartReceiver"
            )
            request.applyToIntent(this)
        }.also {
            context.sendBroadcast(it)
        }
        return true
    }
}

@ContributesMultibinding(SingletonComponent::class)
class BugReportRequester @Inject constructor(
    private val context: Context,
    private val bugReportSettings: BugReportSettings,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        if (!bugReportSettings.dataSourceEnabled) return

        val requestInterval = bugReportSettings.requestInterval
        val initialDelay = if (justBooted) bugReportSettings.firstBugReportDelayAfterBoot else null

        PeriodicWorkRequestBuilder<BugReportRequestWorker>(
            requestInterval.inHours.toLong(),
            TimeUnit.HOURS
        ).also { builder ->
            builder.addTag(WORK_TAG)
            builder.setInputData(
                BugReportRequest(
                    options = bugReportSettings.defaultOptions,
                    requestId = null,
                    replyReceiver = null,
                ).toInputData()
            )
            initialDelay?.let { delay ->
                builder.setInitialDelay(delay.inMinutes.toLong(), TimeUnit.MINUTES)
            }
            Logger.test("Requesting bug report every ${requestInterval.inHours} hours")
        }.build().also {
            val existingWorkPolicy =
                if (settingsChanged) ExistingPeriodicWorkPolicy.REPLACE
                else ExistingPeriodicWorkPolicy.KEEP

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_UNIQUE_NAME_PERIODIC,
                    existingWorkPolicy,
                    it
                )
        }
    }

    override fun cancelPeriodic() {
        Logger.test("Cancelling $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override fun restartRequired(old: SettingsProvider, new: SettingsProvider): Boolean =
        // Note: not including firstBugReportDelayAfterBoot because that is only used immediately after booting.
        old.bugReportSettings.dataSourceEnabled != new.bugReportSettings.dataSourceEnabled ||
            old.bugReportSettings.requestInterval != new.bugReportSettings.requestInterval ||
            old.bugReportSettings.defaultOptions != new.bugReportSettings.defaultOptions
}

@AssistedFactory
@ContributesMultibinding(SingletonComponent::class)
interface BugReportRequestWorkerFactory : IndividualWorkerFactory {
    override fun create(workerParameters: WorkerParameters): BugReportRequestWorker
    override fun type() = BugReportRequestWorker::class
}

class BugReportRequestWorker @AssistedInject constructor(
    appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
    @BugReportPeriodic private val tokenBucketStore: TokenBucketStore,
    private val bugReportSettings: BugReportSettings,
    private val bortSystemCapabilities: BortSystemCapabilities,
    private val builtInMetricsStore: BuiltinMetricsStore,
    private val bortEnabledProvider: BortEnabledProvider,
    private val startBugReport: StartBugReport,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result =
        if (bortEnabledProvider.isEnabled() &&
            tokenBucketStore.takeSimple(tag = BUGREPORT_RATE_LIMITING_TAG) && captureBugReport()
        ) Result.success() else Result.failure()

    suspend fun captureBugReport(): Boolean = startBugReport.requestBugReport(
        context = applicationContext,
        pendingBugReportRequestAccessor = pendingBugReportRequestAccessor,
        request = inputData.toBugReportOptions(),
        bugReportSettings = bugReportSettings,
        bortSystemCapabilities = bortSystemCapabilities,
        builtInMetricsStore = builtInMetricsStore,
    )

    companion object {
        const val BUGREPORT_RATE_LIMITING_TAG = "bugreport_periodic"
    }
}
