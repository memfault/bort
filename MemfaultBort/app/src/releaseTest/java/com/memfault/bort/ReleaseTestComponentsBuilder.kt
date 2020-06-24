package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.memfault.bort.requester.BugReportRequestWorker
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class ReleaseTestComponentsBuilder(
    context: Context
): AppComponents.Builder(
    context
) {
    init {
        loggingInterceptor = Interceptor { chain ->
            val request: Request = chain.request()
            val t1: Long = System.nanoTime()
            Logger.v("Sending request ${request.url()} on ${chain.connection()} ${request.headers()}")
            val response: Response = chain.proceed(request)
            val t2: Long = System.nanoTime()
            Logger.v(
                """
Received response for ${response.request().url()} in ${String.format("%.1f", (t2 - t1) / 1e6)} ms
${response.headers()}
            """
            )
            response
        }

        interceptingWorkerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = when (workerClassName) {
                    BugReportRequestWorker::class.qualifiedName -> object : BugReportRequestWorker(
                        appContext,
                        workerParameters
                    ) {
                        override fun doWork(): Result {
                            Logger.i("** MFLT-TEST ** Periodic Bug Report Request")
                            return Result.success()
                        }
                    }
                    else -> null
            }
        }

        settingsProvider = PersistentSettingsProvider(
            PersistentProjectKeyProvider(
                PreferenceManager.getDefaultSharedPreferences(context)
            )
        )
    }
}

class PersistentProjectKeyProvider(
    sharedPreferences: SharedPreferences
) : PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = BuildConfig.MEMFAULT_PROJECT_API_KEY,
    preferenceKey = "test-project-api-key"
)

class PersistentSettingsProvider(
    private val persistentProjectKeyProvider: PersistentProjectKeyProvider
) : BuildConfigSettingsProvider() {
    override fun projectKey(): String = persistentProjectKeyProvider.getValue()
}