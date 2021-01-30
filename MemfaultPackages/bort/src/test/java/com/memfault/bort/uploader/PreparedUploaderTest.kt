package com.memfault.bort.uploader

import com.memfault.bort.AnrFileUploadMetadata
import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.DropBoxEntryFileUploadPayload
import com.memfault.bort.FakeBootRelativeTimeProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.TombstoneFileUploadMetadata
import com.memfault.bort.http.PROJECT_KEY_HEADER
import com.memfault.bort.time.toAbsoluteTime
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PreparedUploaderTest {
    @get:Rule
    val server = MockWebServer()

    @Test
    fun prepareProvidesUrlAndToken() {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE)
        )
        val result = runBlocking {
            createUploader(server).prepare()
        }
        assertEquals(SECRET_KEY, server.takeRequest().getHeader(PROJECT_KEY_HEADER))
        assertEquals(UPLOAD_URL, result.body()!!.data.upload_url)
        assertEquals(AUTH_TOKEN, result.body()!!.data.token)
    }

    @Test
    fun uploadFile() {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE)
        )
        runBlocking {
            createUploader(server).upload(
                loadTestFileFromResources(),
                // Force the request to go to the mock server so that we can inspect it
                server.url("test").toString()
            )
        }
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        assertNotNull(recordedRequest)
        // Project key should not be included
        assertNull(recordedRequest!!.getHeader(PROJECT_KEY_HEADER))
        assertEquals("PUT", recordedRequest.method)
        assertEquals("application/octet-stream", recordedRequest.getHeader("Content-Type"))
        val text = loadTestFileFromResources().readText(Charset.defaultCharset())
        assertEquals(text, recordedRequest.body.readUtf8())
    }

    @Test
    fun uploadRequestToExternalServer() {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE)
        )
        runBlocking {
            createUploader(server).upload(
                loadTestFileFromResources(),
                UPLOAD_URL
            )
        }
        // Verify that the the request is routed to an external server, not the `baseUrl`
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        assertNull(recordedRequest)
    }

    @Test
    fun commitBugReport() {
        server.enqueue(MockResponse())
        runBlocking {
            createUploader(server).commit("someToken", BugReportFileUploadPayload())
        }
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        checkNotNull(recordedRequest)
        assertEquals("application/json; charset=utf-8", recordedRequest.getHeader("Content-Type"))
        assertEquals(SECRET_KEY, recordedRequest.getHeader(PROJECT_KEY_HEADER))
        assertEquals(
            """{"file":{"token":"someToken"},"processing_options":{"process_anrs":true,""" +
                """"process_java_exceptions":true,"process_last_kmsg":true,"process_recovery_kmsg":true,""" +
                """"process_tombstones":true},"request_id":null}""",
            recordedRequest.body.readUtf8()
        )
    }

    @Test
    fun commitTombstone() {
        server.enqueue(MockResponse())

        runBlocking {
            val deviceInfo = FakeDeviceInfoProvider.getDeviceInfo()
            createUploader(server).commit(
                "someToken",
                DropBoxEntryFileUploadPayload(
                    hardwareVersion = deviceInfo.hardwareVersion,
                    softwareVersion = deviceInfo.softwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    metadata = TombstoneFileUploadMetadata(
                        tag = "SYSTEM_TOMBSTONE",
                        fileTime = 1234.toLong().toAbsoluteTime(),
                        entryTime = 4321.toLong().toAbsoluteTime(),
                        packages = listOf(
                            FileUploadPayload.Package(
                                id = "com.app",
                                versionName = "1.0.0",
                                versionCode = 1,
                                userId = 1001,
                                codePath = "/data/app/apk"
                            )
                        ),
                        collectionTime = FakeBootRelativeTimeProvider.now(),
                        timezone = TimezoneWithId("UTC"),
                    )
                )
            )
        }
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        checkNotNull(recordedRequest)
        assertEquals("/api/v0/upload/android-dropbox-manager-entry/tombstone", recordedRequest.path)
        assertEquals("application/json; charset=utf-8", recordedRequest.getHeader("Content-Type"))
        assertEquals(SECRET_KEY, recordedRequest.getHeader(PROJECT_KEY_HEADER))
        assertEquals(
            (
                """{"file":{"token":"someToken"},"hardware_version":"HW-FOO","device_serial":"SN1234",""" +
                    """"software_version":"1.0.0","software_type":"android-build","metadata":{"type":"tombstone",""" +
                    """"tag":"SYSTEM_TOMBSTONE","file_time":{"timestamp":"1970-01-01T00:00:01.234Z"},""" +
                    """"entry_time":{"timestamp":"1970-01-01T00:00:04.321Z"},"packages":[{"id":"com.app",""" +
                    """"version_code":1,"version_name":"1.0.0","user_id":1001,"code_path":"/data/app/apk"}],""" +
                    """"collection_time":{"uptime_ms":987,"elapsed_realtime_ms":456,""" +
                    """"linux_boot_id":"230295cb-04d4-40b8-8624-ec37089b9b75",""" +
                    """"boot_count":67},"timezone":{"id":"UTC"}}}"""
                ),
            recordedRequest.body.readUtf8()
        )
    }

    @Test
    fun commitAnr() {
        server.enqueue(MockResponse())
        runBlocking {
            val deviceInfo = FakeDeviceInfoProvider.getDeviceInfo()
            createUploader(server).commit(
                "someToken",
                DropBoxEntryFileUploadPayload(
                    hardwareVersion = deviceInfo.hardwareVersion,
                    softwareVersion = deviceInfo.softwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    metadata =
                        AnrFileUploadMetadata(
                            tag = "data_app_anr",
                            fileTime = 1234.toLong().toAbsoluteTime(),
                            entryTime = 4321.toLong().toAbsoluteTime(),
                            collectionTime = FakeBootRelativeTimeProvider.now(),
                            timezone = TimezoneWithId("Europe/Amsterdam"),
                        )
                )
            )
        }
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        checkNotNull(recordedRequest)
        assertEquals("/api/v0/upload/android-dropbox-manager-entry/anr", recordedRequest.path)
        assertEquals("application/json; charset=utf-8", recordedRequest.getHeader("Content-Type"))
        assertEquals(SECRET_KEY, recordedRequest.getHeader(PROJECT_KEY_HEADER))
        assertEquals(
            (
                """{"file":{"token":"someToken"},"hardware_version":"HW-FOO","device_serial":"SN1234",""" +
                    """"software_version":"1.0.0","software_type":"android-build","metadata":{"type":"anr",""" +
                    """"tag":"data_app_anr","file_time":{"timestamp":"1970-01-01T00:00:01.234Z"},""" +
                    """"entry_time":{"timestamp":"1970-01-01T00:00:04.321Z"},""" +
                    """"collection_time":{"uptime_ms":987,"elapsed_realtime_ms":456,""" +
                    """"linux_boot_id":"230295cb-04d4-40b8-8624-ec37089b9b75",""" +
                    """"boot_count":67},"timezone":{"id":"Europe/Amsterdam"}}}"""
                ),
            recordedRequest.body.readUtf8()
        )
    }
}
