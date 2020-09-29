package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.memfault.bort.*
import com.memfault.bort.requester.BugReportRequester
import com.memfault.bort.shared.Logger

class SystemEventReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BOOT_COMPLETED, INTENT_ACTION_MY_PACKAGE_REPLACED)
) {

    private fun onPackageReplaced(context: Context) {
        BugReportRequester(
            context
        ).requestPeriodic(
            settingsProvider.bugReportRequestIntervalHours()
        )
    }

    private fun onBootCompleted(context: Context) {
        Logger.logEvent("boot")

        goAsync {
            DumpsterClient().getprop()?.let { systemProperties ->
                val rebootEventUploader = RebootEventUploader(
                    ingressService = ingressService,
                    deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(settingsProvider, systemProperties),
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
            settingsProvider.bugReportRequestIntervalHours(),
            settingsProvider.firstBugReportDelayAfterBootMinutes()
        )
    }

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        when (action) {
            INTENT_ACTION_MY_PACKAGE_REPLACED -> onPackageReplaced(context)
            INTENT_ACTION_BOOT_COMPLETED -> onBootCompleted(context)
        }
    }
}
