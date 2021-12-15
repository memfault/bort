package com.memfault.bort.uploader

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.workDataOf
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.asResult
import com.memfault.bort.await
import com.memfault.bort.enqueueWorkOnce
import com.memfault.bort.http.ProjectKeyInjectingInterceptor
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.Logger
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.milliseconds
import kotlin.time.minutes
import kotlin.time.toJavaDuration
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout

private const val URL_KEY = "url"
private const val METHOD_KEY = "method"
private const val HEADERS_KEY = "headers"
private const val BODY_MEDIA_TYPE_KEY = "bodyMediaType"
private const val BODY_KEY = "body"
private const val MAX_ATTEMPTS_KEY = "maxAttempts"
private const val TASK_TAGS_KEY = "taskTags"
private const val BACKOFF_MILLISECONDS_KEY = "backoffMillis"

private const val DEFAULT_MAX_ATTEMPTS = 3
private val DEFAULT_BACKOFF_DURATION = 5.minutes

data class HttpTaskInput(
    val url: String,
    val method: String,
    val headers: String,
    val bodyMediaType: String,
    val body: ByteArray,
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val taskTags: List<String> = listOf(),
    val backoffDuration: Duration = DEFAULT_BACKOFF_DURATION,
) {
    companion object {
        fun fromInputData(inputData: Data) =
            HttpTaskInput(
                url = checkNotNull(inputData.getString(URL_KEY), { "url missing" }),
                method = checkNotNull(inputData.getString(METHOD_KEY), { "method missing" }),
                headers = checkNotNull(inputData.getString(HEADERS_KEY), { "headers missing" }),
                body = checkNotNull(inputData.getByteArray(BODY_KEY), { "body missing" }),
                bodyMediaType = checkNotNull(
                    inputData.getString(BODY_MEDIA_TYPE_KEY),
                    { "body media type missing" }
                ),
                maxAttempts = inputData.getInt(MAX_ATTEMPTS_KEY, DEFAULT_MAX_ATTEMPTS),
                taskTags = inputData.getStringArray(TASK_TAGS_KEY)?.asList() ?: listOf(),
                backoffDuration = inputData.getLong(
                    BACKOFF_MILLISECONDS_KEY,
                    DEFAULT_BACKOFF_DURATION.toLongMilliseconds()
                ).milliseconds
            )
    }

    // MFLT-1994: Bort: extend HttpTask to serialize request to disk if >10Kb
    fun toWorkerInputData() =
        workDataOf(
            URL_KEY to url,
            METHOD_KEY to method,
            HEADERS_KEY to headers,
            BODY_MEDIA_TYPE_KEY to bodyMediaType,
            BODY_KEY to body,
            MAX_ATTEMPTS_KEY to maxAttempts,
            TASK_TAGS_KEY to taskTags.toTypedArray(),
            BACKOFF_MILLISECONDS_KEY to backoffDuration.toLongMilliseconds(),
        )

    fun toRequest() =
        Request.Builder()
            .url(url)
            .method(
                method,
                body.toRequestBody(bodyMediaType.toMediaType())
            )
            .headers(
                Headers.Builder().also { headerBuilder ->
                    headers.split("\n").filter { line ->
                        line.trim() != ""
                    }.forEach { line ->
                        headerBuilder.add(line)
                    }
                }.build()
            )
            .build()

    fun taskTagsString(): String =
        taskTags.joinToString(", ")
}

class HttpTask @Inject constructor(
    private val okHttpClient: OkHttpClient,
    override val metrics: BuiltinMetricsStore,
) : Task<HttpTaskInput>() {

    override val getMaxAttempts: () -> Int = { DEFAULT_MAX_ATTEMPTS }
    override fun getMaxAttempts(input: HttpTaskInput): Int = input.maxAttempts

    override suspend fun doWork(worker: TaskRunnerWorker, input: HttpTaskInput): TaskResult {
        val response = try {
            okHttpClient.newCall(
                input.toRequest()
            ).await()
        } catch (e: IOException) {
            return TaskResult.RETRY.also {
                Logger.e("Request failed (tags=${input.taskTagsString()})", e)
                Logger.logEvent("http-error", "0", *input.taskTags.toTypedArray())
            }
        }

        response.use {
            return response.asResult().also { result ->
                val resultString = when (result) {
                    TaskResult.SUCCESS -> "success"
                    else -> "error"
                }
                val codeString = response.code.toString()
                Logger.v("Request $resultString ($codeString) (tags=${input.taskTagsString()})")
                Logger.logEvent(
                    "http-$resultString", codeString,
                    *input.taskTags.toTypedArray()
                )
            }
        }
    }

    override fun convertAndValidateInputData(inputData: Data): HttpTaskInput =
        HttpTaskInput.fromInputData(inputData)
}

/**
 * Class to be used with Retrofit's @Tag annotation to specify additional request parameters.
 * See unit test for an example.
 */
data class HttpTaskOptions(
    val maxAttempts: Int? = null,
    val taskTags: List<String>? = null,
)

fun interface EnqueueHttpTask : (HttpTaskInput) -> Unit

/**
 * Retrofit Call Factory that dispatches the request for executing through a HttpTask (AndroidX
 * Worker based executor). Service calls will immediately return with a HTTP 204 "No Content
 * (Enqueued)" Response and a null body.
 */
class HttpTaskCallFactory @Inject constructor(
    private val enqueueHttpTaskCallback: EnqueueHttpTask,
    private val projectKeyInjectingInterceptor: ProjectKeyInjectingInterceptor,
) : Call.Factory {

    companion object {
        fun fromContextAndConstraints(
            context: Context,
            getUploadConstraints: () -> Constraints,
            projectKeyInjectingInterceptor: ProjectKeyInjectingInterceptor,
            jitterDelayProvider: JitterDelayProvider,
        ) =
            HttpTaskCallFactory(
                { input ->
                    enqueueWorkOnce<HttpTask>(context, input.toWorkerInputData()) {
                        setInitialDelay(jitterDelayProvider.randomJitterDelay())
                        setConstraints(getUploadConstraints())
                        setBackoffCriteria(BackoffPolicy.EXPONENTIAL, input.backoffDuration.toJavaDuration())
                        input.taskTags.forEach { tag -> addTag(tag) }
                    }
                },
                projectKeyInjectingInterceptor
            )
    }

    override fun newCall(request: Request): Call = Call(
        projectKeyInjectingInterceptor.transformRequest(request)
    )

    inner class Call(
        private val request: Request,
    ) : okhttp3.Call {
        private var executed: Boolean = false

        override fun enqueue(responseCallback: Callback) =
            responseCallback.onResponse(this, this.execute())

        override fun isExecuted(): Boolean = executed

        override fun timeout(): Timeout {
            throw NotImplementedError()
        }

        override fun clone(): okhttp3.Call {
            throw CloneNotSupportedException()
        }

        override fun isCanceled(): Boolean = false

        override fun cancel() {
            throw NotImplementedError()
        }

        override fun request(): Request = request

        override fun execute(): Response {
            if (executed) throw IllegalStateException()
            return enqueueHttpTaskCallback(this.toHttpTaskInput()).let {
                executed = true
                Response.Builder()
                    .request(this.request())
                    .protocol(Protocol.HTTP_1_1)
                    // 202 "Accepted" would be more at place, but using "No Content" status to
                    // prevent triggering response deserialization path in retrofit:
                    .code(204)
                    .message("No Content (Enqueued)")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        private fun toHttpTaskInput() = with(request) {
            val requestBody = body ?: throw NullPointerException()
            val options = tag(HttpTaskOptions::class.java)
            HttpTaskInput(
                url = url.toString(),
                method = method,
                bodyMediaType = requestBody.contentType().toString(),
                headers = headers.toString(),
                body = Buffer().also { requestBody.writeTo(it) }.readByteArray(),
                maxAttempts = options?.maxAttempts ?: DEFAULT_MAX_ATTEMPTS,
                taskTags = options?.taskTags ?: listOf()
            )
        }
    }
}
