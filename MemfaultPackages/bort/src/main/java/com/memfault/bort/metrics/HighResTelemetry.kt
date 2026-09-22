package com.memfault.bort.metrics

import com.memfault.bort.BortJson
import com.memfault.bort.shared.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

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

        /**
         * Appends [addMetrics] to the "rollups" array in place, to avoid rewriting the whole file.
         *
         * Relies on "rollups" being the last member written (by [toFile] and MetricsDao.writeHrtReport), unindented,
         * so the file ends "]}". Falls back to a full rewrite otherwise.
         */
        fun mergeHrtIntoFile(hrtFile: File, addMetrics: Set<Rollup>) {
            if (addMetrics.isEmpty()) return
            if (appendRollups(hrtFile, addMetrics)) return

            val hrt = decodeFromStream(hrtFile)
            val mergedHrt = hrt.copy(rollups = hrt.rollups + addMetrics)
            mergedHrt.toFile(hrtFile)
        }

        /** Returns false if the file does not end as [mergeHrtIntoFile] expects. */
        private fun appendRollups(hrtFile: File, addMetrics: Set<Rollup>): Boolean {
            val length = hrtFile.length()
            if (length < EMPTY_TAIL.size) return false

            return try {
                RandomAccessFile(hrtFile, "rw").use { file ->
                    val tail = ByteArray(EMPTY_TAIL.size)
                    file.seek(length - tail.size)
                    file.readFully(tail)

                    val isEmptyArray = tail.contentEquals(EMPTY_TAIL)
                    if (!isEmptyArray && !tail.contentEquals(POPULATED_TAIL)) return@use false

                    val separator = if (isEmptyArray) "" else ","
                    val rollups = addMetrics.joinToString(separator = ",") {
                        BortJson.encodeToString(Rollup.serializer(), it)
                    }

                    val truncatedLength = length - ROLLUPS_TAIL.size
                    file.setLength(truncatedLength)
                    file.seek(truncatedLength)
                    file.write("$separator$rollups$ROLLUPS_TAIL_STRING".toByteArray(Charsets.UTF_8))
                    true
                }
            } catch (e: IOException) {
                Logger.w("Unable to append rollups to $hrtFile", e)
                false
            }
        }

        private const val ROLLUPS_TAIL_STRING = "]}"
        private val ROLLUPS_TAIL = ROLLUPS_TAIL_STRING.toByteArray(Charsets.UTF_8)

        // The byte before "]}" pins down which case this is: "[" for an empty array, "}" closing the last rollup
        // object for a populated one. Anything else (whitespace from a pretty-printer, say) takes the fallback.
        private val EMPTY_TAIL = "[$ROLLUPS_TAIL_STRING".toByteArray(Charsets.UTF_8)
        private val POPULATED_TAIL = "}$ROLLUPS_TAIL_STRING".toByteArray(Charsets.UTF_8)
    }
}
