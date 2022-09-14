package com.memfault.bort.time

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Kotlin Serialization does not work (yet) with inline classes :(
// https://github.com/Kotlin/kotlinx.serialization/issues/1063
@Serializable(with = DurationAsMillisecondsLong::class)
data class BoxedDuration(
    val duration: Duration,
)

fun Duration.boxed() = BoxedDuration(this)

class DurationAsMillisecondsLong : KSerializer<BoxedDuration> {
    override val descriptor = PrimitiveSerialDescriptor(
        "com.memfault.bort.time.DurationAsMillisecondsLong",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): BoxedDuration =
        decoder.decodeLong().milliseconds.boxed()

    override fun serialize(encoder: Encoder, value: BoxedDuration) =
        encoder.encodeLong(value.duration.inWholeMilliseconds)
}
