package com.memfault.usagereporter

import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UsageReporter : Application(), Configuration.Provider {

    @Inject lateinit var connectivityReceiver: ConnectivityReceiver
    @Inject lateinit var dropBoxEntryAddedForwardingReceiver: DropBoxEntryAddedForwardingReceiver
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
    @Inject lateinit var reporterSettingsPreferenceProvider: ReporterSettingsPreferenceProvider
    @Inject lateinit var reporterMetrics: ReporterMetrics
    @Inject lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()

        // Reads a previously-set log level
        val minLogcatLevel = RealLogLevelPreferenceProvider(sharedPreferences).getLogLevel()

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

        val sysProp = SystemPropertiesProxy.get(ClientServerMode.SYSTEM_PROP)
        val clientServerMode = ClientServerMode.decode(sysProp)
        Logger.test("UsageReporter started, clientServerMode=$clientServerMode")

        // This is created in the application, rather than the service, so that it keeps running when the service
        // unbinds.
        _b2bClientServer = create(clientServerMode, this, reporterSettingsPreferenceProvider)

        connectivityReceiver.start()
        dropBoxEntryAddedForwardingReceiver.start()
        reporterMetrics.init()

        ReporterFileCleanupTask.schedule(this)
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setWorkerFactory(hiltWorkerFactory)
        .build()

    companion object {

        private lateinit var _b2bClientServer: B2BClientServer
        val b2bClientServer get() = _b2bClientServer
    }
}
