package com.memfault.usagereporter.clientserver

import android.os.DropBoxManager
import com.memfault.bort.shared.CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.SetReporterSettingsRequest
import com.memfault.usagereporter.ReporterSettings
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.File
import java.io.IOException
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
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
            override val maxFileTransferStorageAge: Duration = 7.days
            override val maxReporterTempStorageBytes: Long = 10000000
            override val maxReporterTempStorageAge: Duration = 1.days
            override val settings: StateFlow<SetReporterSettingsRequest> =
                MutableStateFlow(SetReporterSettingsRequest())
        }
        b2BClientServer = RealB2BClientServer(
            clientServerMode = ClientServerMode.CLIENT,
            getDropBoxManager = { dropboxManager },
            uploadsDir = uploadDir,
            cacheDir = cacheDir,
            port = 1234,
            host = "127.0.0.1",
            retryDelay = 500.milliseconds,
            reporterSettings = reporterSettings,
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testLoopback() {
        val job = GlobalScope.launch {
            b2BClientServer.start(this)
        }
        val file = File.createTempFile("temp", ".$CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG", uploadDir)
        file.writeText("tmp file content")
        b2BClientServer.uploadsQueue.pushOldestFile()
        verify(timeout = 2000) { dropboxManager.addFile(CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG, any(), 0) }
        job.cancel()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReconnection() {
        val job = GlobalScope.launch {
            b2BClientServer.start(this)
        }
        val file = File.createTempFile("temp", ".$CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG", uploadDir)
        file.writeText("tmp file content")
        b2BClientServer.uploadsQueue.pushOldestFile()
        verify(timeout = 2000) { dropboxManager.addFile(CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG, any(), 0) }

        clearMocks(dropboxManager)
        b2BClientServer.clientOrServer.close()
        // Wait for aysync disconnect/reconnect
        Thread.sleep(250)

        val file2 = File.createTempFile("temp", ".$CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG", uploadDir)
        file2.writeText("tmp file content")
        b2BClientServer.uploadsQueue.pushOldestFile()
        verify(timeout = 2000) { dropboxManager.addFile(CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG, any(), 0) }

        job.cancel()
    }

    @Test
    fun testRunChannels() = runTest {
        val connectionHandler = ConnectionHandler(
            files = NoOpSendfileQueue,
            getDropBoxManager = { null },
            tempDirectory = cacheDir,
        )
        val channel = RealASCWrapper(AsynchronousSocketChannel.open())
        val incomingMessages = Channel<BortMessage>()
        val filesChannel = Channel<File?>()
        launch {
            incomingMessages.close()
        }
        try {
            connectionHandler.runChannels(channel, incomingMessages, filesChannel)
        } catch (e: Exception) {
            // Check that it didn't throw when the channel was closed.
            fail("Caught Exception: $e")
        }
    }

    @Test
    fun incrementSendCountOnFailure() = runTest {
        val sendFileQueue = spyk(NoOpSendfileQueue)
        val connectionHandler = ConnectionHandler(
            files = sendFileQueue,
            getDropBoxManager = { null },
            tempDirectory = cacheDir,
        )
        val channel = object : ASCWrapper {
            override suspend fun writeMessage(message: BortMessage) {
                throw IOException("failed")
            }

            override suspend fun readMessages(
                directory: File,
                scope: CoroutineScope,
            ): ReceiveChannel<BortMessage> {
                return Channel()
            }
        }
        val incomingMessages = Channel<BortMessage>()
        val filesChannel = Channel<File?>()
        launch {
            val file = File.createTempFile("temp", ".$CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG", uploadDir)
            file.writeText("tmp file content")
            filesChannel.send(file)
        }
        connectionHandler.runChannels(channel, incomingMessages, filesChannel)
        verify { sendFileQueue.incrementSendCount(any()) }
    }

    @Test
    fun doesNotIncrementSendCountOnSuccess() = runTest {
        val sendFileQueue = spyk(NoOpSendfileQueue)
        val connectionHandler = ConnectionHandler(
            files = sendFileQueue,
            getDropBoxManager = { null },
            tempDirectory = cacheDir,
        )
        val channel = object : ASCWrapper {
            override suspend fun writeMessage(message: BortMessage) {
            }

            override suspend fun readMessages(
                directory: File,
                scope: CoroutineScope,
            ): ReceiveChannel<BortMessage> {
                return Channel()
            }
        }
        val incomingMessages = Channel<BortMessage>()
        val filesChannel = Channel<File?>()
        launch {
            val file = File.createTempFile("temp", ".$CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG", uploadDir)
            file.writeText("tmp file content")
            filesChannel.send(file)
            incomingMessages.close()
        }
        connectionHandler.runChannels(channel, incomingMessages, filesChannel)
        verify(exactly = 0) { sendFileQueue.incrementSendCount(any()) }
    }
}
