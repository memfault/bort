package com.memfault.usagereporter.clientserver

import com.memfault.bort.fileExt.deleteSilently
import com.memfault.usagereporter.ReporterSettings
import com.memfault.usagereporter.clientserver.RealSendfileQueue.Companion.extractDropboxTag
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RealSendfileQueueTest {
    private val dir = Files.createTempDirectory("send").toFile().also { it.deleteOnExit() }
    private val maxRetryCount = 2
    private val settings = object : ReporterSettings {
        override val maxFileTransferStorageBytes: Long = 0
        override val maxFileTransferStorageAge: Duration = Duration.ZERO
        override val maxReporterTempStorageBytes: Long = 0
        override val maxReporterTempStorageAge: Duration = Duration.ZERO
    }
    private val queue = RealSendfileQueue(dir, settings, maxRetryCount)

    @Test
    fun createsFileCorrectlyNamed() {
        val file = queue.createFile("dropbox_tag")
        assertTrue(file.name.endsWith(".dropbox_tag.0"))
    }

    @Test
    fun deletesAboveRetryCount() {
        val file = queue.createFile("memfault_file_upload")
        file.writeText("content")
        val originalModifiedDate = file.lastModified()
        // Make sure modified timestamp isn't updated when the file is renamed. Ensure clock changes.
        Thread.sleep(20)
        assertTrue(file.name.endsWith(".memfault_file_upload.0"))

        // maxRetryCount = 2: first retry allowed
        queue.incrementSendCount(file)
        assertFalse(file.exists())
        val filesAfter1 = dir.listFiles()
        assertEquals(1, filesAfter1.size)
        val fileAfter1 = filesAfter1[0]
        assertTrue(fileAfter1.name.endsWith(".memfault_file_upload.1"))

        // 2nd retry deleted
        queue.incrementSendCount(fileAfter1)
        assertFalse(fileAfter1.exists())
        val filesAfter2 = dir.listFiles()
        assertEquals(1, filesAfter2.size)
        val fileAfter2 = filesAfter2[0]
        assertTrue(fileAfter2.name.endsWith(".memfault_file_upload.2"))
        assertEquals(originalModifiedDate, fileAfter2.lastModified())

        // 3nd retry now allowed: deleted
        queue.incrementSendCount(fileAfter2)
        assertFalse(fileAfter2.exists())
        val filesAfter3 = dir.listFiles()
        assertEquals(0, filesAfter3.size)
    }

    @Test
    fun noPreviousCountStored_addsCount() {
        val tmp = queue.createFile("memfault_file_upload")
        tmp.deleteSilently()
        val file = File(dir, tmp.name.dropLast(2))
        file.writeText("content")
        tmp.renameTo(file)
        assertFalse(tmp.exists())
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".memfault_file_upload"))

        // maxRetryCount = 2: first retry allowed
        queue.incrementSendCount(file)
        assertFalse(file.exists())
        val filesAfter1 = dir.listFiles()
        assertEquals(1, filesAfter1.size)
        val fileAfter1 = filesAfter1[0]
        assertTrue(fileAfter1.name.endsWith(".memfault_file_upload.1"))
        fileAfter1.deleteSilently()
    }

    @Test
    fun extractTagWithCount() {
        val file = File.createTempFile("temp", ".memfault_file_upload.0")
        assertTrue(file.name.endsWith(".memfault_file_upload.0"))
        assertEquals("memfault_file_upload", file.extractDropboxTag())
    }

    @Test
    fun extractTagWithBigCount() {
        val file = File.createTempFile("temp", ".memfault_file_upload.12")
        assertTrue(file.name.endsWith(".memfault_file_upload.12"))
        assertEquals("memfault_file_upload", file.extractDropboxTag())
    }

    @Test
    fun extractTagWithoutCount() {
        val file = File.createTempFile("temp", ".memfault_file_upload")
        assertTrue(file.name.endsWith(".memfault_file_upload"))
        assertEquals("memfault_file_upload", file.extractDropboxTag())
    }
}
