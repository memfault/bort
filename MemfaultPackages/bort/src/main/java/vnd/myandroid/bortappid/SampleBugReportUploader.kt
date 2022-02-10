package vnd.myandroid.bortappid // Update to match your package

import com.memfault.bort.FileUploadPayload
import com.memfault.bort.FileUploader
import com.memfault.bort.Payload
import com.memfault.bort.TaskResult
import com.memfault.bort.shared.Logger
import java.io.File
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

private const val BASE_URL = "https://my.base.url"

interface SampleUploadService {
    @Multipart
    @POST("api/v0/upload")
    suspend fun upload(@Part file: RequestBody, @Part metadata: RequestBody): Response<Unit>
}

fun Payload.asRequestBody() =
    Json.encodeToString(
        FileUploadPayload.serializer(),
        (this as Payload.LegacyPayload).payload
    ).toRequestBody("application/json".toMediaType())

class SampleBugReportUploader(
    private val retrofit: Retrofit,
    private val apiKey: String
) : FileUploader {

    override suspend fun upload(file: File, payload: Payload, shouldCompress: Boolean): TaskResult {
        val customRetrofit = retrofit.newBuilder()
            .baseUrl(BASE_URL)
            .build()

        Logger.d("Uploading to ${customRetrofit.baseUrl()}")

        val uploadService = customRetrofit.create(SampleUploadService::class.java)

        val fileBody = file.asRequestBody("application/octet-stream".toMediaType())
        val metadataBody = payload.asRequestBody()
        return try {
            val response = uploadService.upload(fileBody, metadataBody)
            when (response.code()) {
                in 500..599 -> TaskResult.RETRY
                408 -> TaskResult.RETRY
                in 200..299 -> TaskResult.SUCCESS
                else -> TaskResult.FAILURE
            }
        } catch (e: HttpException) {
            Logger.e("upload failure", e)
            TaskResult.RETRY
        }
    }
}
