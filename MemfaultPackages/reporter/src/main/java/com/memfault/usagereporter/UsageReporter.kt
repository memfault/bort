package com.memfault.usagereporter

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.os.DropBoxManager
import com.memfault.bort.shared.BuildConfigSdkVersionInfo
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.receivers.DropBoxEntryAddedForwardingReceiver

class UsageReporter : Application() {
    override fun onCreate() {
        super.onCreate()

        Logger.TAG = "mflt-report"
        Logger.TAG_TEST = "mflt-report-test"
        Logger.minLevel = LogLevel.fromInt(BuildConfig.MINIMUM_LOG_LEVEL) ?: LogLevel.NONE

        with(BuildConfigSdkVersionInfo) {
            Logger.v(
                """
                |Settings:
                |  minLogLevel=${Logger.minLevel}
                |  build=${Build.TYPE}
                |SDK Version Info:
                |  appVersionName=$appVersionName
                |  appVersionCode=$appVersionCode
                |  currentGitSha=$currentGitSha
                |  upstreamGitSha=$upstreamGitSha
                |  upstreamVersionName=$upstreamVersionName
                |  upstreamVersionCode=$upstreamVersionCode
                """.trimMargin()
            )
        }

        Logger.v("Registering for DropBoxManager intents")
        registerReceiver(
            DropBoxEntryAddedForwardingReceiver(),
            IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED)
        )
    }
}
