package vnd.myandroid.bortappid // Update to match your package

import androidx.work.ListenableWorker
import com.memfault.bort.FileUploader
import com.memfault.bort.Logger
import okhttp3.MediaType
import okhttp3.RequestBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.File

private const val BASE_URL = "https://my.base.url"

interface SampleUploadService {
    @POST("api/v0/upload")
    suspend fun upload(@Body file: RequestBody): Response<Unit>
}

class SampleBugReportUploader(
    private val retrofit: Retrofit,
    private val apiKey: String
): FileUploader {

    override suspend fun upload(file: File): ListenableWorker.Result {
        val customRetrofit = retrofit.newBuilder()
            .baseUrl(BASE_URL)
            .build()

        Logger.d("Uploading to ${customRetrofit.baseUrl()}")

        val uploadService = customRetrofit.create(SampleUploadService::class.java)

        val requestBody = RequestBody.create(
            MediaType.get("application/octet-stream"),
            file
        )
        return try {
            val response = uploadService.upload(requestBody)
            when (response.code()) {
                in 500..599 -> ListenableWorker.Result.retry()
                408 -> ListenableWorker.Result.retry()
                in 200..299 -> ListenableWorker.Result.success()
                else -> ListenableWorker.Result.failure()
            }
        } catch (e: HttpException) {
            Logger.e("upload failure", e)
            ListenableWorker.Result.retry()
        }
    }
}
