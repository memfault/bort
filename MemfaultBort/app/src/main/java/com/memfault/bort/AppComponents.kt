package com.memfault.bort

import android.content.Context
import android.os.Build
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.memfault.bort.uploader.MemfaultBugReportUploader
import com.memfault.bort.uploader.PreparedUploadService
import com.memfault.bort.uploader.PreparedUploader
import com.memfault.bort.uploader.UploadWorker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import okhttp3.*
import retrofit2.Converter
import retrofit2.Retrofit

// A simple holder for application components
// This example uses a service locator pattern over a DI framework for simplicity and ease of use;
// if you would prefer to see a DI framework being used, please let us know!
internal data class AppComponents(
    val settingsProvider: SettingsProvider,
    val okHttpClient: OkHttpClient,
    val retrofitClient: Retrofit,
    val projectKey: String,
    val workerFactory: WorkerFactory,
    val fileUploaderFactory: FileUploaderFactory
) {
    open class Builder {
        var apiKey: String = BuildConfig.MEMFAULT_PROJECT_API_KEY
        var baseUrl: String = BuildConfig.MEMFAULT_FILES_BASE_URL
        var settingsProvider: SettingsProvider? = null
        var networkInterceptor: Interceptor? = null
        var okHttpClient: OkHttpClient? = null
        var retrofit: Retrofit? = null
        var converterFactory: Converter.Factory = Json(JsonConfiguration.Stable)
            .asConverterFactory(MediaType.get("application/json"))
        var workerFactory: WorkerFactory? = null
        var fileUploaderFactory: FileUploaderFactory? = null

        val defaultRetrofit: Retrofit by lazy {
            Retrofit.Builder()
                .client(okHttpClient ?: defaultOkHttpClient)
                .baseUrl(HttpUrl.get(baseUrl))
                .addConverterFactory(
                    converterFactory
                )
                .build()
        }

        val defaultNetworkInterceptor: Interceptor by lazy {
            Interceptor { chain ->
                val request: Request = chain.request()
                val t1: Long = System.nanoTime()
                Logger.v("Sending request ${request.url()}")
                val response: Response = chain.proceed(request)
                val t2: Long = System.nanoTime()
                val delta = (t2 - t1) / 1e6
                Logger.v(
                    """
Received response for ${response.request().url()} in ${String.format("%.1f", delta)} ms
        """.trimEnd()
                )
                response
            }
        }

        val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .addInterceptor(networkInterceptor ?: defaultNetworkInterceptor)
                .build()
        }

        val defaultSettingsProvider: SettingsProvider by lazy {
            object: SettingsProvider {
                override fun bugReportRequestIntervalHours() = BuildConfig.BUG_REPORT_REQUEST_INTERVAL_HOURS.toLong()

                override fun minLogLevel(): LogLevel = LogLevel.fromInt(BuildConfig.MINIMUM_LOG_LEVEL) ?: LogLevel.VERBOSE

                override fun bugReportNetworkConstraint(): NetworkConstraint =
                    if (BuildConfig.UPLOAD_NETWORK_CONSTRAINT_ALLOW_METERED_CONNECTION) NetworkConstraint.CONNECTED
                    else NetworkConstraint.UNMETERED

                override fun maxUploadAttempts(): Int = BuildConfig.BUG_REPORT_MAX_UPLOAD_ATTEMPTS

                override fun isBuildTypeBlacklisted(): Boolean = Build.TYPE == BuildConfig.BLACKLISTED_BUILD_VARIANT
            }
        }

        val defaultWorkerFactory: WorkerFactory by lazy {
            object: WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? = when (workerClassName) {
                    UploadWorker::class.qualifiedName -> UploadWorker(
                        appContext = appContext,
                        workerParameters = workerParameters,
                        settingsProvider = settingsProvider ?: defaultSettingsProvider,
                        retrofit = retrofit ?: defaultRetrofit,
                        projectKey = apiKey,
                        fileUploaderFactory = fileUploaderFactory ?: defaultFileUploaderFactory
                    )
                    // Delegate to the default worker factory
                    else -> null
                }
            }
        }

        val defaultFileUploaderFactory: FileUploaderFactory by lazy {
            object : FileUploaderFactory {
                override fun create(retrofit: Retrofit, projectApiKey: String): FileUploader =
                    MemfaultBugReportUploader(
                        preparedUploader = PreparedUploader(
                            retrofit.create(PreparedUploadService::class.java),
                            projectApiKey
                        )
                    )
            }
        }

        fun build(): AppComponents {
            return AppComponents(
                settingsProvider =  settingsProvider ?: defaultSettingsProvider,
                okHttpClient = okHttpClient ?: defaultOkHttpClient,
                retrofitClient = retrofit ?: defaultRetrofit,
                projectKey = apiKey,
                workerFactory = workerFactory ?: defaultWorkerFactory,
                fileUploaderFactory = fileUploaderFactory ?: defaultFileUploaderFactory
            )
        }
    }
}
