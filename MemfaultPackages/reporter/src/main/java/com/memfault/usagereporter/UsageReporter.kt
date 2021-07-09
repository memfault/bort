package com.memfault.usagereporter

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.os.DropBoxManager
import androidx.preference.PreferenceManager
import com.memfault.bort.shared.BuildConfigSdkVersionInfo
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.receivers.DropBoxEntryAddedForwardingReceiver

class UsageReporter : Application() {
    override fun onCreate() {
        super.onCreate()

        Logger.TAG = "mflt-report"
        Logger.TAG_TEST = "mflt-report-test"

        // Reads a previously-set log level
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(this)
        Logger.minLogcatLevel = RealLogLevelPreferenceProvider(preferenceManager).getLogLevel()

        with(BuildConfigSdkVersionInfo) {
            Logger.v(
                """
                |Settings:
                |  minLogLevel=${Logger.minLogcatLevel}
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

        registerReceiver(
            DropBoxEntryAddedForwardingReceiver(),
            IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED)
        )
        Logger.test("UsageReporter started")
    }
}
