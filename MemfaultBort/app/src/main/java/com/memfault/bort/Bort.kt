package com.memfault.bort

import android.app.Application
import android.content.Context
import androidx.work.*
import com.memfault.bort.requester.BugReportRequester
import com.memfault.bort.uploader.UploadWorker
import okhttp3.OkHttpClient
import retrofit2.Retrofit



internal class BortWorkerFactory: WorkerFactory() {
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

class Bort : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        serviceLocator = SimpleServiceLocator.from(
            object: SettingsProvider {}
        )

        Logger.minLevel = serviceLocator().settingsProvider().minLogLevel()
        Logger.v("onCreate")

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
