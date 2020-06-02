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
    private val fileUploaderFactory: FileUploaderFactory,
    private val retrofit: Retrofit,
    private val projectKey: String
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (settingsProvider.isBuildTypeBlacklisted()) {
            return@withContext Result.failure()
        }

        val filePath = inputData.getString(INTENT_EXTRA_BUGREPORT_PATH)

        return@withContext DelegatingUploader(
            delegate = fileUploaderFactory.create(retrofit, projectKey),
            filePath = filePath,
            maxUploadAttempts = settingsProvider.maxUploadAttempts()
        ).upload(
            workerParameters.runAttemptCount
        ).also {
            Logger.v("UploadWorker result: $it")
            Logger.test("UploadWorker result: $it")
        }
    }
}
