package com.memfault.usagereporter

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import android.os.DropBoxManager
import androidx.preference.PreferenceManager
import com.memfault.bort.shared.BuildConfigSdkVersionInfo
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.LoggerSettings
import com.memfault.bort.shared.disableAppComponents
import com.memfault.bort.shared.isPrimaryUser
import com.memfault.usagereporter.clientserver.B2BClientServer
import com.memfault.usagereporter.clientserver.B2BClientServer.Companion.create
import com.memfault.usagereporter.metrics.ReporterMetrics
import com.memfault.usagereporter.receivers.ConnectivityReceiver
import com.memfault.usagereporter.receivers.DropBoxEntryAddedForwardingReceiver

class UsageReporter : Application() {
    override fun onCreate() {
        super.onCreate()

        // Reads a previously-set log level
        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(this)
        val minLogcatLevel = RealLogLevelPreferenceProvider(preferenceManager).getLogLevel()

        Logger.initTags(tag = "mflt-report", testTag = "mflt-report-test")
        Logger.initSettings(
            LoggerSettings(
                eventLogEnabled = true,
                logToDisk = false,
                minLogcatLevel = minLogcatLevel,
                minStructuredLevel = LogLevel.INFO,
                hrtEnabled = false,
            )
        )

        if (!isPrimaryUser()) {
            Logger.w("reporter disabled for secondary user")
            disableAppComponents(applicationContext)
            System.exit(0)
        }

        with(BuildConfigSdkVersionInfo) {
            Logger.v(
                """
                |Settings:
                |  minLogLevel=$minLogcatLevel
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
            DropBoxEntryAddedForwardingReceiver(RealDropBoxFilterSettingsProvider(preferenceManager)),
            IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED)
        )
        ConnectivityReceiver().register(this)

        val sysProp = SystemPropertiesProxy.get(ClientServerMode.SYSTEM_PROP)
        val clientServerMode = ClientServerMode.decode(sysProp)
        Logger.test("UsageReporter started, clientServerMode=$clientServerMode")

        // This is created in the application, rather than the service, so that it keeps running when the service
        // unbinds.
        _reporterSettings = ReporterSettingsPreferenceProvider(PreferenceManager.getDefaultSharedPreferences(this))
        _b2bClientServer = create(clientServerMode, this, _reporterSettings)
        _reporterMetrics = ReporterMetrics.create(this)

        ReporterFileCleanupTask.schedule(this)
    }

    companion object {
        private lateinit var _b2bClientServer: B2BClientServer
        private lateinit var _reporterMetrics: ReporterMetrics
        private lateinit var _reporterSettings: ReporterSettingsPreferenceProvider

        val b2bClientServer get() = _b2bClientServer
        val reporterMetrics get() = _reporterMetrics
        val writableReporterSettings get() = _reporterSettings
        val reporterSettings: ReporterSettings get() = _reporterSettings
    }
}
