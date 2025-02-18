package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.BortJson
import com.memfault.bort.TemporaryFile
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

internal class HighResTelemetryTest {
    //        {"schema_version":1,"start_time":1677071040117,"duration_ms":4020926,"report_type":"Heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"airplane_mode","metric_type":"property","data_type":"boolean","internal":false},"data":[{"t":1677071040117,"value":false}]},{"metadata":{"string_key":"bort_upstream_version_code","metric_type":"property","data_type":"double","internal":true},"data":[{"t":1677071040117,"value":4040000.0},{"t":1677075061001,"value":4040000.0}]},{"metadata":{"string_key":"bort_upstream_version_name","metric_type":"property","data_type":"string","internal":true},"data":[{"t":1677071040117,"value":"4.4.0+0-"},{"t":1677075061001,"value":"4.4.0+0-"}]},{"metadata":{"string_key":"bort_version_code","metric_type":"property","data_type":"double","internal":true},"data":[{"t":1677071040117,"value":4040000.0},{"t":1677075061000,"value":4040000.0}]},{"metadata":{"string_key":"bort_version_name","metric_type":"property","data_type":"string","internal":true},"data":[{"t":1677071040117,"value":"4.4.0+0--4108c869f-729f0e7"},{"t":1677075061001,"value":"4.4.0+0--4108c869f-729f0e7"}]},{"metadata":{"string_key":"storage.data.bytes_total","metric_type":"gauge","data_type":"double","internal":false},"data":[{"t":1674090023487,"value":6424690000.0}]}]}
    private val HRT_FILE = """
        {"schema_version":1,"start_time":1677071040117,"duration_ms":4020926,"report_type":"Heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"airplane_mode","metric_type":"property","data_type":"boolean","internal":false},"data":[{"t":1677071040117,"value":false}]},{"metadata":{"string_key":"bort_upstream_version_code","metric_type":"property","data_type":"double","internal":true},"data":[{"t":1677071040117,"value":4040000.0},{"t":1677075061001,"value":4040000.0}]},{"metadata":{"string_key":"bort_upstream_version_name","metric_type":"property","data_type":"string","internal":true},"data":[{"t":1677071040117,"value":"4.4.0+0-"},{"t":1677075061001,"value":"4.4.0+0-"}]},{"metadata":{"string_key":"bort_version_code","metric_type":"property","data_type":"double","internal":true},"data":[{"t":1677071040117,"value":4040000.0},{"t":1677075061000,"value":4040000.0}]},{"metadata":{"string_key":"bort_version_name","metric_type":"property","data_type":"string","internal":true},"data":[{"t":1677071040117,"value":"4.4.0+0--4108c869f-729f0e7"},{"t":1677075061001,"value":null}]}]}
    """.trimIndent()

    @Test
    fun parseHrtFile() {
        val tempFile = TemporaryFile()
        tempFile.useFile { f, _ ->
            f.writeText(HRT_FILE)
            val hrt = HighResTelemetry.decodeFromStream(f)
            val recoded = BortJson.encodeToString(HighResTelemetry.serializer(), hrt)
            assertThat(recoded).isEqualTo(HRT_FILE)
        }
    }
}
