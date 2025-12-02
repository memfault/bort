package com.memfault.bort.boot

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class LinuxBootIdFileReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val fileProvider = object : LinuxBootIdFileProvider {
        var file: File? = null
        override fun invoke(): File = requireNotNull(file)
    }

    private val reader = LinuxBootIdFileReader(fileProvider)

    @Test
    fun valid() {
        val tempFile = folder.newFile()
        tempFile.writeText("599cbcd2-b939-4734-8d7c-c6aa5187aa28\n")
        fileProvider.file = tempFile

        val uuidString = reader.invoke()
        assertThat(UUID.fromString(uuidString)).isEqualTo(UUID.fromString("599cbcd2-b939-4734-8d7c-c6aa5187aa28"))

        // delete the temp file and read again to ensure the value is cached
        tempFile.delete()
        val cachedUuidString = reader.invoke()
        assertThat(cachedUuidString).isEqualTo(uuidString)
    }

    @Test
    fun fallbackIsValidUuid() {
        fileProvider.file = File("")
        val uuidString = reader.invoke()

        assertThat(UUID.fromString(uuidString)).isEqualTo(UUID(0, 0))
    }
}
