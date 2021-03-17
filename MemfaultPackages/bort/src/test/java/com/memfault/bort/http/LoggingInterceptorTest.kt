package com.memfault.bort.http

import okhttp3.HttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoggingInterceptorTest {
    @Test
    fun scrubUrl() {
        val originalUrl = HttpUrl.Builder().apply {
            scheme("https")
            host("memfault.com")
            addQueryParameter(QUERY_PARAM_DEVICE_SERIAL, "pii-sn")
        }.build()
        assertEquals("https://memfault.com/?deviceSerial=***SCRUBBED***", scrubUrl(originalUrl).toString())
    }
}
