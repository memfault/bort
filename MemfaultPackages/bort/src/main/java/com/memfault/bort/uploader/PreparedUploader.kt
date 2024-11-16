package com.memfault.bort.uploader

import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.Payload
import com.memfault.bort.Payload.MarPayload
import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.http.ProjectKeyAuthenticated
import com.memfault.bort.http.gzip
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url
import java.io.File
import javax.inject.Inject

enum class PrepareFileKind(val value: String) {
    MAR("mar"),
}

@Serializable
data class PrepareRequestData(
    val device: Device,
    val kind: PrepareFileKind,
    val size: Long,
) {
    @Serializable
    data class Device(
        @SerialName("hardware_version")
        val hardwareVersion: String,
        @SerialName("software_version")
        val softwareVersion: String,
        @SerialName("software_type")
        val softwareType: String,
        @SerialName("device_serial")
        val deviceSerial: String,
    )
}

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
    suspend fun prepare(
        @Body args: PrepareRequestData,
    ): Response<PrepareResult>

    @PUT
    suspend fun upload(
        @Url url: String,
        @Body file: RequestBody,
        @Header("Content-Encoding") contentEncoding: String? = null,
    ): Response<Unit>

    @POST("/api/v0/upload/mar")
    @ProjectKeyAuthenticated
    suspend fun commitMar(
        @Body payload: MarFileUploadPayload,
    ): Response<Unit>
}

/**
 * A client to upload files to Memfault's ("prepared") file upload API.
 */
class PreparedUploader @Inject constructor(
    private val preparedUploadService: PreparedUploadService,
    private val deviceInfoProvider: DeviceInfoProvider,
) {

    /**
     * Prepare a file upload.
     */
    suspend fun prepare(
        file: File,
        kind: PrepareFileKind,
    ): Response<PrepareResult> =
        preparedUploadService.prepare(
            PrepareRequestData(
                device = deviceInfoProvider.getDeviceInfo().asDevice(),
                kind = kind,
                size = file.length(),
            ),
        )

    /**
     * Upload a prepared file from an FileInputStream or ByteArrayInputStream.
     */
    suspend fun upload(
        file: File,
        uploadUrl: String,
        shouldCompress: Boolean = true,
    ): Response<Unit> =
        preparedUploadService.upload(
            url = uploadUrl,
            file = file.asRequestBody("application/octet-stream".toMediaType()).let {
                if (shouldCompress) it.gzip() else it
            },
            contentEncoding = if (shouldCompress) "gzip" else null,
        )

    /**
     * Commit an uploaded bug report.
     */
    suspend fun commit(token: String, wrapper: Payload): Response<Unit> = when (wrapper) {
        is MarPayload -> {
            preparedUploadService.commitMar(wrapper.payload.copy(file = wrapper.payload.file.copy(token = token)))
        }
    }
}

fun DeviceInfo.asDevice() = PrepareRequestData.Device(
    hardwareVersion = hardwareVersion,
    deviceSerial = deviceSerial,
    softwareVersion = softwareVersion,
    softwareType = SOFTWARE_TYPE,
)

fun Payload.kind() = when (this) {
    is MarPayload -> PrepareFileKind.MAR
}
