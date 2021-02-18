package com.memfault.bort.http

import com.memfault.bort.DeviceIdProvider
import io.mockk.every
import io.mockk.mockk
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DebugInfoInjectingInterceptorTest {
    @Test
    fun transformRequestRespectsWhitelist() {
        listOf(
            "https://memfault.otherdomain.org/",
            "https://foo.com/",
            "http://foo.com/",
            "http://127.0.0.1:5000/",
            "http://localhost:5000/"
        ).forEach { url ->
            Request.Builder()
                .url(url)
                .build().also { request ->
                    DebugInfoInjectingInterceptor(
                        mockk(),
                        mockk(),
                    ).transformRequest(request).let {
                        assertEquals(url, it.url.toString())
                    }
                }
        }
    }

    @Test
    fun transformMemfaultOrCleartextApiRequests() {
        listOf(
            "https://memfault.com/",
            "https://sub.memfault.com/",
            "http://localhost:9000/api",
            "http://127.0.0.1:5000/api"
        ).forEach { url ->
            Request.Builder()
                .url(url)
                .build().also { request ->
                    DebugInfoInjectingInterceptor(
                        mockk {
                            every { upstreamVersionName } returns "version"
                            every { upstreamVersionCode } returns 1
                            every { upstreamGitSha } returns "sha"
                            every { appVersionName } returns "appVersion"
                            every { appVersionCode } returns 1
                        },
                        mockk<DeviceIdProvider>() {
                            every { deviceId() } returns "DEMO"
                        }
                    ).transformRequest(request).let { result ->
                        result.url.queryParameterNames.also { queryParamNames ->
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_VERSION_NAME))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_VERSION_CODE))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_GIT_SHA))
                            assert(queryParamNames.contains(QUERY_PARAM_VERSION_NAME))
                            assert(queryParamNames.contains(QUERY_PARAM_VERSION_CODE))
                            assert(queryParamNames.contains(QUERY_PARAM_DEVICE_ID))
                        }
                        assertNotNull(result.header(X_REQUEST_ID))
                    }
                }
        }
    }
}
