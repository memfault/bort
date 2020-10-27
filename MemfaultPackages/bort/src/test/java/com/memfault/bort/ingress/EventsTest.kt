package com.memfault.bort.ingress

import com.memfault.bort.BortJson
import com.memfault.bort.DeviceInfo
import com.memfault.bort.uploader.createRetrofit
import java.time.Instant
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RebootEventInfoTest {
    fun stringify(info: RebootEventInfo) =
        BortJson.encodeToString(RebootEventInfo.serializer(), info)

    @Test
    fun mandatoryFieldsOnly() {
        assertEquals(
            """{"boot_count":1,"linux_boot_id":"230295cb-04d4-40b8-8624-ec37089b9b75",""" +
                """"reason":"shutdown","subreason":null,"details":null}""",
            stringify(
                RebootEventInfo(
                    1,
                    "230295cb-04d4-40b8-8624-ec37089b9b75",
                    "shutdown"
                )
            )
        )
    }

    @Test
    fun allFields() {
        assertEquals(
            """{"boot_count":1,"linux_boot_id":"230295cb-04d4-40b8-8624-ec37089b9b75",""" +
                """"reason":"shutdown","subreason":"battery","details":["thermal","50C"]}""",
            stringify(
                RebootEventInfo(
                    1,
                    "230295cb-04d4-40b8-8624-ec37089b9b75",
                    "shutdown",
                    "battery",
                    listOf("thermal", "50C")
                )
            )
        )
    }
}

class RebootEventUploaderTest {
    @get:Rule
    val server = MockWebServer()

    @Test
    fun uploadEvents() {
        server.enqueue(MockResponse())
        val service = createRetrofit(server).create(IngressService::class.java)
        service.uploadRebootEvents(
            listOf(
                RebootEvent(
                    capturedDate = Instant.ofEpochSecond(0),
                    deviceInfo = DeviceInfo(
                        deviceSerial = "SERIAL",
                        hardwareVersion = "HARDWARE-XYZ",
                        softwareVersion = "SW-VERSION"
                    ),
                    eventInfo = RebootEventInfo(
                        1,
                        "230295cb-04d4-40b8-8624-ec37089b9b75",
                        "shutdown",
                        "battery",
                        listOf("thermal", "50C")
                    )
                )
            )
        ).execute()
        val request = server.takeRequest()

        assertEquals(
            """[{"type":"android_reboot","sdk_version":"0.5.0","captured_date":"1970-01-01T00:00:00Z",""" +
                """"hardware_version":"HARDWARE-XYZ","device_serial":"SERIAL","software_version":"SW-VERSION",""" +
                """"event_info":{"boot_count":1,"linux_boot_id":"230295cb-04d4-40b8-8624-ec37089b9b75",""" +
                """"reason":"shutdown","subreason":"battery","details":["thermal","50C"]},""" +
                """"software_type":"android-build"}]""",
            request.body.readUtf8()
        )
    }
}
