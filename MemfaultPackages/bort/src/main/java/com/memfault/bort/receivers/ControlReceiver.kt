package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.AppUpgrade
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.BugReportRequestStatus
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.DumpsterClient
import com.memfault.bort.INTENT_ACTION_BORT_ENABLE
import com.memfault.bort.INTENT_ACTION_BUG_REPORT_REQUESTED
import com.memfault.bort.INTENT_ACTION_COLLECT_METRICS
import com.memfault.bort.INTENT_ACTION_DEV_MODE
import com.memfault.bort.INTENT_ACTION_UPDATE_CONFIGURATION
import com.memfault.bort.INTENT_ACTION_UPDATE_PROJECT_KEY
import com.memfault.bort.INTENT_EXTRA_BORT_ENABLED
import com.memfault.bort.INTENT_EXTRA_DEV_MODE_ENABLED
import com.memfault.bort.INTENT_EXTRA_PROJECT_KEY
import com.memfault.bort.PendingBugReportRequestAccessor
import com.memfault.bort.RealDevMode
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.broadcastReply
import com.memfault.bort.clientserver.ClientDeviceInfoSender
import com.memfault.bort.dropbox.DropBoxFilterSettings
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.requester.MetricsCollectionRequester
import com.memfault.bort.requester.PeriodicWorkRequester.PeriodicWorkManager
import com.memfault.bort.requester.StartRealBugReport
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.ContinuousLoggingController
import com.memfault.bort.settings.ProjectKeyProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.SettingsUpdateRequester
import com.memfault.bort.settings.applyReporterServiceSettings
import com.memfault.bort.settings.reloadCustomEventConfigFrom
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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Base receiver to handle events that control the SDK. */
abstract class BaseControlReceiver(extraActions: Set<String>) : FilteringReceiver(
    setOf(
        INTENT_ACTION_BORT_ENABLE,
        INTENT_ACTION_BUG_REPORT_REQUESTED,
    ) + extraActions
) {
    @Inject lateinit var dumpsterClient: DumpsterClient
    @Inject lateinit var bortEnabledProvider: BortEnabledProvider
    @Inject lateinit var periodicWorkManager: PeriodicWorkManager
    @Inject lateinit var settingsProvider: SettingsProvider
    @Inject lateinit var pendingBugReportRequestAccessor: PendingBugReportRequestAccessor
    @Inject lateinit var fileUploadHoldingArea: FileUploadHoldingArea
    @BugReportRequestStore @Inject lateinit var tokenBucketStore: TokenBucketStore
    @Inject lateinit var bortSystemCapabilities: BortSystemCapabilities
    @Inject lateinit var builtInMetricsStore: BuiltinMetricsStore
    @Inject lateinit var startBugReport: StartRealBugReport
    @Inject lateinit var reporterServiceConnector: ReporterServiceConnector
    @Inject lateinit var metricsCollectionRequester: MetricsCollectionRequester
    @Inject lateinit var settingsUpdateRequester: SettingsUpdateRequester
    @Inject lateinit var devMode: RealDevMode
    @Inject lateinit var continuousLoggingController: ContinuousLoggingController
    @Inject lateinit var dropBoxFilterSettings: DropBoxFilterSettings
    @Inject lateinit var clientDeviceInfoSender: ClientDeviceInfoSender
    @Inject lateinit var appUpgrade: AppUpgrade
    @Inject lateinit var projectKeyProvider: ProjectKeyProvider

    private fun allowedByRateLimit(): Boolean =
        tokenBucketStore.takeSimple(key = "control-requested", tag = "bugreport_request")

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
        )?.milliseconds ?: BugReportRequestTimeoutTask.DEFAULT_TIMEOUT
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

    private fun onBortEnabled(intent: Intent, context: Context) {
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
                dropBoxFilterSettings = dropBoxFilterSettings,
            )

            periodicWorkManager.scheduleTasksAfterBootOrEnable(bortEnabled = isNowEnabled, justBooted = false)

            dumpsterClient.setBortEnabled(isNowEnabled)
            dumpsterClient.setStructuredLogEnabled(
                isNowEnabled &&
                    settingsProvider.structuredLogSettings.dataSourceEnabled
            )
            continuousLoggingController.configureContinuousLogging()
            // Pass the new settings to structured logging (after we enable/disable it)
            reloadCustomEventConfigFrom(settingsProvider.structuredLogSettings)
            clientDeviceInfoSender.maybeSendDeviceInfoToServer()

            appUpgrade.handleUpgrade(context)
        }
    }

    private fun onCollectMetrics() = CoroutineScope(Dispatchers.Default).launch {
        if (!bortEnabledProvider.isEnabled()) return@launch
        if (!devMode.isEnabled()) {
            Logger.d("Dev mode disabled: not collecting metrics")
            return@launch
        }
        Logger.d("Metric collection requested")
        metricsCollectionRequester.restartPeriodicCollection(resetLastHeartbeatTime = false, collectImmediately = true)
    }

    private fun onUpdateConfig() {
        if (!bortEnabledProvider.isEnabled()) return
        if (!devMode.isEnabled()) {
            Logger.d("Dev mode disabled: not updating config")
            return
        }
        goAsync {
            Logger.d("Settings update requested")
            settingsUpdateRequester.restartSettingsUpdate(delayAfterSettingsUpdate = false)
        }
    }

    private fun onDevMode(intent: Intent, context: Context) {
        if (!bortEnabledProvider.isEnabled()) return
        if (!intent.hasExtra(INTENT_EXTRA_DEV_MODE_ENABLED)) return
        val enabled = intent.getBooleanExtra(
            INTENT_EXTRA_DEV_MODE_ENABLED,
            false // never used, because we just checked hasExtra()
        )
        devMode.setEnabled(enabled, context)
    }

    private fun onChangeProjectKey(intent: Intent) {
        // This is allowed to run before enabling Bort (in fact this is encouraged if possible).
        val newProjectKey = intent.getStringExtra(INTENT_EXTRA_PROJECT_KEY)
        if (newProjectKey != null) {
            projectKeyProvider.projectKey = newProjectKey
        } else {
            projectKeyProvider.reset()
        }
    }

    override fun onIntentReceived(context: Context, intent: Intent, action: String) {
        when (action) {
            INTENT_ACTION_BUG_REPORT_REQUESTED -> onBugReportRequested(context, intent)
            INTENT_ACTION_BORT_ENABLE -> onBortEnabled(intent, context)
            INTENT_ACTION_COLLECT_METRICS -> onCollectMetrics()
            INTENT_ACTION_UPDATE_CONFIGURATION -> onUpdateConfig()
            INTENT_ACTION_DEV_MODE -> onDevMode(intent, context)
            INTENT_ACTION_UPDATE_PROJECT_KEY -> onChangeProjectKey(intent)
        }
    }
}

@AndroidEntryPoint
@Deprecated("Please target ControlReceiver")
class RequestBugReportReceiver : BaseControlReceiver(emptySet())

@AndroidEntryPoint
@Deprecated("Please target ControlReceiver")
class BortEnableReceiver : BaseControlReceiver(emptySet())

@AndroidEntryPoint
class ShellControlReceiver : BaseControlReceiver(
    setOf(
        // These actions are only available from adb (i.e. not via Broadcast from another app on the device).
        INTENT_ACTION_COLLECT_METRICS,
        INTENT_ACTION_UPDATE_CONFIGURATION,
        INTENT_ACTION_DEV_MODE,
        INTENT_ACTION_UPDATE_PROJECT_KEY,
    )
)

@AndroidEntryPoint
class ControlReceiver : BaseControlReceiver(emptySet())
