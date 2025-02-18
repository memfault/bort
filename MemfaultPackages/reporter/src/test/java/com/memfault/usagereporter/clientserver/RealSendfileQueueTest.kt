package com.memfault.usagereporter.clientserver

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.shared.SetReporterSettingsRequest
import com.memfault.usagereporter.ReporterSettings
import com.memfault.usagereporter.clientserver.RealSendfileQueue.Companion.extractDropboxTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration

internal class RealSendfileQueueTest {
    private val dir = Files.createTempDirectory("send").toFile().also { it.deleteOnExit() }
    private val maxRetryCount = 2
    private val settings = object : ReporterSettings {
        override val maxFileTransferStorageBytes: Long = 0
        override val maxFileTransferStorageAge: Duration = Duration.ZERO
        override val maxReporterTempStorageBytes: Long = 0
        override val maxReporterTempStorageAge: Duration = Duration.ZERO
        override val settings: StateFlow<SetReporterSettingsRequest> =
            MutableStateFlow(SetReporterSettingsRequest())
    }
    private val queue = RealSendfileQueue(dir, settings, maxRetryCount)

    @Test
    fun createsFileCorrectlyNamed() {
        val file = queue.createFile("dropbox_tag")
        assertThat(file.name.endsWith(".dropbox_tag.0")).isTrue()
    }

    @Test
    fun deletesAboveRetryCount() {
        val file = queue.createFile("memfault_file_upload")
        file.writeText("content")
        val originalModifiedDate = file.lastModified()
        // Make sure modified timestamp isn't updated when the file is renamed. Ensure clock changes.
        Thread.sleep(20)
        assertThat(file.name.endsWith(".memfault_file_upload.0")).isTrue()

        // maxRetryCount = 2: first retry allowed
        queue.incrementSendCount(file)
        assertThat(file.exists()).isFalse()
        val filesAfter1 = dir.listFiles()!!
        assertThat(filesAfter1.size).isEqualTo(1)
        val fileAfter1 = filesAfter1[0]
        assertThat(fileAfter1.name.endsWith(".memfault_file_upload.1")).isTrue()

        // 2nd retry deleted
        queue.incrementSendCount(fileAfter1)
        assertThat(fileAfter1.exists()).isFalse()
        val filesAfter2 = dir.listFiles()!!
        assertThat(filesAfter2.size).isEqualTo(1)
        val fileAfter2 = filesAfter2[0]
        assertThat(fileAfter2.name.endsWith(".memfault_file_upload.2")).isTrue()
        assertThat(fileAfter2.lastModified()).isEqualTo(originalModifiedDate)

        // 3nd retry now allowed: deleted
        queue.incrementSendCount(fileAfter2)
        assertThat(fileAfter2.exists()).isFalse()
        val filesAfter3 = dir.listFiles()!!
        assertThat(filesAfter3.size).isEqualTo(0)
    }

    @Test
    fun noPreviousCountStored_addsCount() {
        val tmp = queue.createFile("memfault_file_upload")
        tmp.deleteSilently()
        val file = File(dir, tmp.name.dropLast(2))
        file.writeText("content")
        tmp.renameTo(file)
        assertThat(tmp.exists()).isFalse()
        assertThat(file.exists()).isTrue()
        assertThat(file.name.endsWith(".memfault_file_upload")).isTrue()

        // maxRetryCount = 2: first retry allowed
        queue.incrementSendCount(file)
        assertThat(file.exists()).isFalse()
        val filesAfter1 = dir.listFiles()!!
        assertThat(filesAfter1.size).isEqualTo(1)
        val fileAfter1 = filesAfter1[0]
        assertThat(fileAfter1.name.endsWith(".memfault_file_upload.1")).isTrue()
        fileAfter1.deleteSilently()
    }

    @Test
    fun extractTagWithCount() {
        val file = File.createTempFile("temp", ".memfault_file_upload.0")
        assertThat(file.name.endsWith(".memfault_file_upload.0")).isTrue()
        assertThat(file.extractDropboxTag()).isEqualTo("memfault_file_upload")
    }

    @Test
    fun extractTagWithBigCount() {
        val file = File.createTempFile("temp", ".memfault_file_upload.12")
        assertThat(file.name.endsWith(".memfault_file_upload.12")).isTrue()
        assertThat(file.extractDropboxTag()).isEqualTo("memfault_file_upload")
    }

    @Test
    fun extractTagWithoutCount() {
        val file = File.createTempFile("temp", ".memfault_file_upload")
        assertThat(file.name.endsWith(".memfault_file_upload")).isTrue()
        assertThat(file.extractDropboxTag()).isEqualTo("memfault_file_upload")
    }
}
