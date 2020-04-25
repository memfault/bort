package com.memfault.bort.uploader

import androidx.work.ListenableWorker
import com.memfault.bort.Logger
import com.memfault.bort.SettingsProvider
import retrofit2.HttpException
import retrofit2.Response
import java.io.File

private fun <T : Any> Response<T>.asResult(): ListenableWorker.Result =
    when (code()) {
        in 500..599 -> ListenableWorker.Result.retry()
        408 -> ListenableWorker.Result.retry()
        in 200..299 -> ListenableWorker.Result.success()
        else -> ListenableWorker.Result.failure()
    }

internal class BugReportUploader(
    private val settingsProvider: SettingsProvider,
    private val preparedUploader: PreparedUploader?,
    private val filePath: String?,
    private val maxUploadAttempts: Int = settingsProvider.maxUploadAttempts()
) {

    internal suspend fun upload(
        runAttemptCount: Int
    ): ListenableWorker.Result {
        Logger.v("uploading $filePath")
        preparedUploader ?: return deleteAndFail("No uploader available")
        val path = filePath ?: return deleteAndFail("File path is null")

        if (runAttemptCount >= maxUploadAttempts) {
            return deleteAndFail("Reached max attempts $runAttemptCount of $maxUploadAttempts")
        }

        val file = File(path)
        if (!file.exists()) {
            return deleteAndFail("File does not exist")
        }

        val prepareResponse = try {
            preparedUploader.prepare()
        } catch (e: HttpException) {
            return ListenableWorker.Result.retry()
        } catch (e: Exception) {
            return ListenableWorker.Result.retry()
        }

        when (val result = prepareResponse.asResult()) {
            is ListenableWorker.Result.Retry -> return result
            is ListenableWorker.Result.Failure -> return deleteAndFail("Prepare response failed $prepareResponse")
        }

        // Re-try for unexpected server-side response
        val prepareData = prepareResponse.body()?.data ?: return ListenableWorker.Result.retry()

        try {
            when (val result = preparedUploader.upload(file, prepareData.upload_url).asResult()) {
                is ListenableWorker.Result.Retry -> return result
                is ListenableWorker.Result.Failure -> return deleteAndFail("Upload failed")
            }
        } catch (e: HttpException) {
            return ListenableWorker.Result.retry()
        } catch (e: Exception) {
            return ListenableWorker.Result.retry()
        }

        try {
            when (val result = preparedUploader.commitBugreport(prepareData.token).asResult()) {
                is ListenableWorker.Result.Retry -> return result
                is ListenableWorker.Result.Failure -> return deleteAndFail("Upload failed")
            }
        } catch (e: HttpException) {
            return ListenableWorker.Result.retry()
        } catch (e: Exception) {
            return ListenableWorker.Result.retry()
        }

        file.delete()
        return ListenableWorker.Result.success()
    }

    private fun deleteAndFail(message: String): ListenableWorker.Result {
        Logger.e("$message file=($filePath)")
        filePath?.let {
            File(it).delete()
        }
        return ListenableWorker.Result.failure()
    }

}
