package com.memfault.bort.metrics

import com.memfault.bort.BortJson
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

// Remember to handle deserializing v1, if this is ever rev'd to v2 (because there might be a metrics service creating
// v1 files).
@Serializable
data class HighResTelemetry(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    @SerialName("start_time")
    val startTimeMs: Long,
    @SerialName("duration_ms")
    val durationMs: Long,
    @SerialName("report_type")
    val reportType: String,
    @SerialName("producer")
    val producer: Producer,
    @SerialName("rollups")
    val rollups: List<Rollup>,
) {
    @Serializable
    data class Producer(
        @SerialName("version")
        val version: String,
        @SerialName("id")
        val id: String = "bort",
    )

    @Serializable
    data class Rollup(
        @SerialName("metadata")
        val metadata: RollupMetadata,
        @SerialName("data")
        val data: List<Datum>,
    )

    @Serializable
    data class Datum(
        /**
         * Timestamp. Unit: epoch in milliseconds.
         */
        @SerialName("t")
        val t: Long,

        /**
         * There should be no mixed types in a single rollup.
         * `null` can be used to unset a `property` or could be used for `event`,
         * in case there is no value for the event.
         */
        @SerialName("value")
        val value: JsonPrimitive?,
    )

    @Serializable
    data class RollupMetadata(
        @SerialName("string_key")
        val stringKey: String,
        @SerialName("metric_type")
        val metricType: MetricType,
        @SerialName("data_type")
        val dataType: DataType,
        @SerialName("internal")
        val internal: Boolean,
    )

    @Serializable
    enum class MetricType {
        @SerialName("counter")
        Counter,

        @SerialName("gauge")
        Gauge,

        @SerialName("property")
        Property,

        @SerialName("event")
        Event,
    }

    @Serializable
    enum class DataType {
        @SerialName("double")
        DoubleType,

        @SerialName("string")
        StringType,

        @SerialName("boolean")
        BooleanType,
    }

    @OptIn(ExperimentalSerializationApi::class)
    companion object {
        fun HighResTelemetry.toFile(file: File) {
            file.outputStream().use { stream ->
                BortJson.encodeToStream(serializer(), this, stream)
            }
        }

        fun decodeFromStream(file: File): HighResTelemetry =
            file.inputStream().use { stream ->
                BortJson.decodeFromStream(serializer(), stream)
            }

        fun mergeHrtIntoFile(hrtFile: File, addMetrics: List<HighResTelemetry.Rollup>) {
            val hrt = decodeFromStream(hrtFile)
            val mergedHrt = hrt.copy(rollups = hrt.rollups + addMetrics)
            mergedHrt.toFile(hrtFile)
        }
    }
}
