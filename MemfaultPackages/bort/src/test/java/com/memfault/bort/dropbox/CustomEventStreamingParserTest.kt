package com.memfault.bort.dropbox

import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.LogcatCollectionId
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
/**
 * Note: This test uses Robolectric because the implementation uses android.util.JsonReader/JsonWriter in order to
 * process and enrich JSON output in a single pass and avoiding memory usage that grows with the number of events.
 * The implementation itself is available in real devices but is stubbed on the SDK and thus could not be used on
 * tests otherwise.
 *
 * Note: Robolectric does not support Junit5 yet, please port this to junit/jupiter when it does.
 * https://github.com/robolectric/robolectric/issues/3477
 */
class StructuredLogStreamingParserTest {
    @Test
    fun happyPath() {
        val input = VALID_STRUCTURED_LOG_FIXTURE

        val (output, metadata) = parse(input)
        assertEquals(
            """{
                |"schema_version":1,
                |"linux_boot_id":"00000000-0000-0000-0000-000000000001",
                |"cid":"00000000-0000-0000-0000-000000000002",
                |"next_cid":"00000000-0000-0000-0000-000000000003",
                |"events":[
                |{"ts":123.1,"type":"type1","data":{"extra_int":3,"extra_double":3.0,"extra_string":"blah","extra_null":null,"extra_array":[1,2,3],"extra_object":{"a":"b"}}},
                |{"ts":123.1,"_type":"internal","data":[1,2,3]}
                |],
                |"device_serial":"SN1234",
                |"software_version":"1.0.0",
                |"hardware_version":"HW-FOO"
                |}""".trimMargin().replace("\n", ""),
            output
        )

        assertEquals(
            StructuredLogMetadata(
                schemaVersion = 1,
                cid = LogcatCollectionId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                nextCid = LogcatCollectionId(UUID.fromString("00000000-0000-0000-0000-000000000003")),
                linuxBootId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            ),
            metadata
        )
    }

    @Test
    fun emptyThrows() {
        assertThrows<StructuredLogParseException> { parse("{}") }
    }

    @Test
    fun invalidThrows() {
        assertThrows<StructuredLogParseException> { parse("shall not pass") }
    }

    private fun parse(json: String): Pair<String, StructuredLogMetadata> {
        val output = ByteArrayOutputStream()
        val deviceInfo = runBlocking { FakeDeviceInfoProvider().getDeviceInfo() }
        val metadata = StructuredLogStreamingParser(json.byteInputStream(), output, deviceInfo)
            .parse()
        return Pair(output.toString(), metadata)
    }
}

val VALID_STRUCTURED_LOG_FIXTURE = """{
 "schema_version": 1,
 "linux_boot_id": "00000000-0000-0000-0000-000000000001",
 "cid": "00000000-0000-0000-0000-000000000002",
 "next_cid": "00000000-0000-0000-0000-000000000003",
 "events": [
   {"ts":123.1,"type":"type1","data":{"extra_int": 3, "extra_double": 3.0, "extra_string": "blah", "extra_null": null, "extra_array": [1,2,3], "extra_object": {"a": "b"}}},
   {"ts":123.1,"_type":"internal","data":[1,2,3]}
 ]
}
""".trimIndent()
