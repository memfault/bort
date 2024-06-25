package com.memfault.bort.json

import android.util.JsonWriter
import java.io.File

inline fun File?.useJsonWriter(block: (JsonWriter?) -> Unit) {
    if (this == null) {
        block(null)
    } else {
        bufferedWriter().use { bufferedWriter ->
            JsonWriter(bufferedWriter).use { jsonWriter ->
                block(jsonWriter)
            }
        }
    }
}
