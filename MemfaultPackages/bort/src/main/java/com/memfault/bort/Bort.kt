package com.memfault.bort

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.memfault.bort.dropbox.DropBoxTagEnabler
import com.memfault.bort.metrics.BORT_CRASH
import com.memfault.bort.metrics.BORT_STARTED
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.receivers.DropBoxEntryAddedReceiver
import com.memfault.bort.scopes.RootScopeBuilder
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.BortWorkManagerConfiguration
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.asLoggerSettings
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.disableAppComponents
import com.memfault.bort.shared.isPrimaryUser
import com.memfault.bort.time.UptimeTracker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
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

    @Inject lateinit var bortWorkManagerConfig: BortWorkManagerConfiguration

    @Inject lateinit var rootScopeBuilder: RootScopeBuilder

    @Inject lateinit var dropBoxEntryAddedReceiver: DropBoxEntryAddedReceiver

    @Inject lateinit var projectKeySysprop: ProjectKeySysprop

    @Inject lateinit var dropBoxTagEnabler: DropBoxTagEnabler

    override fun onCreate() {
        super.onCreate()

        Logger.initTags(tag = "bort", testTag = "bort-test")

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
        logDebugInfo(settingsProvider)
        uptimeTracker.trackUptimeOnStart()

        // We want this to happen even if Bort is disabled.
        runBlocking {
            projectKeySysprop.loadFromSysprop()
        }

        rootScopeBuilder.onCreate("bort-root")

        appUpgrade.handleUpgrade(this)
        dropBoxTagEnabler.enableTagsIfRequired()
        dropBoxEntryAddedReceiver.initialize()

        if (bortEnabledProvider.isEnabled()) {
            Logger.test("Bort app running with Bort enabled")
        } else {
            Logger.test("Bort app running with Bort not enabled")
        }
    }

    override fun onTerminate() {
        rootScopeBuilder.onTerminate()
        super.onTerminate()
    }

    private fun logDebugInfo(
        settingsProvider: SettingsProvider,
    ) {
        with(settingsProvider) {
            Logger.initSettings(asLoggerSettings())
            Logger.i(
                "bort.oncreate",
                mapOf(
                    "installationId" to installationIdProvider.id(),
                    "appVersionName" to sdkVersionInfo.appVersionName,
                    "appVersionCode" to sdkVersionInfo.appVersionCode,
                    "currentGitSha" to sdkVersionInfo.currentGitSha,
                    "upstreamGitSha" to sdkVersionInfo.upstreamGitSha,
                    "upstreamVersionName" to sdkVersionInfo.upstreamVersionName,
                    "upstreamVersionCode" to sdkVersionInfo.upstreamVersionCode,
                    "bugreport.enabled" to bugReportSettings.dataSourceEnabled,
                    "dropbox.enabled" to dropBoxSettings.dataSourceEnabled,
                    "bugreport.periodic.limit.period" to bugReportSettings.periodicRateLimitingPercentOfPeriod,
                ),
            )
        }
    }

    override val workManagerConfiguration: Configuration get() =
        Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .setMinimumLoggingLevel(bortWorkManagerConfig.logLevel)
            .build()
}
