package com.memfault.usagereporter

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.memfault.bort.android.SystemPropertiesProxy
import com.memfault.bort.scopes.RootScopeBuilder
import com.memfault.bort.shared.BuildConfigSdkVersionInfo
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.LoggerSettings
import com.memfault.bort.shared.disableAppComponents
import com.memfault.bort.shared.isPrimaryUser
import com.memfault.usagereporter.clientserver.B2BClientServer
import com.memfault.usagereporter.clientserver.B2BClientServer.Companion.create
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class UsageReporter : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject lateinit var reporterSettingsPreferenceProvider: ReporterSettingsPreferenceProvider

    @Inject lateinit var logLevelPreferenceProvider: LogLevelPreferenceProvider

    @Inject lateinit var rootScopeBuilder: RootScopeBuilder

    override fun onCreate() {
        super.onCreate()

        // Reads a previously-set log level
        val minLogcatLevel = logLevelPreferenceProvider.getLogLevel()

        Logger.initTags(tag = "mflt-report", testTag = "mflt-report-test")
        Logger.initSettings(
            LoggerSettings(
                minLogcatLevel = minLogcatLevel,
                minStructuredLevel = LogLevel.INFO,
                hrtEnabled = false,
            ),
        )

        if (!isPrimaryUser()) {
            Logger.w("reporter disabled for secondary user")
            disableAppComponents(applicationContext)
            exitProcess(0)
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
                """.trimMargin(),
            )
        }

        rootScopeBuilder.onCreate("reporter-root")

        val sysProp = SystemPropertiesProxy.get(ClientServerMode.SYSTEM_PROP)
        val clientServerMode = ClientServerMode.decode(sysProp)
        Logger.test("UsageReporter started, clientServerMode=$clientServerMode")

        // This is created in the application, rather than the service, so that it keeps running when the service
        // unbinds.
        _b2bClientServer = create(clientServerMode, this, reporterSettingsPreferenceProvider)

        ReporterFileCleanupTask.schedule(this)
    }

    override fun onTerminate() {
        rootScopeBuilder.onTerminate()
        super.onTerminate()
    }

    override val workManagerConfiguration: Configuration get() = Configuration.Builder()
        .setWorkerFactory(hiltWorkerFactory)
        .build()

    companion object {

        private lateinit var _b2bClientServer: B2BClientServer
        val b2bClientServer get() = _b2bClientServer
    }
}
