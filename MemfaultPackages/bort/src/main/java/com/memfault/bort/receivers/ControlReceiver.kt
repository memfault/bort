package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.memfault.bort.BugReportRequestStatus
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.INTENT_ACTION_BORT_ENABLE
import com.memfault.bort.INTENT_ACTION_BUG_REPORT_REQUESTED
import com.memfault.bort.INTENT_EXTRA_BORT_ENABLED
import com.memfault.bort.PREFERENCE_TOKEN_BUCKET_BUGREPORT_REQUESTS
import com.memfault.bort.broadcastReply
import com.memfault.bort.requester.BugReportRequester
import com.memfault.bort.requester.MetricsCollectionRequester
import com.memfault.bort.requester.requestBugReport
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_EXTRA_BUG_REPORT_REQUEST_TIMEOUT_MS
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.getLongOrNull
import com.memfault.bort.tokenbucket.RealTokenBucketFactory
import com.memfault.bort.tokenbucket.RealTokenBucketStorage
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.sendSdkEnabledEvent
import java.lang.Exception
import kotlin.time.milliseconds
import kotlin.time.minutes

/** Base receiver to handle events that control the SDK. */
abstract class BaseControlReceiver : FilteringReceiver(
    setOf(INTENT_ACTION_BORT_ENABLE, INTENT_ACTION_BUG_REPORT_REQUESTED)
) {
    private fun allowedByRateLimit(context: Context): Boolean =
        TokenBucketStore(
            storage = RealTokenBucketStorage(
                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context),
                preferenceKey = PREFERENCE_TOKEN_BUCKET_BUGREPORT_REQUESTS,
            ),
            maxBuckets = 1,
            tokenBucketFactory = RealTokenBucketFactory(defaultCapacity = 3, defaultPeriod = 30.minutes),
        ).edit { map ->
            val bucket = map.upsertBucket("control-requested") ?: return@edit false
            bucket.take()
        }

    private fun onBugReportRequested(context: Context, intent: Intent) {
        val request = try {
            BugReportRequest.fromIntent(intent)
        } catch (e: Exception) {
            Logger.e("Invalid bug report request", e)
            return
        }

        val allowedByRateLimit = allowedByRateLimit(context)

        Logger.v("Received request for bug report, allowedByRateLimit=$allowedByRateLimit")
        if (!bortEnabledProvider.isEnabled()) {
            Logger.w("Bort not enabled; not sending request")
            request.broadcastReply(context, BugReportRequestStatus.ERROR_SDK_NOT_ENABLED)
            return
        }
        if (!allowedByRateLimit) {
            request.broadcastReply(context, BugReportRequestStatus.ERROR_RATE_LIMITED)
            return
        }

        val timeout = intent.extras?.getLongOrNull(
            INTENT_EXTRA_BUG_REPORT_REQUEST_TIMEOUT_MS
        )?.let(Long::milliseconds) ?: BugReportRequestTimeoutTask.DEFAULT_TIMEOUT
        requestBugReport(context, pendingBugReportRequestAccessor, request, timeout)
    }

    private fun onBortEnabled(context: Context, intent: Intent) {
        val isNowEnabled = intent.getBooleanExtra(
            INTENT_EXTRA_BORT_ENABLED,
            !settingsProvider.isRuntimeEnableRequired
        )
        val wasEnabled = bortEnabledProvider.isEnabled()
        Logger.test("wasEnabled=$wasEnabled isNowEnabled=$isNowEnabled")
        Logger.logEventBortSdkEnabled(isNowEnabled)
        if (wasEnabled == isNowEnabled) {
            return
        }

        bortEnabledProvider.setEnabled(isNowEnabled)

        listOf(
            MetricsCollectionRequester(context, settingsProvider.metricsSettings),
            BugReportRequester(context, settingsProvider.bugReportSettings),
        ).forEach {
            if (isNowEnabled) {
                it.startPeriodic()
            } else {
                it.cancelPeriodic()
            }
        }

        sendSdkEnabledEvent(
            ingressService,
            isNowEnabled,
            deviceIdProvider,
            settingsProvider.sdkVersionInfo
        )
    }

    override fun onIntentReceived(context: Context, intent: Intent, action: String) {
        when (action) {
            INTENT_ACTION_BUG_REPORT_REQUESTED -> onBugReportRequested(context, intent)
            INTENT_ACTION_BORT_ENABLE -> onBortEnabled(context, intent)
        }
    }
}

@Deprecated("Please target ControlReceiver")
class RequestBugReportReceiver : BaseControlReceiver()

@Deprecated("Please target ControlReceiver")
class BortEnableReceiver : BaseControlReceiver()

class ShellControlReceiver : BaseControlReceiver()

class ControlReceiver : BaseControlReceiver()
