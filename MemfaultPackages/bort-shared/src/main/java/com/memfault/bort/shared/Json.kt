package com.memfault.bort

import kotlinx.serialization.json.Json

val BortJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
