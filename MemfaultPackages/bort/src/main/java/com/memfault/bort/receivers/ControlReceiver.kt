package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.AppUpgrade
import com.memfault.bort.DumpsterClient
import com.memfault.bort.INTENT_ACTION_BORT_ENABLE
import com.memfault.bort.INTENT_ACTION_BUG_REPORT_REQUESTED
import com.memfault.bort.INTENT_ACTION_COLLECT_METRICS
import com.memfault.bort.INTENT_ACTION_DEV_MODE
import com.memfault.bort.INTENT_ACTION_OVERRIDE_SERIAL
import com.memfault.bort.INTENT_ACTION_UPDATE_CONFIGURATION
import com.memfault.bort.INTENT_ACTION_UPDATE_PROJECT_KEY
import com.memfault.bort.INTENT_EXTRA_BORT_ENABLED
import com.memfault.bort.INTENT_EXTRA_DEV_MODE_ENABLED
import com.memfault.bort.INTENT_EXTRA_PROJECT_KEY
import com.memfault.bort.INTENT_EXTRA_SERIAL
import com.memfault.bort.OverrideSerial
import com.memfault.bort.RealDevMode
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.boot.BootCountTracker
import com.memfault.bort.bugreport.RequestBugReportIntentUseCase
import com.memfault.bort.clientserver.ClientDeviceInfoSender
import com.memfault.bort.dropbox.DropBoxTagEnabler
import com.memfault.bort.requester.MetricsCollectionRequester
import com.memfault.bort.requester.PeriodicWorkRequester.PeriodicWorkManager
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.ContinuousLoggingController
import com.memfault.bort.settings.ProjectKeyChangeSource.BROADCAST
import com.memfault.bort.settings.ProjectKeyProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.SettingsUpdateRequester
import com.memfault.bort.settings.applyReporterServiceSettings
import com.memfault.bort.settings.reloadCustomEventConfigFrom
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.goAsync
import com.memfault.bort.uploader.FileUploadHoldingArea
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Base receiver to handle events that control the SDK. */
abstract class BaseControlReceiver(extraActions: Set<String>) : FilteringReceiver(
    setOf(
        INTENT_ACTION_BORT_ENABLE,
        INTENT_ACTION_BUG_REPORT_REQUESTED,
    ) + extraActions,
) {
    @Inject lateinit var dumpsterClient: DumpsterClient

    @Inject lateinit var bortEnabledProvider: BortEnabledProvider

    @Inject lateinit var periodicWorkManager: PeriodicWorkManager

    @Inject lateinit var settingsProvider: SettingsProvider

    @Inject lateinit var fileUploadHoldingArea: FileUploadHoldingArea

    @Inject lateinit var reporterServiceConnector: ReporterServiceConnector

    @Inject lateinit var metricsCollectionRequester: MetricsCollectionRequester

    @Inject lateinit var settingsUpdateRequester: SettingsUpdateRequester

    @Inject lateinit var devMode: RealDevMode

    @Inject lateinit var continuousLoggingController: ContinuousLoggingController

    @Inject lateinit var clientDeviceInfoSender: ClientDeviceInfoSender

    @Inject lateinit var appUpgrade: AppUpgrade

    @Inject lateinit var projectKeyProvider: ProjectKeyProvider

    @Inject lateinit var dropBoxEntryAddedReceiver: DropBoxEntryAddedReceiver

    @Inject lateinit var dropBoxTagEnabler: DropBoxTagEnabler

    @Inject lateinit var overrideSerial: OverrideSerial

    @Inject lateinit var bootCountTracker: BootCountTracker

    @Inject lateinit var requestBugReportIntentUseCase: RequestBugReportIntentUseCase

    private suspend fun onBortEnabled(intent: Intent, context: Context) {
        // It doesn't make sense to take any action here if bort isn't configured to require runtime enabling
        // (we would get into a bad state where jobs are cancelled, but we can not re-enable).
        if (!bortEnabledProvider.requiresRuntimeEnable()) return

        if (!intent.hasExtra(INTENT_EXTRA_BORT_ENABLED)) return
        val isNowEnabled = intent.getBooleanExtra(
            INTENT_EXTRA_BORT_ENABLED,
            false, // never used, because we just checked hasExtra()
        )
        val wasEnabled = bortEnabledProvider.isEnabled()
        Logger.test("wasEnabled=$wasEnabled isNowEnabled=$isNowEnabled")
        if (wasEnabled == isNowEnabled) {
            return
        }
        Logger.i(if (isNowEnabled) "bort.enabled" else "bort.disabled", mapOf())

        bortEnabledProvider.setEnabled(isNowEnabled)
        if (isNowEnabled) {
            dropBoxTagEnabler.enableTagsIfRequired()
        }
        fileUploadHoldingArea.handleChangeBortEnabled()
        dropBoxEntryAddedReceiver.initialize()

        applyReporterServiceSettings(
            reporterServiceConnector = reporterServiceConnector,
            settingsProvider = settingsProvider,
            bortEnabledProvider = bortEnabledProvider,
        )

        periodicWorkManager.scheduleTasksAfterBootOrEnable(bortEnabled = isNowEnabled, justBooted = false)

        dumpsterClient.setBortEnabled(isNowEnabled)
        dumpsterClient.setStructuredLogEnabled(
            isNowEnabled &&
                settingsProvider.structuredLogSettings.dataSourceEnabled,
        )
        if (isNowEnabled) {
            // If bort was just enabled, record the last reboot (which may have been a factory reset that we would
            // otherwise have missed because bort was disabled with the BOOT_COMPLETED intent fired).
            bootCountTracker.trackIfNeeded()
        }
        continuousLoggingController.configureContinuousLogging()
        // Pass the new settings to structured logging (after we enable/disable it)
        reloadCustomEventConfigFrom(settingsProvider.structuredLogSettings)
        clientDeviceInfoSender.maybeSendDeviceInfoToServer()

        appUpgrade.handleUpgrade(context)
    }

    private suspend fun onCollectMetrics() {
        if (!bortEnabledProvider.isEnabled()) return
        if (!devMode.isEnabled()) {
            Logger.d("Dev mode disabled: not collecting metrics")
            return
        }
        Logger.d("Metric collection requested")
        metricsCollectionRequester.restartPeriodicCollection(collectImmediately = true)
    }

    private fun onUpdateConfig() {
        if (!bortEnabledProvider.isEnabled()) return
        if (!devMode.isEnabled()) {
            Logger.d("Dev mode disabled: not updating config")
            return
        }
        Logger.d("Settings update requested")
        settingsUpdateRequester.restartSettingsUpdate(delayAfterSettingsUpdate = false, cancel = true)
    }

    private suspend fun onDevMode(intent: Intent, context: Context) {
        if (!bortEnabledProvider.isEnabled()) return
        if (!intent.hasExtra(INTENT_EXTRA_DEV_MODE_ENABLED)) return
        val enabled = intent.getBooleanExtra(
            INTENT_EXTRA_DEV_MODE_ENABLED,
            false, // never used, because we just checked hasExtra()
        )
        devMode.setEnabled(enabled, context)
    }

    private fun onChangeProjectKey(intent: Intent) {
        // This is allowed to run before enabling Bort (in fact this is encouraged if possible).
        val newProjectKey = intent.getStringExtra(INTENT_EXTRA_PROJECT_KEY)
        if (newProjectKey != null) {
            projectKeyProvider.setProjectKey(newKey = newProjectKey, source = BROADCAST)
        } else {
            projectKeyProvider.reset(source = BROADCAST)
        }
    }

    private fun onOverrideSerial(intent: Intent) {
        // This is allowed to run before enabling Bort (in fact this is encouraged if possible).
        val serial = intent.getStringExtra(INTENT_EXTRA_SERIAL)
        overrideSerial.overriddenSerial = serial
    }

    override fun onIntentReceived(context: Context, intent: Intent, action: String) = goAsync {
        when (action) {
            INTENT_ACTION_BUG_REPORT_REQUESTED -> requestBugReportIntentUseCase.onRequestedBugReport(intent)
            INTENT_ACTION_BORT_ENABLE -> onBortEnabled(intent, context)
            INTENT_ACTION_COLLECT_METRICS -> onCollectMetrics()
            INTENT_ACTION_UPDATE_CONFIGURATION -> onUpdateConfig()
            INTENT_ACTION_DEV_MODE -> onDevMode(intent, context)
            INTENT_ACTION_UPDATE_PROJECT_KEY -> onChangeProjectKey(intent)
            INTENT_ACTION_OVERRIDE_SERIAL -> onOverrideSerial(intent)
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
        INTENT_ACTION_OVERRIDE_SERIAL,
    ),
)

@AndroidEntryPoint
class ControlReceiver : BaseControlReceiver(emptySet())
