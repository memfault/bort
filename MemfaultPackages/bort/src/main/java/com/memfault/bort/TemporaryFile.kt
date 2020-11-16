package com.memfault.bort

import com.memfault.bort.shared.Logger
import java.io.File

interface TemporaryFileFactory {
    val temporaryFileDirectory: File
    fun createTemporaryFile(prefix: String = "tmp", suffix: String?) =
        TemporaryFile(prefix, suffix, temporaryFileDirectory)
}

class TemporaryFile(
    val prefix: String = "tmp",
    val suffix: String? = null,
    val directory: File? = null
) {

    suspend fun <R> useFile(block: suspend (File) -> R): R {
        val file = createTempFile(prefix, suffix, directory)
        return try {
            block(file)
        } finally {
            if (!file.delete()) {
                Logger.w("Failed to delete $file")
            }
        }
    }
}
