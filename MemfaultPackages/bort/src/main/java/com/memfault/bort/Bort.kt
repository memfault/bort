package com.memfault.bort

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.memfault.bort.shared.Logger

open class Bort : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        Logger.TAG = "bort"
        Logger.TAG_TEST = "bort-test"

        appComponentsBuilder = initComponents()

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
            Logger.minLevel = minLogLevel
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
            Logger.v(
                """
                |Settings (${this.javaClass.simpleName}):
                |  device=${appComponents().deviceIdProvider.deviceId()}
                |  minLogLevel=$minLogLevel
                |  isRuntimeEnableRequired=$isRuntimeEnableRequired
                |  eventLogEnabled=${Logger.eventLogEnabled()}
                |  build=${Build.TYPE}
                |Http Api Settings:
                |  deviceBaseUrl=${httpApiSettings.deviceBaseUrl}
                |  filesBaseUrl=${httpApiSettings.filesBaseUrl}
                |  ingressBaseUrl=${httpApiSettings.ingressBaseUrl}
                |  uploadNetworkConstraint=${httpApiSettings.uploadNetworkConstraint}
                |Device Info Settings:
                |  androidBuildVersionKey=${deviceInfoSettings.androidBuildVersionKey}
                |  androidHardwareVersionKey=${deviceInfoSettings.androidHardwareVersionKey}
                |  androidSerialNumberKey=${deviceInfoSettings.androidSerialNumberKey}
                |Bug Report Settings:
                |  dataSourceEnabled=${bugReportSettings.dataSourceEnabled}
                |  requestInterval=${bugReportSettings.requestInterval.inHours}h
                |  defaultOptions=${bugReportSettings.defaultOptions}
                |  maxUploadAttempts=${bugReportSettings.maxUploadAttempts}
                |  firstBugReportDelayAfterBoot=${bugReportSettings.firstBugReportDelayAfterBoot}
                |DropBox Settings:
                |  dataSourceEnabled=${dropBoxSettings.dataSourceEnabled}
                |Metrics Settings:
                |  dataSourceEnabled=${metricsSettings.dataSourceEnabled}
                |  collectionInterval=${metricsSettings.collectionInterval}
                |BatteryStats Settings:
                |  dataSourceEnabled=${batteryStatsSettings.dataSourceEnabled}
                |SDK Version Info:
                |  appVersionName=${sdkVersionInfo.appVersionName}
                |  appVersionCode=${sdkVersionInfo.appVersionCode}
                |  currentGitSha=${sdkVersionInfo.currentGitSha}
                |  upstreamGitSha=${sdkVersionInfo.upstreamGitSha}
                |  upstreamVersionName=${sdkVersionInfo.upstreamVersionName}
                |  upstreamVersionCode=${sdkVersionInfo.upstreamVersionCode}
                """.trimMargin()
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
