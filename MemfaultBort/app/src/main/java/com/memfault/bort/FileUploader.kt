package com.memfault.bort

import retrofit2.Retrofit
import java.io.File

interface FileUploader {
    suspend fun upload(file: File): TaskResult
}

interface FileUploaderFactory {
    fun create(
        retrofit: Retrofit,
        projectApiKey: String
    ): FileUploader
}
