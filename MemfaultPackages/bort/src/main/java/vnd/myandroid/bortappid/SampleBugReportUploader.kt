package vnd.myandroid.bortappid // Update to match your package

import com.memfault.bort.FileUploader
import com.memfault.bort.TaskResult
import com.memfault.bort.shared.Logger
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

private const val BASE_URL = "https://my.base.url"

interface SampleUploadService {
    @POST("api/v0/upload")
    suspend fun upload(@Body file: RequestBody): Response<Unit>
}

class SampleBugReportUploader(
    private val retrofit: Retrofit,
    private val apiKey: String
) : FileUploader {

    override suspend fun upload(file: File): TaskResult {
        val customRetrofit = retrofit.newBuilder()
            .baseUrl(BASE_URL)
            .build()

        Logger.d("Uploading to ${customRetrofit.baseUrl()}")

        val uploadService = customRetrofit.create(SampleUploadService::class.java)

        val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
        return try {
            val response = uploadService.upload(requestBody)
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
