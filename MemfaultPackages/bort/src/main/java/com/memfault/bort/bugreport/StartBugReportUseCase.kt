package com.memfault.bort.bugreport

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.ExistingWorkPolicy.REPLACE
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.bugreport.BugReportRequestStatus.ERROR_ALREADY_PENDING
import com.memfault.bort.metrics.BUG_REPORT_DELETED_OLD
import com.memfault.bort.metrics.BUG_REPORT_DELETED_STORAGE
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.settings.BugReportSettings
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration

interface StartBugReportUseCase {
    suspend fun startBugReport(
        request: BugReportRequest,
        requestTimeout: Duration = BugReportRequestTimeoutTask.DEFAULT_TIMEOUT,
    ): Boolean
}

@ContributesBinding(SingletonComponent::class)
class RealStartBugReportUseCase
@Inject constructor(
    private val context: Application,
    private val pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
    private val bugReportSettings: BugReportSettings,
    private val builtInMetricsStore: BuiltinMetricsStore,
) : StartBugReportUseCase {
    override suspend fun startBugReport(
        request: BugReportRequest,
        requestTimeout: Duration,
    ): Boolean {
        // First, cleanup old bugreport files that:
        // - Are waiting for connectivity, to be uploaded
        // - Failed to be deleted (perhaps because of a bug/crash)
        //
        // Note that we cannot tell which bugreports are still queued for upload as WorkManager tasks.
        val cleanupResult = cleanupFiles(
            dir = File(context.filesDir, "bugreports"),
            maxDirStorageBytes = bugReportSettings.maxStorageBytes.toLong(),
            maxFileAge = bugReportSettings.maxStoredAge,
        )
        if (cleanupResult.deletedForAgeCount > 0) {
            builtInMetricsStore.increment(BUG_REPORT_DELETED_OLD, incrementBy = cleanupResult.deletedForAgeCount)
        }
        if (cleanupResult.deletedForStorageCount > 0) {
            builtInMetricsStore.increment(
                BUG_REPORT_DELETED_STORAGE,
                incrementBy = cleanupResult.deletedForStorageCount,
            )
        }

        val (success, _) = pendingBugReportRequestAccessor.compareAndSwap(request) { pendingRequest ->
            pendingRequest == null
        }

        if (!success) {
            Logger.w("Ignoring bug report request: already one pending")
            // Re-schedule timeout in case it has not been scheduled AND bug report capturing is not running.
            // This could happen in the odd case where a request was written to pendingBugReportRequestAccessor but Bort
            // killed/crashed before it was able to schedule the timeout and kick off MemfaultDumpstateRunner.
            // If we would not schedule a timeout in that scenario, the pendingBugReportRequestAccessor state would never
            // get cleared.
            pendingBugReportRequestAccessor.get()?.requestId?.let {
                BugReportRequestTimeoutTask.schedule(context, it, KEEP, requestTimeout)
            }
            request.broadcastReply(context, ERROR_ALREADY_PENDING)
            return false
        }

        request.requestId.let {
            BugReportRequestTimeoutTask.schedule(context, it, REPLACE, requestTimeout)
        }

        Logger.v(
            "Sending $INTENT_ACTION_BUG_REPORT_START to " +
                "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER (requestId=${request.requestId}",
        )
        val bugReportStartIntent = Intent(INTENT_ACTION_BUG_REPORT_START).apply {
            component = ComponentName(
                APPLICATION_ID_MEMFAULT_USAGE_REPORTER,
                "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER.BugReportStartReceiver",
            )
            request.applyToIntent(this)
        }
        context.sendBroadcast(bugReportStartIntent)

        return true
    }
}
