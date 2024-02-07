package com.memfault.bort

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class TemporaryFileTest {
    @Test
    fun cleanup() = runTest {
        lateinit var file: File
        val tempFile = TemporaryFile()
        assertThrows<Exception> {
            tempFile.useFile { f, _ ->
                file = f
                throw Exception()
            }
        }
        assertFalse(file.exists())
    }

    @Test
    fun preventDeletion() = runTest {
        lateinit var file: File
        val tempFile = TemporaryFile()
        tempFile.useFile { f, preventDeletion ->
            f.writeText("hi")
            file = f
            preventDeletion()
        }
        assertTrue(file.exists())
    }
}
