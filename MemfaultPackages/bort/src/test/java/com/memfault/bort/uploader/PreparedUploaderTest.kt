package com.memfault.bort.uploader

import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.Payload.MarPayload
import com.memfault.bort.TemporaryFile
import com.memfault.bort.http.PROJECT_KEY_HEADER
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.GzipSource
import okio.buffer
import org.junit.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

internal class PreparedUploaderTest {
    @get:Rule
    val server = MockWebServer()

    private fun fileUploadPayload() = MarPayload(
        MarFileUploadPayload(
            file = FileUploadToken("", "", ""),
            hardwareVersion = "",
            deviceSerial = "",
            softwareVersion = "",
            softwareType = "",
        )
    )

    @Test
    fun prepareProvidesUrlAndToken() {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE)
        )
        val result = runBlocking {
            TemporaryFile().useFile { file, _ ->
                createUploader(server).prepare(file, fileUploadPayload().kind())
            }
        }
        assertEquals(SECRET_KEY, server.takeRequest().getHeader(PROJECT_KEY_HEADER))
        assertEquals(UPLOAD_URL, result.body()!!.data.upload_url)
        assertEquals(AUTH_TOKEN, result.body()!!.data.token)
    }

    @TestFactory
    fun uploadFile() = listOf(
        true,
        false,
    ).map { shouldCompress ->
        DynamicTest.dynamicTest("shouldCompress=$shouldCompress") {

            server.enqueue(
                MockResponse()
                    .setBody(UPLOAD_RESPONSE)
            )
            runBlocking {
                createUploader(server).upload(
                    loadTestFileFromResources(),
                    // Force the request to go to the mock server so that we can inspect it
                    server.url("test").toString(),
                    shouldCompress = shouldCompress,
                )
            }
            val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
            assertNotNull(recordedRequest)
            // Project key should not be included
            assertNull(recordedRequest!!.getHeader(PROJECT_KEY_HEADER))
            assertEquals("PUT", recordedRequest.method)
            assertEquals("application/octet-stream", recordedRequest.getHeader("Content-Type"))
            assertEquals(if (shouldCompress) "gzip" else null, recordedRequest.getHeader("Content-Encoding"))
            val text = loadTestFileFromResources().readText(Charset.defaultCharset())
            assertEquals(
                text,
                recordedRequest.body.let {
                    if (shouldCompress) GzipSource(it).buffer()
                    else it
                }.readUtf8()
            )
        }
    }

    @Test
    fun commitMar() {
        server.enqueue(MockResponse())
        runBlocking {
            val deviceInfo = FakeDeviceInfoProvider().getDeviceInfo()
            createUploader(server).commit(
                "someToken",
                MarPayload(
                    MarFileUploadPayload(
                        hardwareVersion = deviceInfo.hardwareVersion,
                        softwareVersion = deviceInfo.softwareVersion,
                        deviceSerial = deviceInfo.deviceSerial,
                        file = FileUploadToken("", "aa", "logcat.txt"),
                    )
                )
            )
        }
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        checkNotNull(recordedRequest)
        assertEquals("/api/v0/upload/mar", recordedRequest.path)
        assertEquals("application/json; charset=utf-8", recordedRequest.getHeader("Content-Type"))
        assertEquals(SECRET_KEY, recordedRequest.getHeader(PROJECT_KEY_HEADER))
        assertEquals(
            (
                """{"file":{"token":"someToken","md5":"aa","name":"logcat.txt"},""" +
                    """"hardware_version":"HW-FOO","device_serial":"SN1234","software_version":"1.0.0",""" +
                    """"software_type":"android-build"}""".trimMargin()
                ),
            recordedRequest.body.readUtf8()
        )
    }
}
