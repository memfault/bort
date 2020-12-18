package com.memfault.bort.time

import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class InstantAsIso8601String : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor(
        "com.memfault.bort.time.InstantAsISO8601String",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): Instant =
        Instant.from(DateTimeFormatter.ISO_INSTANT.parse(decoder.decodeString()))

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
}
