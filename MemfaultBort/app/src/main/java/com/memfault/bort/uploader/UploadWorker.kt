package com.memfault.bort.uploader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.Logger
import com.memfault.bort.SettingsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import retrofit2.Retrofit

internal class UploadWorker(
    appContext: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    private val settingsProvider = SettingsProvider()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val httpUrl = HttpUrl.parse(settingsProvider.baseUrl())

        val retrofit: Retrofit? = httpUrl?.let {
            Retrofit.Builder()
                .baseUrl(httpUrl)
                .addConverterFactory(PreparedUploader.converterFactory())
                .build()
        }

        val preparedUploader: PreparedUploader? = retrofit?.let {
            PreparedUploader(
                retrofit.create(PreparedUploadService::class.java),
                settingsProvider.apiKey()
            )
        }

        return@withContext BugReportUploader(
            SettingsProvider(),
            preparedUploader,
            inputData.getString(INTENT_EXTRA_BUGREPORT_PATH)
        ).upload(
            workerParameters.runAttemptCount
        ).also {
            Logger.v("UploadWorker result: $it")
        }
    }
}
