package com.memfault.bort.bugreport

import android.app.Application
import android.content.Intent
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_EXTRA_BUG_REPORT_REQUEST_TIMEOUT_MS
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.getLongOrNull
import com.memfault.bort.tokenbucket.BugReportRequestStore
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

interface RequestBugReportIntentUseCase {
    suspend fun onRequestedBugReport(intent: Intent)
}

@ContributesBinding(SingletonComponent::class)
class RealRequestBugReportIntentUseCase
@Inject constructor(
    private val application: Application,
    private val bortEnabledProvider: BortEnabledProvider,
    @BugReportRequestStore val tokenBucketStore: TokenBucketStore,
    private val startBugReportUseCase: StartBugReportUseCase,
) : RequestBugReportIntentUseCase {
    override suspend fun onRequestedBugReport(intent: Intent) {
        val request = try {
            BugReportRequest.fromIntent(intent)
        } catch (e: Exception) {
            Logger.e("Invalid bug report request", e)
            return
        }

        if (!bortEnabledProvider.isEnabled()) {
            Logger.w("Bort not enabled; not sending request")
            request.broadcastReply(application, BugReportRequestStatus.ERROR_SDK_NOT_ENABLED)
            return
        }

        val allowedByRateLimit = tokenBucketStore.takeSimple(key = "control-requested", tag = "bugreport_request")
        Logger.v("Received request for bug report, allowedByRateLimit=$allowedByRateLimit")

        if (!allowedByRateLimit) {
            request.broadcastReply(application, BugReportRequestStatus.ERROR_RATE_LIMITED)
            return
        }

        val timeout = intent.extras
            ?.getLongOrNull(INTENT_EXTRA_BUG_REPORT_REQUEST_TIMEOUT_MS)?.milliseconds
            ?: BugReportRequestTimeoutTask.DEFAULT_TIMEOUT

        startBugReportUseCase.startBugReport(request, timeout)
    }
}
