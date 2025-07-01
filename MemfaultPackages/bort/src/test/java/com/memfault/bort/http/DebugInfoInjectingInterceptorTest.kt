package com.memfault.bort.http

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.memfault.bort.DEVICE_INFO_FIXTURE
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.uploader.BortEnabledTestProvider
import io.mockk.every
import io.mockk.mockk
import okhttp3.Request
import org.junit.Before
import org.junit.Test

class DebugInfoInjectingInterceptorTest {
    lateinit var bortEnabledProvider: BortEnabledTestProvider
    lateinit var deviceInfoProvider: FakeDeviceInfoProvider

    @Before
    fun setUp() {
        bortEnabledProvider = BortEnabledTestProvider()
        deviceInfoProvider = FakeDeviceInfoProvider()
    }

    @Test
    fun transformRequestRespectsWhitelist() {
        listOf(
            "https://memfault.otherdomain.org/",
            "https://foo.com/",
            "http://foo.com/",
            "http://127.0.0.1:8000/",
            "http://app.memfault.test:8000/",
        ).forEach { url ->
            Request.Builder()
                .url(url)
                .build().also { request ->
                    DebugInfoInjectingInterceptor(
                        mockk(),
                        mockk(),
                        bortEnabledProvider,
                        deviceInfoProvider,
                    ).transformRequest(request).let {
                        assertThat(it.url.toString()).isEqualTo(url)
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
            "http://127.0.0.1:8000/api",
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
                        mockk {
                            every { id() } returns "DEMO"
                        },
                        bortEnabledProvider,
                        deviceInfoProvider,
                    ).transformRequest(request).let { result ->
                        result.url.queryParameterNames.also { queryParamNames ->
                            assert(queryParamNames.contains(QUERY_PARAM_DEVICE_SERIAL))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_VERSION_NAME))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_VERSION_CODE))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_GIT_SHA))
                            assert(queryParamNames.contains(QUERY_PARAM_VERSION_NAME))
                            assert(queryParamNames.contains(QUERY_PARAM_VERSION_CODE))
                            assert(queryParamNames.contains(QUERY_PARAM_DEVICE_ID))
                        }
                        assertThat(

                            result.url.queryParameter(QUERY_PARAM_DEVICE_SERIAL),
                        ).isEqualTo(DEVICE_INFO_FIXTURE.deviceSerial)
                        assertThat(result.header(X_REQUEST_ID)).isNotNull()
                    }
                }
        }
    }

    @Test
    fun emptyDeviceSerialIfBortNotEnabled() {
        bortEnabledProvider.setEnabled(false)

        Request.Builder()
            .url("https://memfault.com/")
            .build().also { request ->
                DebugInfoInjectingInterceptor(
                    mockk {
                        every { upstreamVersionName } returns "version"
                        every { upstreamVersionCode } returns 1
                        every { upstreamGitSha } returns "sha"
                        every { appVersionName } returns "appVersion"
                        every { appVersionCode } returns 1
                    },
                    mockk {
                        every { id() } returns "DEMO"
                    },
                    bortEnabledProvider,
                    deviceInfoProvider,
                ).transformRequest(request).let { result ->
                    result.url.queryParameterNames.also { queryParamNames ->
                        assert(queryParamNames.contains(QUERY_PARAM_DEVICE_SERIAL))
                    }
                    assertThat(
                        result.url.queryParameter(QUERY_PARAM_DEVICE_SERIAL),
                    ).isEqualTo("")
                }
            }
    }
}
