package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.memfault.bort.AndroidBootReason
import com.memfault.bort.BootCountTracker
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DumpsterClient
import com.memfault.bort.INTENT_ACTION_BOOT_COMPLETED
import com.memfault.bort.INTENT_ACTION_MY_PACKAGE_REPLACED
import com.memfault.bort.RealLastTrackedBootCountProvider
import com.memfault.bort.RebootEventUploader
import com.memfault.bort.requester.BugReportRequester
import com.memfault.bort.shared.Logger

class SystemEventReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BOOT_COMPLETED, INTENT_ACTION_MY_PACKAGE_REPLACED)
) {

    private fun onPackageReplaced(context: Context) {
        BugReportRequester(
            context
        ).requestPeriodic(
            settingsProvider.bugReportSettings.requestIntervalHours,
            settingsProvider.bugReportSettings.defaultOptions
        )
    }

    private fun onBootCompleted(context: Context) {
        Logger.logEvent("boot")

        goAsync {
            DumpsterClient().getprop()?.let { systemProperties ->
                val rebootEventUploader = RebootEventUploader(
                    ingressService = ingressService,
                    deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
                        settingsProvider.deviceInfoSettings, systemProperties
                    ),
                    androidSysBootReason = systemProperties.get(AndroidBootReason.SYS_BOOT_REASON_KEY)
                )
                val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
                BootCountTracker(
                    lastTrackedBootCountProvider = RealLastTrackedBootCountProvider(
                        PreferenceManager.getDefaultSharedPreferences(context)
                    ),
                    untrackedBootCountHandler = rebootEventUploader::handleUntrackedBootCount
                ).trackIfNeeded(bootCount)
            }
        }

        BugReportRequester(
            context
        ).requestPeriodic(
            settingsProvider.bugReportSettings.requestIntervalHours,
            settingsProvider.bugReportSettings.defaultOptions,
            settingsProvider.bugReportSettings.firstBugReportDelayAfterBootMinutes
        )
    }

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        when (action) {
            INTENT_ACTION_MY_PACKAGE_REPLACED -> onPackageReplaced(context)
            INTENT_ACTION_BOOT_COMPLETED -> onBootCompleted(context)
        }
    }
}
