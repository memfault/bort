package com.memfault.bort

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class FileAsAbsolutePathSerializer : KSerializer<File> {
    override val descriptor = PrimitiveSerialDescriptor(
        "com.memfault.bort.FileAsAbsolutePathSerializer",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): File =
        decoder.decodeString().let { File(it) }

    override fun serialize(encoder: Encoder, value: File) =
        encoder.encodeString(value.absolutePath)
}
