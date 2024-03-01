package com.memfault.bort

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.memfault.bort.connectivity.BortFallbackConnectivityMetrics
import com.memfault.bort.dropbox.DropBoxTagEnabler
import com.memfault.bort.metrics.BORT_CRASH
import com.memfault.bort.metrics.BORT_STARTED
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.receivers.DropBoxEntryAddedReceiver
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.WorkManagerConfiguration
import com.memfault.bort.settings.asLoggerSettings
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.disableAppComponents
import com.memfault.bort.shared.isPrimaryUser
import com.memfault.bort.time.UptimeTracker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider
import kotlin.system.exitProcess

@HiltAndroidApp
open class Bort : Application(), Configuration.Provider {
    @Inject lateinit var metrics: BuiltinMetricsStore

    @Inject lateinit var bortEnabledProvider: BortEnabledProvider

    @Inject lateinit var uptimeTracker: UptimeTracker

    @Inject lateinit var settingsProvider: SettingsProvider

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject lateinit var installationIdProvider: InstallationIdProvider

    @Inject lateinit var appUpgrade: AppUpgrade

    @Inject lateinit var configureStrictMode: ConfigureStrictMode

    @Inject lateinit var workManagerConfiguration: WorkManagerConfiguration

    @Inject lateinit var dropBoxEntryAddedReceiver: DropBoxEntryAddedReceiver

    @Inject lateinit var projectKeySysprop: ProjectKeySysprop

    @Inject lateinit var bortFallbackConnectivityMetrics: BortFallbackConnectivityMetrics

    @Inject lateinit var dropBoxTagEnabler: DropBoxTagEnabler

    override fun onCreate() {
        super.onCreate()

        Logger.initTags(tag = "bort", testTag = "bort-test")
        configureStrictMode.configure()

        if (!isPrimaryUser()) {
            Logger.w("bort disabled for secondary user")
            disableAppComponents(applicationContext)
            exitProcess(0)
        }

        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Ensure that the metric is written to disk before the process dies; synchronous = true
            metrics.increment(BORT_CRASH)
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }

        metrics.increment(BORT_STARTED)
        logDebugInfo(bortEnabledProvider, settingsProvider)
        uptimeTracker.trackUptimeOnStart()

        // We want this to happen even if Bort is disabled.
        runBlocking {
            projectKeySysprop.loadFromSysprop()
        }

        bortFallbackConnectivityMetrics.start()
        appUpgrade.handleUpgrade(this)
        dropBoxTagEnabler.enableTagsIfRequired()
        dropBoxEntryAddedReceiver.initialize()

        if (bortEnabledProvider.isEnabled()) {
            Logger.test("Bort app running with Bort enabled")
        } else {
            Logger.test("Bort app running with Bort not enabled")
        }
    }

    private fun logDebugInfo(
        bortEnabledProvider: BortEnabledProvider,
        settingsProvider: SettingsProvider,
    ) {
        Logger.logEventBortSdkEnabled(bortEnabledProvider.isEnabled())

        with(settingsProvider) {
            Logger.initSettings(asLoggerSettings())
            Logger.initLogFile(this@Bort)
            Logger.logEvent(
                "bort-oncreate",
                "installationId=${installationIdProvider.id()}",
                "appVersionName=${sdkVersionInfo.appVersionName}",
                "appVersionCode=${sdkVersionInfo.appVersionCode}",
                "currentGitSha=${sdkVersionInfo.currentGitSha}",
                "upstreamGitSha=${sdkVersionInfo.upstreamGitSha}",
                "upstreamVersionName=${sdkVersionInfo.upstreamVersionName}",
                "upstreamVersionCode=${sdkVersionInfo.upstreamVersionCode}",
                "bugreport.enabled=${bugReportSettings.dataSourceEnabled}",
                "dropbox.enabled=${dropBoxSettings.dataSourceEnabled}",
                "bugreport.periodic.limit.period=${bugReportSettings.periodicRateLimitingPercentOfPeriod}",
            )
            Logger.i(
                "bort.oncreate",
                mapOf(
                    "appVersionName" to sdkVersionInfo.appVersionName,
                ),
            )
        }
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .setMinimumLoggingLevel(workManagerConfiguration.logLevel)
            .build()
}
