package com.memfault.bort.ota

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.memfault.bort.ota.lib.OtaLoggerSettings
import com.memfault.bort.ota.lib.UpdateActionHandler
import com.memfault.bort.ota.lib.Updater
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg.LATEST_VALUE
import com.memfault.bort.scopes.RootScopeBuilder
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.LoggerSettings
import com.memfault.bort.shared.disableAppComponents
import com.memfault.bort.shared.isPrimaryUser
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class OtaApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var updater: Updater

    @Inject lateinit var actionHandler: UpdateActionHandler

    @Inject lateinit var otaLoggerSettings: OtaLoggerSettings

    @Inject lateinit var rootScopeBuilder: RootScopeBuilder

    override fun onCreate() {
        super.onCreate()

        Logger.initTags(tag = "bort-ota", testTag = "bort-ota-test")
        Logger.initSettings(
            LoggerSettings(
                eventLogEnabled = true,
                logToDisk = false,
                minLogcatLevel = otaLoggerSettings.minLogcatLevel,
                minStructuredLevel = LogLevel.INFO,
                hrtEnabled = false,
            ),
        )

        if (!isPrimaryUser()) {
            Logger.w("bort-ota disabled for secondary user")
            disableAppComponents(applicationContext)
            exitProcess(0)
        }

        rootScopeBuilder.onCreate("ota-root")

        val autoInstallMetric = Reporting.report()
            .boolStateTracker(name = "ota_auto_install", aggregations = listOf(LATEST_VALUE), internal = true)
        autoInstallMetric.state(BuildConfig.OTA_AUTO_INSTALL)

        actionHandler.initialize()
    }

    override fun onTerminate() {
        rootScopeBuilder.onTerminate()
        super.onTerminate()
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
