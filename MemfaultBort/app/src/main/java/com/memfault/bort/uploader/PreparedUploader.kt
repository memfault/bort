package com.memfault.bort.uploader

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import okhttp3.MediaType
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import java.io.File

internal const val PROJECT_KEY_HEADER = "Memfault-Project-Key"

@Serializable
data class PrepareResponseData(
    val upload_url: String,
    val token: String
)

@Serializable
data class PrepareResult(
    val data: PrepareResponseData
)

@Serializable
data class CommitFileToken(
    val token: String
)

@Serializable
data class CommitRequest(
    val file: CommitFileToken
)

interface PreparedUploadService {
    @POST("/api/v0/upload")
    suspend fun prepare(@Header(PROJECT_KEY_HEADER) apiKey: String): Response<PrepareResult>

    @PUT
    suspend fun upload(@Url url: String, @Body file: RequestBody): Response<Unit>

    @POST("/api/v0/upload/{upload_type}")
    suspend fun commit(
        @Header("Memfault-Project-Key") apiKey: String,
        @Path(value = "upload_type") uploadType: String,
        @Body commitRequest: CommitRequest
    ): Response<Unit>
}

/**
 * A client to upload files to Memfault's ("prepared") file upload API.
 */
internal class PreparedUploader(
    private val preparedUploadService: PreparedUploadService,
    private val apiKey: String
) {
    companion object {
        internal fun converterFactory() =
            Json(JsonConfiguration.Stable).asConverterFactory(MediaType.get("application/json"))
    }

    /**
     * Prepare a file upload.
     */
    suspend fun prepare(): Response<PrepareResult> =
        preparedUploadService.prepare(apiKey)

    /**
     * Upload a prepared file.
     */
    suspend fun upload(file: File, uploadUrl: String): Response<Unit> {
        val requestBody = RequestBody.create(MediaType.get("application/octet-stream"), file)
        return preparedUploadService.upload(
            uploadUrl,
            requestBody
        )
    }

    /**
     * Commit an uploaded bug report.
     */
    suspend fun commitBugreport(token: String): Response<Unit> =
        preparedUploadService.commit(
            apiKey = apiKey,
            uploadType = "bugreport",
            commitRequest = CommitRequest(
                CommitFileToken(
                    token
                )
            )
        )
}
