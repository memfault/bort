package com.memfault.bort

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.memfault.bort.requester.BugReportRequester


class Bort : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        with(appComponents().settingsProvider) {
            Logger.minLevel = minLogLevel()
            Logger.v(
                """
                Settings:
                requestIntervalHours=${bugReportRequestIntervalHours()}
                minLogLevel=${minLogLevel()}
                networkConstraint=${bugReportNetworkConstraint()}
                maxUploadAttempts=${maxUploadAttempts()}
                build=${Build.TYPE}
            """.trimIndent()
            )
        }

        if (appComponents().settingsProvider.isBuildTypeBlacklisted()) {
            Logger.d("'${BuildConfig.BLACKLISTED_BUILD_VARIANT}' build, not running")
            return
        }


        BugReportRequester(
            context = this
        ).requestPeriodic(
            appComponents().settingsProvider.bugReportRequestIntervalHours()
        )
    }

    companion object {
        private var appComponentsBuilder: AppComponents.Builder? = ComponentsBuilder()
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
