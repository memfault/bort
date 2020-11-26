package com.memfault.bort

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TemporaryFileTest {
    @Test
    fun cleanup() {
        lateinit var file: File
        val tempFile = TemporaryFile()
        assertThrows<Exception> {
            runBlocking {
                tempFile.useFile { f, _ ->
                    file = f
                    throw Exception()
                }
            }
        }
        assertFalse(file.exists())
    }

    @Test
    fun preventDeletion() {
        lateinit var file: File
        val tempFile = TemporaryFile()
        runBlocking {
            tempFile.useFile { f, preventDeletion ->
                f.writeText("hi")
                file = f
                preventDeletion()
            }
        }
        assertTrue(file.exists())
    }
}
