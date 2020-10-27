package com.memfault.bort

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

suspend fun Call.await(): Response =
    suspendCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }

fun resultForHttpStatusCode(code: Int): TaskResult =
    when (code) {
        in 500..599 -> TaskResult.RETRY
        408 -> TaskResult.RETRY
        in 200..299 -> TaskResult.SUCCESS
        else -> TaskResult.FAILURE
    }

fun Response.asResult(): TaskResult =
    resultForHttpStatusCode(code)

fun <T : Any> retrofit2.Response<T>.asResult(): TaskResult =
    resultForHttpStatusCode(code())
