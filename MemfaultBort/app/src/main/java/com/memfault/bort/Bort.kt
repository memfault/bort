package com.memfault.bort

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.memfault.bort.requester.BugReportRequester
import com.memfault.bort.uploader.UploadWorker


internal class BortWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        UploadWorker::class.qualifiedName -> UploadWorker(
            appContext,
            workerParameters,
            Bort.serviceLocator().retrofitClient(),
            BuildConfig.MEMFAULT_PROJECT_API_KEY,
            Bort.serviceLocator().settingsProvider().maxUploadAttempts()
        )
        // Delegate to the default worker factory
        else -> null
    }
}

fun isBuildTypeBlacklisted() = Build.TYPE == BuildConfig.BLACKLISTED_BUILD_VARIANT

class Bort : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        serviceLocator = SimpleServiceLocator.from(
            object : SettingsProvider {}
        )

        Logger.minLevel = serviceLocator().settingsProvider().minLogLevel()
        with(serviceLocator.settingsProvider()) {
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

        if (isBuildTypeBlacklisted()) {
            Logger.d("'${BuildConfig.BLACKLISTED_BUILD_VARIANT}' build, not running")
            return
        }


        BugReportRequester(
            context = this
        ).requestPeriodic(
            serviceLocator().settingsProvider().bugReportRequestIntervalHours()
        )
    }

    companion object {
        private lateinit var serviceLocator: ServiceLocator

        @JvmStatic
        fun serviceLocator() = serviceLocator
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setWorkerFactory(BortWorkerFactory())
        .build()
}
