package com.memfault.bort.zip

import assertk.assertThat
import assertk.assertions.isNull
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipException
import java.util.zip.ZipFile

class ZipFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun openEmptyFileFailure() {
        val file = folder.newFile("empty")
        assertThrows<ZipException> { ZipFile(file) }
    }

    @Test
    fun openEmptyFileNull() {
        val file = folder.newFile("empty")
        assertThat(openZipFile(file)).isNull()
    }
}
