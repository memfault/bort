package com.memfault.bort.uploader

import com.memfault.bort.FileUploadMetadata
import com.memfault.bort.FileUploader
import com.memfault.bort.TaskResult
import com.memfault.bort.asResult
import com.memfault.bort.shared.Logger
import java.io.File
import retrofit2.HttpException

internal class MemfaultFileUploader(
    private val preparedUploader: PreparedUploader
) : FileUploader {
    override suspend fun upload(file: File, metadata: FileUploadMetadata): TaskResult {
        Logger.v("uploading $file")

        val prepareResponse = try {
            preparedUploader.prepare()
        } catch (e: HttpException) {
            Logger.e("prepare", e)
            return TaskResult.RETRY
        } catch (e: Exception) {
            Logger.e("prepare", e)
            return TaskResult.RETRY
        }

        when (val result = prepareResponse.asResult()) {
            TaskResult.RETRY -> return result
            TaskResult.FAILURE -> return result
        }

        // Re-try for unexpected server-side response
        val prepareData = prepareResponse.body()?.data ?: return TaskResult.RETRY

        try {
            when (val result = preparedUploader.upload(file, prepareData.upload_url).asResult()) {
                TaskResult.RETRY -> return result
                TaskResult.FAILURE -> return result
            }
        } catch (e: HttpException) {
            Logger.e("upload", e)
            return TaskResult.RETRY
        } catch (e: Exception) {
            Logger.e("upload", e)
            return TaskResult.RETRY
        }

        try {
            when (val result = preparedUploader.commit(prepareData.token, metadata).asResult()) {
                TaskResult.RETRY -> return result
                TaskResult.FAILURE -> return result
            }
        } catch (e: HttpException) {
            Logger.e("commit", e)
            return TaskResult.RETRY
        } catch (e: Exception) {
            Logger.e("commit", e)
            return TaskResult.RETRY
        }

        return TaskResult.SUCCESS
    }
}
