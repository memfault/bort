package com.memfault.bort

import java.io.File
import retrofit2.Retrofit

interface FileUploader {
    suspend fun upload(file: File): TaskResult
}

interface FileUploaderFactory {
    fun create(
        retrofit: Retrofit,
        projectApiKey: String
    ): FileUploader
}
