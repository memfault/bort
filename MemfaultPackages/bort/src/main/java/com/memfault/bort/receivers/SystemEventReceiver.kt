package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.memfault.bort.AndroidBootReason
import com.memfault.bort.BootCountTracker
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DumpsterClient
import com.memfault.bort.InjectSet
import com.memfault.bort.LinuxBootId
import com.memfault.bort.LinuxRebootTracker
import com.memfault.bort.RealLastTrackedBootCountProvider
import com.memfault.bort.RealLastTrackedLinuxBootIdProvider
import com.memfault.bort.RebootEventUploader
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.dropbox.ProcessedEntryCursorProvider
import com.memfault.bort.logcat.RealNextLogcatStartTimeProvider
import com.memfault.bort.logcat.handleTimeChanged
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.applyReporterServiceSettings
import com.memfault.bort.shared.Logger
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
    )
) {
    @Inject lateinit var periodicWorkRequesters: InjectSet<PeriodicWorkRequester>
    @Inject lateinit var dumpsterClient: DumpsterClient
    @Inject lateinit var settingsProvider: SettingsProvider
    @Inject lateinit var deviceInfoProvider: DeviceInfoProvider
    @Inject lateinit var enqueueUpload: EnqueueUpload
    @Inject lateinit var reporterServiceConnector: ReporterServiceConnector
    @Inject lateinit var fileUploadHoldingArea: FileUploadHoldingArea
    @Inject lateinit var tokenBucketStoreRegistry: TokenBucketStoreRegistry
    @Reboots @Inject lateinit var tokenBucketStore: TokenBucketStore
    @Inject lateinit var bortSystemCapabilities: BortSystemCapabilities
    @Inject lateinit var readLinuxBootId: LinuxBootId
    @Inject lateinit var dropBoxProcessedEntryCursorProvider: ProcessedEntryCursorProvider

    private fun onPackageReplaced() {
        goAsync {
            periodicWorkRequesters.forEach {
                it.startPeriodic()
            }
        }
    }

    private fun onBootCompleted(context: Context) {
        Logger.logEvent("boot")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        if (LinuxRebootTracker(
                readLinuxBootId,
                RealLastTrackedLinuxBootIdProvider(sharedPreferences)
            ).checkAndUnset()
        ) {
            tokenBucketStoreRegistry.handleLinuxReboot()
            fileUploadHoldingArea.handleLinuxReboot()
        }

        goAsync {
            val bortEnabled = !bortEnabledProvider.requiresRuntimeEnable() ||
                bortEnabledProvider.isEnabled()
            dumpsterClient.setBortEnabled(bortEnabled)
            dumpsterClient.setStructuredLogEnabled(settingsProvider.structuredLogSettings.dataSourceEnabled)

            applyReporterServiceSettings(
                reporterServiceConnector,
                settingsProvider,
                bortEnabledProvider,
            )

            if (settingsProvider.rebootEventsSettings.dataSourceEnabled &&
                bortSystemCapabilities.supportsRebootEvents()
            ) {
                dumpsterClient.getprop()?.let { systemProperties ->
                    val rebootEventUploader = RebootEventUploader(
                        deviceInfo = deviceInfoProvider.getDeviceInfo(),
                        androidSysBootReason = systemProperties.get(AndroidBootReason.SYS_BOOT_REASON_KEY),
                        tokenBucketStore = tokenBucketStore,
                        getLinuxBootId = readLinuxBootId,
                        enqueueUpload = enqueueUpload,
                    )

                    val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
                    BootCountTracker(
                        lastTrackedBootCountProvider = RealLastTrackedBootCountProvider(
                            sharedPreferences = sharedPreferences
                        ),
                        untrackedBootCountHandler = rebootEventUploader::handleUntrackedBootCount
                    ).trackIfNeeded(bootCount)
                }
            }

            periodicWorkRequesters.forEach {
                it.startPeriodic(justBooted = true)
            }
        }
    }

    private fun onTimeChanged(context: Context) {
        RealNextLogcatStartTimeProvider(
            PreferenceManager.getDefaultSharedPreferences(context)
        ).handleTimeChanged()
        dropBoxProcessedEntryCursorProvider.handleTimeChange()
    }

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> onPackageReplaced()
            Intent.ACTION_BOOT_COMPLETED -> onBootCompleted(context)
            Intent.ACTION_TIME_CHANGED -> onTimeChanged(context)
        }
    }
}
