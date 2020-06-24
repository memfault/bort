package com.memfault.bort.uploader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memfault.bort.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

internal class UploadWorker(
    appContext: Context,
    private val workerParameters: WorkerParameters,
    private val settingsProvider: SettingsProvider,
    private val bortEnabledProvider: BortEnabledProvider,
    private val fileUploaderFactory: FileUploaderFactory,
    private val retrofit: Retrofit
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Logger.logEvent("upload", "start", workerParameters.runAttemptCount.toString())
        if (!bortEnabledProvider.isEnabled()) {
            return@withContext Result.failure()
        }

        val filePath = inputData.getString(INTENT_EXTRA_BUGREPORT_PATH)

        return@withContext DelegatingUploader(
            delegate = fileUploaderFactory.create(retrofit, settingsProvider.projectKey()),
            filePath = filePath,
            maxUploadAttempts = settingsProvider.maxUploadAttempts()
        ).upload(
            workerParameters.runAttemptCount
        ).also {
            Logger.logEvent("upload", "result", it.toString())
            "UploadWorker result: $it".also { message ->
                Logger.v(message)
                Logger.test(message)
            }
        }
    }
}
