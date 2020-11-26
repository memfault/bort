package com.memfault.bort

import com.memfault.bort.shared.Logger
import java.io.File

interface TemporaryFileFactory {
    val temporaryFileDirectory: File?
    fun createTemporaryFile(prefix: String = "tmp", suffix: String?) =
        TemporaryFile(prefix, suffix, temporaryFileDirectory)
}

class TemporaryFile(
    val prefix: String = "tmp",
    val suffix: String? = null,
    val directory: File? = null
) {
    suspend fun <R> useFile(block: suspend (file: File, preventDeletion: () -> Unit) -> R): R {
        val file = createTempFile(prefix, suffix, directory)
        var shouldDelete = true
        return try {
            block(file) { shouldDelete = false }
        } finally {
            if (shouldDelete && !file.delete()) {
                Logger.w("Failed to delete $file")
            }
        }
    }
}
