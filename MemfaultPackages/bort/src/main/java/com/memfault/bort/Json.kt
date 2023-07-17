package com.memfault.bort

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

val BortJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        polymorphicDefaultDeserializer(DataScrubbingRule::class) { UnknownScrubbingRule.serializer() }
    }
}
