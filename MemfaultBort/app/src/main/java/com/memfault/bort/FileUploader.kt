package com.memfault.bort

import androidx.work.ListenableWorker
import retrofit2.Retrofit
import java.io.File

interface FileUploader {
    suspend fun upload(file: File): ListenableWorker.Result
}

interface FileUploaderFactory {
    fun create(
        retrofit: Retrofit,
        projectApiKey: String
    ): FileUploader
}
