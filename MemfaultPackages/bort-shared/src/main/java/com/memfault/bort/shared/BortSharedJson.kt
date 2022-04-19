package com.memfault.bort.shared

import kotlinx.serialization.json.Json

/**
 * A mirror of BortJson, which can be used outside of the Bort app.
 */
val BortSharedJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
