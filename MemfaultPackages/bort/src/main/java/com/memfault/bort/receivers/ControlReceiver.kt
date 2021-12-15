package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.BugReportRequestStatus
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.DumpsterClient
import com.memfault.bort.INTENT_ACTION_BORT_ENABLE
import com.memfault.bort.INTENT_ACTION_BUG_REPORT_REQUESTED
import com.memfault.bort.INTENT_EXTRA_BORT_ENABLED
import com.memfault.bort.InjectSet
import com.memfault.bort.PendingBugReportRequestAccessor
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.broadcastReply
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.requester.StartRealBugReport
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.applyReporterServiceSettings
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_EXTRA_BUG_REPORT_REQUEST_TIMEOUT_MS
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.getLongOrNull
import com.memfault.bort.shared.goAsync
import com.memfault.bort.tokenbucket.BugReportRequestStore
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.FileUploadHoldingArea
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Base receiver to handle events that control the SDK. */
abstract class BaseControlReceiver : FilteringReceiver(
    setOf(INTENT_ACTION_BORT_ENABLE, INTENT_ACTION_BUG_REPORT_REQUESTED)
) {
    @Inject lateinit var dumpsterClient: DumpsterClient
    @Inject lateinit var bortEnabledProvider: BortEnabledProvider
    @Inject lateinit var periodicWorkRequesters: InjectSet<PeriodicWorkRequester>
    @Inject lateinit var settingsProvider: SettingsProvider
    @Inject lateinit var pendingBugReportRequestAccessor: PendingBugReportRequestAccessor
    @Inject lateinit var fileUploadHoldingArea: FileUploadHoldingArea
    @BugReportRequestStore @Inject lateinit var tokenBucketStore: TokenBucketStore
    @Inject lateinit var bortSystemCapabilities: BortSystemCapabilities
    @Inject lateinit var builtInMetricsStore: BuiltinMetricsStore
    @Inject lateinit var startBugReport: StartRealBugReport
    @Inject lateinit var reporterServiceConnector: ReporterServiceConnector

    private fun allowedByRateLimit(): Boolean =
        tokenBucketStore
            .edit { map ->
                val bucket = map.upsertBucket("control-requested") ?: return@edit false
                bucket.take(tag = "bugreport_request")
            }

    private fun onBugReportRequested(context: Context, intent: Intent) {
        val request = try {
            BugReportRequest.fromIntent(intent)
        } catch (e: Exception) {
            Logger.e("Invalid bug report request", e)
            return
        }

        if (!bortEnabledProvider.isEnabled()) {
            Logger.w("Bort not enabled; not sending request")
            request.broadcastReply(context, BugReportRequestStatus.ERROR_SDK_NOT_ENABLED)
            return
        }

        val allowedByRateLimit = allowedByRateLimit()
        Logger.v("Received request for bug report, allowedByRateLimit=$allowedByRateLimit")

        if (!allowedByRateLimit) {
            request.broadcastReply(context, BugReportRequestStatus.ERROR_RATE_LIMITED)
            return
        }

        val timeout = intent.extras?.getLongOrNull(
            INTENT_EXTRA_BUG_REPORT_REQUEST_TIMEOUT_MS
        )?.let(Long::milliseconds) ?: BugReportRequestTimeoutTask.DEFAULT_TIMEOUT
        CoroutineScope(Dispatchers.Default).launch {
            startBugReport.requestBugReport(
                context,
                pendingBugReportRequestAccessor,
                request,
                timeout,
                settingsProvider.bugReportSettings,
                bortSystemCapabilities,
                builtInMetricsStore
            )
        }
    }

    private fun onBortEnabled(intent: Intent) {
        // It doesn't make sense to take any action here if bort isn't configured to require runtime enabling
        // (we would get into a bad state where jobs are cancelled, but we can not re-enable).
        if (!bortEnabledProvider.requiresRuntimeEnable()) return

        if (!intent.hasExtra(INTENT_EXTRA_BORT_ENABLED)) return
        val isNowEnabled = intent.getBooleanExtra(
            INTENT_EXTRA_BORT_ENABLED,
            false // never used, because we just checked hasExtra()
        )
        val wasEnabled = bortEnabledProvider.isEnabled()
        Logger.test("wasEnabled=$wasEnabled isNowEnabled=$isNowEnabled")
        Logger.logEventBortSdkEnabled(isNowEnabled)
        if (wasEnabled == isNowEnabled) {
            return
        }
        Logger.i(if (isNowEnabled) "bort.enabled" else "bort.disabled", mapOf())

        bortEnabledProvider.setEnabled(isNowEnabled)

        fileUploadHoldingArea.handleChangeBortEnabled()

        goAsync {
            applyReporterServiceSettings(
                reporterServiceConnector = reporterServiceConnector,
                settingsProvider = settingsProvider,
                bortEnabledProvider = bortEnabledProvider,
            )

            periodicWorkRequesters.forEach {
                if (isNowEnabled) {
                    it.startPeriodic()
                } else {
                    it.cancelPeriodic()
                }
            }

            dumpsterClient.setBortEnabled(isNowEnabled)
            dumpsterClient.setStructuredLogEnabled(
                isNowEnabled &&
                    settingsProvider.structuredLogSettings.dataSourceEnabled
            )
        }
    }

    override fun onIntentReceived(context: Context, intent: Intent, action: String) {
        when (action) {
            INTENT_ACTION_BUG_REPORT_REQUESTED -> onBugReportRequested(context, intent)
            INTENT_ACTION_BORT_ENABLE -> onBortEnabled(intent)
        }
    }
}

@AndroidEntryPoint
@Deprecated("Please target ControlReceiver")
class RequestBugReportReceiver : BaseControlReceiver()

@AndroidEntryPoint
@Deprecated("Please target ControlReceiver")
class BortEnableReceiver : BaseControlReceiver()

@AndroidEntryPoint
class ShellControlReceiver : BaseControlReceiver()

@AndroidEntryPoint
class ControlReceiver : BaseControlReceiver()
