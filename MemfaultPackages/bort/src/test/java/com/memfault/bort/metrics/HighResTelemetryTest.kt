package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.BortJson
import com.memfault.bort.TemporaryFile
import com.memfault.bort.metrics.HighResTelemetry.Companion.mergeHrtIntoFile
import com.memfault.bort.metrics.HighResTelemetry.Companion.toFile
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import kotlinx.serialization.json.JsonPrimitive
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File

internal class HighResTelemetryTest {
    //        {"schema_version":1,"start_time":1677071040117,"duration_ms":4020926,"report_type":"Heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"airplane_mode","metric_type":"property","data_type":"boolean","internal":false},"data":[{"t":1677071040117,"value":false}]},{"metadata":{"string_key":"bort_upstream_version_code","metric_type":"property","data_type":"double","internal":true},"data":[{"t":1677071040117,"value":4040000.0},{"t":1677075061001,"value":4040000.0}]},{"metadata":{"string_key":"bort_upstream_version_name","metric_type":"property","data_type":"string","internal":true},"data":[{"t":1677071040117,"value":"4.4.0+0-"},{"t":1677075061001,"value":"4.4.0+0-"}]},{"metadata":{"string_key":"bort_version_code","metric_type":"property","data_type":"double","internal":true},"data":[{"t":1677071040117,"value":4040000.0},{"t":1677075061000,"value":4040000.0}]},{"metadata":{"string_key":"bort_version_name","metric_type":"property","data_type":"string","internal":true},"data":[{"t":1677071040117,"value":"4.4.0+0--4108c869f-729f0e7"},{"t":1677075061001,"value":"4.4.0+0--4108c869f-729f0e7"}]},{"metadata":{"string_key":"storage.data.bytes_total","metric_type":"gauge","data_type":"double","internal":false},"data":[{"t":1674090023487,"value":6424690000.0}]}]}
    private val HRT_FILE = """
        {"schema_version":1,"start_time":1677071040117,"duration_ms":4020926,"report_type":"Heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"airplane_mode","metric_type":"property","data_type":"boolean","internal":false},"data":[{"t":1677071040117,"value":false}]},{"metadata":{"string_key":"bort_upstream_version_code","metric_type":"property","data_type":"double","internal":true},"data":[{"t":1677071040117,"value":4040000.0},{"t":1677075061001,"value":4040000.0}]},{"metadata":{"string_key":"bort_upstream_version_name","metric_type":"property","data_type":"string","internal":true},"data":[{"t":1677071040117,"value":"4.4.0+0-"},{"t":1677075061001,"value":"4.4.0+0-"}]},{"metadata":{"string_key":"bort_version_code","metric_type":"property","data_type":"double","internal":true},"data":[{"t":1677071040117,"value":4040000.0},{"t":1677075061000,"value":4040000.0}]},{"metadata":{"string_key":"bort_version_name","metric_type":"property","data_type":"string","internal":true},"data":[{"t":1677071040117,"value":"4.4.0+0--4108c869f-729f0e7"},{"t":1677075061001,"value":null}]}]}
    """.trimIndent()

    private fun rollup(key: String, timestampMs: Long, value: Double) = Rollup(
        metadata = RollupMetadata(
            stringKey = key,
            metricType = HighResTelemetry.MetricType.Gauge,
            dataType = HighResTelemetry.DataType.DoubleType,
            internal = false,
        ),
        data = listOf(Datum(t = timestampMs, value = JsonPrimitive(value))),
    )

    private fun assertRollupsSerializedLast(file: File) {
        assertThat(file.readText().endsWith("]}")).isEqualTo(true)
    }

    /** The pre-append implementation, to compare against byte-for-byte. */
    private fun mergedByRewrite(file: File, addMetrics: Set<Rollup>): String {
        val hrt = HighResTelemetry.decodeFromStream(file)
        return BortJson.encodeToString(
            HighResTelemetry.serializer(),
            hrt.copy(rollups = hrt.rollups + addMetrics),
        )
    }

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

    @Test
    fun mergeIntoPopulatedRollups() {
        val tempFile = TemporaryFile()
        tempFile.useFile { f, _ ->
            f.writeText(HRT_FILE)
            assertRollupsSerializedLast(f)

            val added = setOf(
                rollup("battery.level", 1677075061002, 55.0),
                rollup("cpu.usage", 1677075061003, 12.5),
            )
            val expected = mergedByRewrite(f, added)

            mergeHrtIntoFile(f, added)

            assertThat(f.readText()).isEqualTo(expected)
        }
    }

    @Test
    fun mergeIntoEmptyRollups() {
        val tempFile = TemporaryFile()
        tempFile.useFile { f, _ ->
            HighResTelemetry(
                startTimeMs = 1677071040117,
                durationMs = 4020926,
                reportType = "Heartbeat",
                producer = HighResTelemetry.Producer(version = "1"),
                rollups = emptyList(),
            ).toFile(f)
            assertRollupsSerializedLast(f)

            val added = setOf(rollup("battery.level", 1677075061002, 55.0))
            val expected = mergedByRewrite(f, added)

            mergeHrtIntoFile(f, added)

            assertThat(f.readText()).isEqualTo(expected)
        }
    }

    @Test
    fun mergeNothingLeavesFileUntouched() {
        val tempFile = TemporaryFile()
        tempFile.useFile { f, _ ->
            f.writeText(HRT_FILE)

            mergeHrtIntoFile(f, emptySet())

            assertThat(f.readText()).isEqualTo(HRT_FILE)
        }
    }

    @Test
    fun mergeFallsBackWhenEmptyRollupsArrayHasWhitespace() {
        val tempFile = TemporaryFile()
        tempFile.useFile { f, _ ->
            f.writeText(HRT_FILE.substringBefore("\"rollups\":") + "\"rollups\":[ ]}")

            val added = setOf(rollup("battery.level", 1677075061002, 55.0))
            val expected = mergedByRewrite(f, added)

            mergeHrtIntoFile(f, added)

            assertThat(f.readText()).isEqualTo(expected)
        }
    }

    @Test
    fun mergeFallsBackWhenTailIsUnexpected() {
        val tempFile = TemporaryFile()
        tempFile.useFile { f, _ ->
            f.writeText("$HRT_FILE\n")

            val added = setOf(rollup("battery.level", 1677075061002, 55.0))
            val expected = mergedByRewrite(f, added)

            mergeHrtIntoFile(f, added)

            assertThat(f.readText()).isEqualTo(expected)
        }
    }
}
