package com.memfault.bort.dropbox

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.memfault.bort.DeviceInfo
import com.memfault.bort.LogcatCollectionId
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Parses and enriches data from a structured log file in a single pass. In the name
 * of efficiency this class melds the parsing and manipulation concerns together and
 * produces a richer file containing device-specific info while also collecting log
 * metadata.
 *
 * Parsing is performed using a sax-style parser to prevent memory usage from growing
 * linear with the number of events.
 */
class StructuredLogStreamingParser(
    private val input: InputStream,
    private val output: OutputStream,
    private val deviceInfo: DeviceInfo,
) {
    private var schemaVersion: Int = -1
    private var cid: String? = null
    private var nextCid: String? = null
    private var linuxBootId: String? = null
    private var hasEvents: Boolean = false

    fun parse(): StructuredLogMetadata {
        try {
            output.bufferedWriter().use { writer ->
                input.bufferedReader().use { reader ->
                    JsonReader(reader).use { jsonReader ->
                        JsonWriter(writer).use { jsonWriter ->
                            jsonReader.beginObject()
                            jsonWriter.beginObject()
                            parseMetadata(jsonReader, jsonWriter)
                            writeDeviceMetadata(jsonWriter)
                            jsonWriter.endObject()
                            jsonReader.endObject()
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            throw StructuredLogParseException("Unable to parse structured log", ex)
        }
        checkRequiredFields()

        return StructuredLogMetadata(
            schemaVersion,
            LogcatCollectionId(UUID.fromString(cid)),
            LogcatCollectionId(UUID.fromString(nextCid)),
            UUID.fromString(linuxBootId),
        )
    }

    private fun checkRequiredFields() {
        if (cid == null) throw StructuredLogParseException("Missing cid")
        if (nextCid == null) throw StructuredLogParseException("Missing next_cid")
        if (schemaVersion != 1) {
            throw StructuredLogParseException("Unsupported schema version, expected 1, was $schemaVersion")
        }
        if (linuxBootId == null) throw StructuredLogParseException("Missing linux_boot_id")
        if (!hasEvents) throw StructuredLogParseException("Missing events")
    }

    private fun parseMetadata(reader: JsonReader, writer: JsonWriter) {
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                SCHEMA_VERSION -> {
                    schemaVersion = reader.nextInt()
                    writer.name(name)
                    writer.value(schemaVersion)
                }
                LINUX_BOOT_ID -> {
                    linuxBootId = reader.nextString()
                    writer.name(name)
                    writer.value(linuxBootId)
                }
                CID -> {
                    cid = reader.nextString()
                    writer.name(name)
                    writer.value(cid)
                }
                NEXT_CID -> {
                    nextCid = reader.nextString()
                    writer.name(name)
                    writer.value(nextCid)
                }
                EVENTS -> {
                    hasEvents = true
                    writer.name(name)
                    passThrough(reader, writer)
                }
                DEVICE_SERIAL, SOFTWARE_VERSION, HARDWARE_VERSION -> throw StructuredLogParseException(
                    "Unexpected key '$name' in structured log the entry should be added by this parser",
                )
                else -> throw StructuredLogParseException("Unexpected name in json: $name")
            }
        }
    }

    private fun writeDeviceMetadata(writer: JsonWriter) {
        writer.name(DEVICE_SERIAL)
        writer.value(deviceInfo.deviceSerial)

        writer.name(SOFTWARE_VERSION)
        writer.value(deviceInfo.softwareVersion)

        writer.name(HARDWARE_VERSION)
        writer.value(deviceInfo.hardwareVersion)
    }

    private fun passThrough(reader: JsonReader, writer: JsonWriter, depth: Int = 0) {
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.BOOLEAN -> writer.value(reader.nextBoolean())
                JsonToken.NUMBER -> {
                    // JsonReader has a Number token which might mean double or integer so we have to take
                    // its string form and attempt to parse it.
                    val num = reader.nextString()
                    val asInt = num.toIntOrNull()
                    if (asInt != null) {
                        writer.value(asInt)
                    } else {
                        writer.value(num.toDouble())
                    }
                }
                JsonToken.STRING -> writer.value(reader.nextString())
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    writer.beginObject()
                    passThrough(reader, writer, depth + 1)
                }
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    writer.beginArray()
                    passThrough(reader, writer, depth + 1)
                }
                JsonToken.NAME -> writer.name(reader.nextName())
                JsonToken.NULL -> {
                    reader.nextNull()
                    writer.nullValue()
                }
                // Handled bellow
                JsonToken.END_DOCUMENT, JsonToken.END_ARRAY, JsonToken.END_OBJECT, null -> {}
            }
        }

        when (reader.peek()) {
            JsonToken.END_ARRAY -> {
                if (depth > 0) {
                    writer.endArray()
                    reader.endArray()
                }
            }
            JsonToken.END_OBJECT -> {
                if (depth > 0) {
                    writer.endObject()
                    reader.endObject()
                }
            }
            else -> {}
        }
    }
}

class StructuredLogParseException(msg: String, rootCause: Throwable? = null) : Exception(msg, rootCause)

data class StructuredLogMetadata(
    val schemaVersion: Int,
    val cid: LogcatCollectionId,
    val nextCid: LogcatCollectionId,
    val linuxBootId: UUID,
)

private const val SCHEMA_VERSION = "schema_version"
private const val LINUX_BOOT_ID = "linux_boot_id"
private const val CID = "cid"
private const val NEXT_CID = "next_cid"
private const val EVENTS = "events"
private const val DEVICE_SERIAL = "device_serial"
private const val SOFTWARE_VERSION = "software_version"
private const val HARDWARE_VERSION = "hardware_version"
