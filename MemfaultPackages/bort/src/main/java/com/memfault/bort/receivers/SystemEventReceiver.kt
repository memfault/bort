package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DumpsterClient
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.boot.BootCountTracker
import com.memfault.bort.boot.LinuxRebootTracker
import com.memfault.bort.clientserver.ClientDeviceInfoSender
import com.memfault.bort.dropbox.ProcessedEntryCursorProvider
import com.memfault.bort.logcat.NextLogcatStartTimeProvider
import com.memfault.bort.logcat.handleTimeChanged
import com.memfault.bort.metrics.CrashHandler
import com.memfault.bort.requester.PeriodicWorkRequester.PeriodicWorkManager
import com.memfault.bort.settings.ContinuousLoggingController
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.applyReporterServiceSettings
import com.memfault.bort.settings.reloadCustomEventConfigFrom
import com.memfault.bort.shared.goAsync
import com.memfault.bort.tokenbucket.Reboots
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.TokenBucketStoreRegistry
import com.memfault.bort.uploader.EnqueueUpload
import com.memfault.bort.uploader.FileUploadHoldingArea
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SystemEventReceiver : BortEnabledFilteringReceiver(
    setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
    ),
) {
    @Inject
    lateinit var periodicWorkManager: PeriodicWorkManager

    @Inject
    lateinit var dumpsterClient: DumpsterClient

    @Inject
    lateinit var settingsProvider: SettingsProvider

    @Inject
    lateinit var deviceInfoProvider: DeviceInfoProvider

    @Inject
    lateinit var enqueueUpload: EnqueueUpload

    @Inject
    lateinit var reporterServiceConnector: ReporterServiceConnector

    @Inject
    lateinit var fileUploadHoldingArea: FileUploadHoldingArea

    @Inject
    lateinit var tokenBucketStoreRegistry: TokenBucketStoreRegistry

    @Reboots
    @Inject
    lateinit var tokenBucketStore: TokenBucketStore

    @Inject
    lateinit var linuxRebootTracker: LinuxRebootTracker

    @Inject
    lateinit var dropBoxProcessedEntryCursorProvider: ProcessedEntryCursorProvider

    @Inject
    lateinit var continuousLoggingController: ContinuousLoggingController

    @Inject
    lateinit var clientDeviceInfoSender: ClientDeviceInfoSender

    @Inject
    lateinit var crashHandler: CrashHandler

    @Inject
    lateinit var bootCountTracker: BootCountTracker

    @Inject
    lateinit var nextLogcatStartTimeProvider: NextLogcatStartTimeProvider

    private suspend fun onPackageReplaced() {
        periodicWorkManager.scheduleTasksAfterBootOrEnable(
            bortEnabled = bortEnabledProvider.isEnabled(),
            justBooted = false,
        )
    }

    private suspend fun onBootCompleted() {
        if (linuxRebootTracker.checkAndUnset()) {
            tokenBucketStoreRegistry.handleLinuxReboot()
            fileUploadHoldingArea.handleLinuxReboot()
        }
        crashHandler.onBoot()

        val bortEnabled = bortEnabledProvider.isEnabled()
        // Note - this doesn't do anything if Bort is disabled (this method isn't called).
        dumpsterClient.setBortEnabled(bortEnabled)
        dumpsterClient.setStructuredLogEnabled(settingsProvider.structuredLogSettings.dataSourceEnabled)
        // Pass the new settings to structured logging (after we enable/disable it)
        reloadCustomEventConfigFrom(settingsProvider.structuredLogSettings)
        clientDeviceInfoSender.maybeSendDeviceInfoToServer()

        applyReporterServiceSettings(
            reporterServiceConnector = reporterServiceConnector,
            settingsProvider = settingsProvider,
            bortEnabledProvider = bortEnabledProvider,
        )

        continuousLoggingController.configureContinuousLogging()

        bootCountTracker.trackIfNeeded()

        periodicWorkManager.scheduleTasksAfterBootOrEnable(
            bortEnabled = bortEnabledProvider.isEnabled(),
            justBooted = true,
        )
    }

    private fun onTimeChanged() {
        nextLogcatStartTimeProvider.handleTimeChanged()
        dropBoxProcessedEntryCursorProvider.handleTimeChange()
    }

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) = goAsync {
        when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> onPackageReplaced()
            Intent.ACTION_BOOT_COMPLETED -> onBootCompleted()
            Intent.ACTION_TIME_CHANGED -> onTimeChanged()
        }
    }
}
