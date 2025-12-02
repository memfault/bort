package com.memfault.bort.uploader

import com.memfault.bort.FileUploader
import com.memfault.bort.Payload
import com.memfault.bort.TaskResult
import com.memfault.bort.asResult
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class)
class MemfaultFileUploader @Inject constructor(
    private val preparedUploader: PreparedUploader,
) : FileUploader {

    override suspend fun upload(
        file: File,
        payload: Payload,
        shouldCompress: Boolean,
    ): TaskResult {
        val prepareResponse = try {
            preparedUploader.prepare(file, payload.kind())
        } catch (e: HttpException) {
            Logger.e("Upload failed due to HTTP error $e", e)
            return TaskResult.RETRY
        } catch (e: Exception) {
            Logger.e("Upload failed due to error $e", e)
            return TaskResult.RETRY
        }

        when (val result = prepareResponse.asResult()) {
            TaskResult.RETRY -> return result
            TaskResult.FAILURE -> return result
            else -> {}
        }

        // Re-try for unexpected server-side response
        val prepareData = prepareResponse.body()?.data ?: return TaskResult.RETRY

        try {
            when (val result = preparedUploader.upload(file, prepareData.upload_url, shouldCompress).asResult()) {
                TaskResult.RETRY -> return result
                TaskResult.FAILURE -> return result
                else -> {}
            }
        } catch (e: HttpException) {
            Logger.e("upload", e)
            return TaskResult.RETRY
        } catch (e: Exception) {
            Logger.e("upload", e)
            return TaskResult.RETRY
        }

        try {
            when (val result = preparedUploader.commit(prepareData.token, payload).asResult()) {
                TaskResult.RETRY -> return result
                TaskResult.FAILURE -> return result
                else -> {}
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
