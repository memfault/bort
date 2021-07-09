package com.memfault.bort

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.memfault.bort.metrics.BORT_CRASH
import com.memfault.bort.metrics.BORT_STARTED
import com.memfault.bort.metrics.metrics
import com.memfault.bort.settings.selectSettingsToMap
import com.memfault.bort.shared.Logger

open class Bort : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Ensure that the metric is written to disk before the process dies; synchronous = true
            metrics()?.increment(BORT_CRASH, synchronous = true)
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }

        Logger.TAG = "bort"
        Logger.TAG_TEST = "bort-test"

        appComponentsBuilder = initComponents()

        metrics()?.increment(BORT_STARTED)
        logDebugInfo()

        if (!appComponents().isEnabled()) {
            Logger.test("Bort not enabled, not running app")
            return
        }
    }

    open fun initComponents(): AppComponents.Builder = AppComponents.Builder(this)

    private fun logDebugInfo() {
        Logger.logEventBortSdkEnabled(appComponents().isEnabled())

        with(appComponents().settingsProvider) {
            Logger.minLogcatLevel = minLogcatLevel
            Logger.minStructuredLevel = minStructuredLogLevel
            Logger.eventLogEnabled = this::eventLogEnabled
            Logger.logEvent(
                "bort-oncreate",
                "device=${appComponents().deviceIdProvider.deviceId()}",
                "appVersionName=${sdkVersionInfo.appVersionName}",
                "appVersionCode=${sdkVersionInfo.appVersionCode}",
                "currentGitSha=${sdkVersionInfo.currentGitSha}",
                "upstreamGitSha${sdkVersionInfo.upstreamGitSha}",
                "upstreamVersionName=${sdkVersionInfo.upstreamVersionName}",
                "upstreamVersionCode=${sdkVersionInfo.upstreamVersionCode}",
                "bugreport.enabled=${bugReportSettings.dataSourceEnabled}",
                "dropbox.enabled=${dropBoxSettings.dataSourceEnabled}",
            )
            Logger.i(
                "bort.oncreate",
                selectSettingsToMap() + mapOf(
                    "device" to appComponents().deviceIdProvider.deviceId(),
                    "build" to Build.TYPE,
                    "appVersionName" to sdkVersionInfo.appVersionName,
                    "appVersionCode" to sdkVersionInfo.appVersionCode,
                    "currentGitSha" to sdkVersionInfo.currentGitSha,
                    "upstreamGitSha" to sdkVersionInfo.upstreamGitSha,
                    "upstreamVersionName" to sdkVersionInfo.upstreamVersionName,
                    "upstreamVersionCode" to sdkVersionInfo.upstreamVersionCode,
                )
            )
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak") // Statically hold the application context only
        private var appComponentsBuilder: AppComponents.Builder? = null
        @Volatile private var appComponents: AppComponents? = null

        internal fun appComponents(): AppComponents {
            appComponents?.let {
                return it
            }
            return synchronized(this) {
                val components = appComponents
                components?.let {
                    return components
                }
                val created = checkNotNull(appComponentsBuilder).build()
                appComponents = created
                appComponentsBuilder = null
                created
            }
        }

        internal fun updateComponents(builder: AppComponents.Builder) {
            synchronized(this) {
                appComponents = null
                appComponentsBuilder = builder
            }
        }
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(
                // Create a WorkerFactory provider that provides a fresh WorkerFactory. This
                // ensures the WorkerFactory is always using fresh app components.
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker? =
                        appComponents().workerFactory.createWorker(
                            appContext,
                            workerClassName,
                            workerParameters
                        )
                }
            ).build()
}
