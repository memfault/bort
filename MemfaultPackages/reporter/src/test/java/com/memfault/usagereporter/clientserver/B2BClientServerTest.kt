package com.memfault.usagereporter.clientserver

import android.os.DropBoxManager
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.usagereporter.ReporterSettings
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlin.time.milliseconds
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class B2BClientServerTest {
    lateinit var uploadDir: File
    lateinit var cacheDir: File
    lateinit var dropboxManager: DropBoxManager
    lateinit var b2BClientServer: RealB2BClientServer
    lateinit var reporterSettings: ReporterSettings

    @BeforeEach
    fun setup() {
        cacheDir = Files.createTempDirectory("cache").toFile()
        cacheDir.deleteOnExit()
        uploadDir = Files.createTempDirectory("uploads").toFile()
        uploadDir.deleteOnExit()
        dropboxManager = mockk {
            every { addFile(any(), any(), any()) } answers { }
        }
        reporterSettings = object : ReporterSettings {
            override val maxFileTransferStorageBytes: Long = 50000000
        }
        b2BClientServer = RealB2BClientServer(
            clientServerMode = ClientServerMode.CLIENT,
            getDropBoxManager = { dropboxManager },
            uploadsDir = uploadDir,
            cacheDir = cacheDir,
            port = 1234,
            host = "127.0.0.1",
            retryDelay = 500.milliseconds,
            reporterSettings = reporterSettings
        )
    }

    @Test
    fun testLoopback() {
        val job = GlobalScope.launch {
            b2BClientServer.start(this)
        }
        val file = File.createTempFile("temp", ".$CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG", uploadDir)
        file.writeText("tmp file content")
        b2BClientServer.uploadsQueue.pushOldestFile()
        verify(timeout = 1000) { dropboxManager.addFile(CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG, any(), 0) }
        job.cancel()
    }

    @Test
    fun testReconnection() {
        val job = GlobalScope.launch {
            b2BClientServer.start(this)
        }
        val file = File.createTempFile("temp", ".$CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG", uploadDir)
        file.writeText("tmp file content")
        b2BClientServer.uploadsQueue.pushOldestFile()
        verify(timeout = 1000) { dropboxManager.addFile(CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG, any(), 0) }

        clearMocks(dropboxManager)
        b2BClientServer.clientOrServer!!.close()
        // Wait for aysync disconnect/reconnect
        Thread.sleep(100)

        val file2 = File.createTempFile("temp", ".$CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG", uploadDir)
        file2.writeText("tmp file content")
        b2BClientServer.uploadsQueue.pushOldestFile()
        verify(timeout = 1000) { dropboxManager.addFile(CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG, any(), 0) }

        job.cancel()
    }
}
