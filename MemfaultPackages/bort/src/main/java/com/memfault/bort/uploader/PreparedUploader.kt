package com.memfault.bort.uploader

import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.DropBoxEntryFileUploadPayload
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.FileUploadTokenOnly
import com.memfault.bort.HeartbeatFileUploadPayload
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.StructuredLogFileUploadPayload
import com.memfault.bort.http.ProjectKeyAuthenticated
import com.memfault.bort.http.gzip
import com.memfault.bort.shared.Logger
import java.io.File
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Url

@Serializable
data class PrepareResponseData(
    val upload_url: String,
    val token: String,
)

@Serializable
data class PrepareResult(
    val data: PrepareResponseData,
)

interface PreparedUploadService {
    @POST("/api/v0/upload")
    @ProjectKeyAuthenticated
    suspend fun prepare(): Response<PrepareResult>

    @PUT
    suspend fun upload(
        @Url url: String,
        @Body file: RequestBody,
        @Header("Content-Encoding") contentEncoding: String? = null,
    ): Response<Unit>

    @POST("/api/v0/upload/{upload_type}")
    @ProjectKeyAuthenticated
    suspend fun commit(
        @Path(value = "upload_type") uploadType: String,
        @Body payload: BugReportFileUploadPayload,
    ): Response<Unit>

    @POST("/api/v0/upload/android-logcat")
    @ProjectKeyAuthenticated
    suspend fun commitLogcat(
        @Body payload: LogcatFileUploadPayload,
    ): Response<Unit>

    @POST("/api/v0/upload/android-dropbox-manager-entry/{family}")
    @ProjectKeyAuthenticated
    suspend fun commitDropBoxEntry(
        @Path(value = "family") entryFamily: String,
        @Body payload: DropBoxEntryFileUploadPayload,
    ): Response<Unit>

    @POST("/api/v0/upload/android-structured")
    @ProjectKeyAuthenticated
    suspend fun commitStructuredLog(
        @Body payload: StructuredLogFileUploadPayload,
    ): Response<Unit>

    @POST("/api/v0/events/android-heartbeat")
    @ProjectKeyAuthenticated
    suspend fun commitHeartbeat(
        @Body commitRequest: HeartbeatFileUploadPayload,
    ): Response<Unit>

    @POST("/api/v0/upload/mar")
    @ProjectKeyAuthenticated
    suspend fun commitMar(
        @Body payload: MarFileUploadPayload,
    ): Response<Unit>
}

// Work around since vararg's can't be used in lambdas
internal interface UploadEventLogger {
    fun log(vararg strings: String) = Logger.logEvent(*strings)
}

/**
 * A client to upload files to Memfault's ("prepared") file upload API.
 */
internal class PreparedUploader(
    private val preparedUploadService: PreparedUploadService,
    private val eventLogger: UploadEventLogger = object : UploadEventLogger {},
) {

    /**
     * Prepare a file upload.
     */
    suspend fun prepare(): Response<PrepareResult> = preparedUploadService.prepare().also {
        eventLogger.log("prepare")
    }

    /**
     * Upload a prepared file from an FileInputStream or ByteArrayInputStream.
     */
    suspend fun upload(file: File, uploadUrl: String, shouldCompress: Boolean = true): Response<Unit> {
        return preparedUploadService.upload(
            url = uploadUrl,
            file = file.asRequestBody("application/octet-stream".toMediaType()).let {
                if (shouldCompress) it.gzip() else it
            },
            contentEncoding = if (shouldCompress) "gzip" else null,
        ).also {
            eventLogger.log("upload", "done")
        }
    }

    /**
     * Commit an uploaded bug report.
     */
    suspend fun commit(token: String, payload: FileUploadPayload): Response<Unit> {
        return when (payload) {
            is BugReportFileUploadPayload ->
                preparedUploadService.commit(
                    uploadType = "bugreport",
                    payload = payload.copy(file = FileUploadTokenOnly(token))
                ).also {
                    eventLogger.log("commit", "done", "bugreport")
                }
            is DropBoxEntryFileUploadPayload ->
                preparedUploadService.commitDropBoxEntry(
                    entryFamily = payload.metadata.family,
                    payload = payload.copy(file = FileUploadTokenOnly(token)),
                ).also {
                    eventLogger.log("commit", "done", payload.metadata.family)
                }
            is StructuredLogFileUploadPayload ->
                preparedUploadService.commitStructuredLog(
                    payload = payload.copy(file = FileUploadTokenOnly(token))
                )
            is HeartbeatFileUploadPayload -> {
                preparedUploadService.commitHeartbeat(
                    payload.copy(
                        attachments = payload.attachments.copy(
                            batteryStats = payload.attachments.batteryStats?.copy(
                                file = payload.attachments.batteryStats.file.copy(
                                    token = token,
                                )
                            )
                        )
                    )
                ).also {
                    eventLogger.log("commit", "done", "heartbeat")
                }
            }
            is LogcatFileUploadPayload -> {
                preparedUploadService.commitLogcat(
                    payload.copy(file = payload.file.copy(token = token))
                )
            }
            is MarFileUploadPayload -> {
                preparedUploadService.commitMar(payload.copy(file = payload.file.copy(token = token)))
            }
        }
    }
}
