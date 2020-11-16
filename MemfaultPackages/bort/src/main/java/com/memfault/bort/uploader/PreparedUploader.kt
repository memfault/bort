package com.memfault.bort.uploader

import com.memfault.bort.BugReportFileUploadMetadata
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FileUploadMetadata
import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.TombstoneFileUploadMetadata
import com.memfault.bort.http.ProjectKeyAuthenticated
import com.memfault.bort.shared.Logger
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Url

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

@Serializable
data class CommitRequestWithMetadata(
    val file: CommitFileToken,

    @SerialName("hardware_version")
    val hardwareVersion: String,

    @SerialName("device_serial")
    val deviceSerial: String,

    @SerialName("software_version")
    val softwareVersion: String,

    @SerialName("software_type")
    val softwareType: String = SOFTWARE_TYPE,

    val metadata: FileUploadMetadata,
)

interface PreparedUploadService {
    @POST("/api/v0/upload")
    @ProjectKeyAuthenticated
    suspend fun prepare(): Response<PrepareResult>

    @PUT
    suspend fun upload(@Url url: String, @Body file: RequestBody): Response<Unit>

    @POST("/api/v0/upload/{upload_type}")
    @ProjectKeyAuthenticated
    suspend fun commit(
        @Path(value = "upload_type") uploadType: String,
        @Body commitRequest: CommitRequest
    ): Response<Unit>

    @POST("/api/v0/upload/android-dropbox-manager-entry/{family}")
    @ProjectKeyAuthenticated
    suspend fun commitDropBoxEntry(
        @Path(value = "family") entryFamily: String,
        @Body commitRequest: CommitRequestWithMetadata
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
    private val deviceInfoProvider: DeviceInfoProvider,
    private val eventLogger: UploadEventLogger = object : UploadEventLogger {}
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
    suspend fun upload(file: File, uploadUrl: String): Response<Unit> {
        val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
        return preparedUploadService.upload(
            uploadUrl,
            requestBody
        ).also {
            eventLogger.log("upload", "done")
        }
    }

    /**
     * Commit an uploaded bug report.
     */
    suspend fun commit(token: String, metadata: FileUploadMetadata): Response<Unit> {
        return when (metadata) {
            is BugReportFileUploadMetadata ->
                preparedUploadService.commit(
                    uploadType = "bugreport",
                    commitRequest = CommitRequest(
                        CommitFileToken(
                            token
                        )
                    )
                ).also {
                    eventLogger.log("commit", "done", "bugreport")
                }
            is TombstoneFileUploadMetadata ->
                deviceInfoProvider.getDeviceInfo().let { deviceInfo ->
                    preparedUploadService.commitDropBoxEntry(
                        entryFamily = "tombstone",
                        commitRequest = CommitRequestWithMetadata(
                            file = CommitFileToken(token),
                            metadata = metadata,
                            hardwareVersion = deviceInfo.hardwareVersion,
                            deviceSerial = deviceInfo.deviceSerial,
                            softwareVersion = deviceInfo.softwareVersion,
                        )
                    ).also {
                        eventLogger.log("commit", "done", "tombstone")
                    }
                }
        }
    }
}
