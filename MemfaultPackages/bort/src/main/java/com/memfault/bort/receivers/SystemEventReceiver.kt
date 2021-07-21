package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.memfault.bort.AndroidBootReason
import com.memfault.bort.BootCountTracker
import com.memfault.bort.DumpsterClient
import com.memfault.bort.LinuxRebootTracker
import com.memfault.bort.RealLastTrackedBootCountProvider
import com.memfault.bort.RealLastTrackedLinuxBootIdProvider
import com.memfault.bort.RebootEventUploader
import com.memfault.bort.logcat.RealNextLogcatStartTimeProvider
import com.memfault.bort.logcat.handleTimeChanged
import com.memfault.bort.readLinuxBootId
import com.memfault.bort.settings.applyReporterServiceSettings
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.goAsync

class SystemEventReceiver : BortEnabledFilteringReceiver(
    setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
    )
) {

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
                ::readLinuxBootId, RealLastTrackedLinuxBootIdProvider(sharedPreferences)
            ).checkAndUnset()
        ) {
            tokenBucketStoreRegistry.handleLinuxReboot()
            fileUploadHoldingArea.handleLinuxReboot()
        }

        goAsync {
            if (!bortEnabledProvider.requiresRuntimeEnable()) {
                DumpsterClient().setBortEnabled(true)
                DumpsterClient().setStructuredLogEnabled(settingsProvider.structuredLogSettings.dataSourceEnabled)
            }

            applyReporterServiceSettings(
                reporterServiceConnector,
                settingsProvider,
            )

            if (settingsProvider.rebootEventsSettings.dataSourceEnabled &&
                bortSystemCapabilities.supportsRebootEvents()
            ) {
                DumpsterClient().getprop()?.let { systemProperties ->
                    val rebootEventUploader = RebootEventUploader(
                        ingressService = ingressService,
                        deviceInfo = deviceInfoProvider.getDeviceInfo(),
                        androidSysBootReason = systemProperties.get(AndroidBootReason.SYS_BOOT_REASON_KEY),
                        tokenBucketStore = rebootEventTokenBucketStore,
                        getLinuxBootId = ::readLinuxBootId
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
    }

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> onPackageReplaced()
            Intent.ACTION_BOOT_COMPLETED -> onBootCompleted(context)
            Intent.ACTION_TIME_CHANGED -> onTimeChanged(context)
        }
    }
}
