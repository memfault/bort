package com.memfault.bort

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class TemporaryFileTest {
    @Test
    fun cleanup() = runTest {
        lateinit var file: File
        val tempFile = TemporaryFile()
        assertFailure {
            tempFile.useFile { f, _ ->
                file = f
                throw Exception()
            }
        }.isInstanceOf<Exception>()
        assertThat(file.exists()).isFalse()
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
        assertThat(file.exists()).isTrue()
    }
}
