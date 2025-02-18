package com.memfault.bort.uploader

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.Payload.MarPayload
import com.memfault.bort.TemporaryFile
import com.memfault.bort.http.PROJECT_KEY_HEADER
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.GzipSource
import okio.buffer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

@RunWith(TestParameterInjector::class)
class PreparedUploaderTest {
    @get:Rule
    val server = MockWebServer()

    private fun fileUploadPayload() = MarPayload(
        MarFileUploadPayload(
            file = FileUploadToken("", "", ""),
            hardwareVersion = "",
            deviceSerial = "",
            softwareVersion = "",
            softwareType = "",
        ),
    )

    @Test
    fun prepareProvidesUrlAndToken() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE),
        )
        val result = TemporaryFile().useFile { file, _ ->
            createUploader(server).prepare(file, fileUploadPayload().kind())
        }
        assertThat(server.takeRequest().getHeader(PROJECT_KEY_HEADER)).isEqualTo(SECRET_KEY)
        assertThat(result.body()!!.data.upload_url).isEqualTo(UPLOAD_URL)
        assertThat(result.body()!!.data.token).isEqualTo(AUTH_TOKEN)
    }

    @Test
    fun uploadFile(@TestParameter shouldCompress: Boolean) = runTest {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE),
        )
        createUploader(server).upload(
            loadTestFileFromResources(),
            // Force the request to go to the mock server so that we can inspect it
            server.url("test").toString(),
            shouldCompress = shouldCompress,
        )
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        assertThat(recordedRequest).isNotNull()
        // Project key should not be included
        assertThat(recordedRequest!!.getHeader(PROJECT_KEY_HEADER)).isNull()
        assertThat(recordedRequest.method).isEqualTo("PUT")
        assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/octet-stream")
        assertThat(recordedRequest.getHeader("Content-Encoding")).isEqualTo(if (shouldCompress) "gzip" else null)
        val text = loadTestFileFromResources().readText(Charset.defaultCharset())
        assertThat(text).isEqualTo(
            recordedRequest.body.let {
                if (shouldCompress) {
                    GzipSource(it).buffer()
                } else {
                    it
                }
            }.readUtf8(),
        )
    }

    @Test
    fun commitMar() = runTest {
        server.enqueue(MockResponse())
        val deviceInfo = FakeDeviceInfoProvider().getDeviceInfo()
        createUploader(server).commit(
            "someToken",
            MarPayload(
                MarFileUploadPayload(
                    hardwareVersion = deviceInfo.hardwareVersion,
                    softwareVersion = deviceInfo.softwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    file = FileUploadToken("", "aa", "logcat.txt"),
                ),
            ),
        )
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        checkNotNull(recordedRequest)
        assertThat(recordedRequest.path).isEqualTo("/api/v0/upload/mar")
        assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8")
        assertThat(recordedRequest.getHeader(PROJECT_KEY_HEADER)).isEqualTo(SECRET_KEY)
        assertThat(recordedRequest.body.readUtf8()).isEqualTo(
            """{"file":{"token":"someToken","md5":"aa","name":"logcat.txt"},""" +
                """"hardware_version":"HW-FOO","device_serial":"SN1234","software_version":"1.0.0",""" +
                """"software_type":"android-build"}""".trimMargin(),
        )
    }
}
