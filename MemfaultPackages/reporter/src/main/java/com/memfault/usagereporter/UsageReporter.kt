package com.memfault.usagereporter

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.os.DropBoxManager
import androidx.preference.PreferenceManager
import com.memfault.bort.shared.BuildConfigSdkVersionInfo
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.clientserver.B2BClientServer
import com.memfault.usagereporter.clientserver.B2BClientServer.Companion.create
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

        val sysProp = SystemPropertiesProxy.get(ClientServerMode.SYSTEM_PROP)
        val clientServerMode = ClientServerMode.decode(sysProp)
        // This is created in the application, rather than the service, so that it keeps running when the service
        // unbinds.
        _b2bClientServer = create(clientServerMode, this)
    }

    companion object {
        private lateinit var _b2bClientServer: B2BClientServer

        val b2bClientServer get() = _b2bClientServer
    }
}
