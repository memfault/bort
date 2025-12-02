package com.memfault.bort.time

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class InstantAsIso8601String : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor(
        "com.memfault.bort.time.InstantAsISO8601String",
        PrimitiveKind.STRING,
    )

    override fun deserialize(decoder: Decoder): Instant =
        Instant.from(DateTimeFormatter.ISO_INSTANT.parse(decoder.decodeString()))

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
}

@OptIn(ExperimentalSerializationApi::class)
class NullableInstantAsIso8601String : KSerializer<Instant?> {
    override val descriptor = PrimitiveSerialDescriptor(
        "com.memfault.bort.time.NullableInstantAsISO8601String",
        PrimitiveKind.STRING,
    )

    override fun deserialize(decoder: Decoder): Instant? =
        if (decoder.decodeNotNullMark()) {
            val dateString = decoder.decodeString()

            if (dateString.isNotEmpty()) {
                try {
                    Instant.from(DateTimeFormatter.ISO_INSTANT.parse(dateString))
                } catch (e: DateTimeParseException) {
                    null
                }
            } else {
                null
            }
        } else {
            decoder.decodeNull()
        }

    override fun serialize(encoder: Encoder, value: Instant?) =
        if (value != null) {
            val dateString = try {
                DateTimeFormatter.ISO_INSTANT.format(value)
            } catch (e: DateTimeParseException) {
                null
            }

            if (dateString != null) {
                encoder.encodeNotNullMark()
                encoder.encodeString(dateString)
            } else {
                encoder.encodeNull()
            }
        } else {
            encoder.encodeNull()
        }
}
