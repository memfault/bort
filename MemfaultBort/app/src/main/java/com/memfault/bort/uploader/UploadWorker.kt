package com.memfault.bort.uploader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

internal class UploadWorker(
    appContext: Context,
    private val workerParameters: WorkerParameters,
    private val retrofit: Retrofit,
    private val apiKey: String,
    private val maxUploadAttempts: Int
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preparedUploader = PreparedUploader(
            retrofit.create(PreparedUploadService::class.java),
            apiKey
        )

        return@withContext BugReportUploader(
            preparedUploader = preparedUploader,
            filePath = inputData.getString(INTENT_EXTRA_BUGREPORT_PATH),
            maxUploadAttempts = maxUploadAttempts
        ).upload(
            workerParameters.runAttemptCount
        ).also {
            Logger.v("UploadWorker result: $it")
        }
    }
}
