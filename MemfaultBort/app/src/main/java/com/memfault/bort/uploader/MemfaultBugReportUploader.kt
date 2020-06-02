package com.memfault.bort.uploader

import androidx.work.ListenableWorker
import com.memfault.bort.FileUploader
import com.memfault.bort.Logger
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

internal class MemfaultBugReportUploader(
    private val preparedUploader: PreparedUploader
): FileUploader {

    override suspend fun upload(file: File): ListenableWorker.Result {
        Logger.v("uploading $file")

        val prepareResponse = try {
            preparedUploader.prepare()
        } catch (e: HttpException) {
            Logger.e("prepare", e)
            return ListenableWorker.Result.retry()
        } catch (e: Exception) {
            Logger.e("prepare", e)
            return ListenableWorker.Result.retry()
        }

        when (val result = prepareResponse.asResult()) {
            is ListenableWorker.Result.Retry -> return result
            is ListenableWorker.Result.Failure -> return result
        }

        // Re-try for unexpected server-side response
        val prepareData = prepareResponse.body()?.data ?: return ListenableWorker.Result.retry()

        try {
            when (val result = preparedUploader.upload(file, prepareData.upload_url).asResult()) {
                is ListenableWorker.Result.Retry -> return result
                is ListenableWorker.Result.Failure -> return result
            }
        } catch (e: HttpException) {
            Logger.e("upload", e)
            return ListenableWorker.Result.retry()
        } catch (e: Exception) {
            Logger.e("upload", e)
            return ListenableWorker.Result.retry()
        }

        try {
            when (val result = preparedUploader.commitBugreport(prepareData.token).asResult()) {
                is ListenableWorker.Result.Retry -> return result
                is ListenableWorker.Result.Failure -> return result
            }
        } catch (e: HttpException) {
            Logger.e("commit", e)
            return ListenableWorker.Result.retry()
        } catch (e: Exception) {
            Logger.e("commit", e)
            return ListenableWorker.Result.retry()
        }

        return ListenableWorker.Result.success()
    }

}
