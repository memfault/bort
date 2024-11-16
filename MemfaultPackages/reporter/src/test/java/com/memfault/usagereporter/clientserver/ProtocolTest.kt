package com.memfault.usagereporter.clientserver

import com.memfault.usagereporter.clientserver.BortMessage.Companion.readMessage
import com.memfault.usagereporter.clientserver.BortMessage.SendFileMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.UUID
import java.util.concurrent.Future
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class ProtocolTest {
    private lateinit var outputFolder: File

    @BeforeEach
    fun setup() {
        outputFolder = createOutputFolder()
    }

    @Test
    fun testFileTransfer() = runBlocking {
        val sockets = openSockets(TestChannelConfig())
        sockets.use {
            assertSendFile(sockets.client, sockets.serverSocket)
        }
    }

    @Test
    fun testFileMultipleFileTransferBidirectional() = runBlocking {
        val sockets = openSockets(TestChannelConfig())
        sockets.use {
            assertSendFile(sockets.client, sockets.serverSocket)
            assertSendFile(sockets.serverSocket, sockets.client)
            assertSendFile(sockets.client, sockets.serverSocket)
        }
    }

    /**
     * Simulates a congested connection where reads can be smaller chunks than were written, requiring re-assembly.
     */
    @Test
    fun testFileTransferWithSlowSocket() = runBlocking {
        val sockets = openSockets(TestChannelConfig(delayChunkSize = 550, delayFor = 100.milliseconds))
        sockets.use {
            assertSendFile(sockets.client, sockets.serverSocket)
        }
    }

    private suspend fun CoroutineScope.assertSendFile(
        from: AsynchronousByteChannel,
        to: AsynchronousByteChannel,
    ) {
        val scope = this
        val inputFile = createInputFile()
        val tag = UUID.randomUUID().toString()
        // Let the send take place async with the receive (i.e. don't flush all sent bytes before receiving).
        scope.async { from.writeMessage(SendFileMessage(inputFile, tag)) }
        val outputMessage = to.readMessage(outputFolder)
        check(outputMessage is SendFileMessage)
        outputMessage.file.deleteOnExit()
        assertEquals(inputFile.name, outputMessage.file.name)
        assertEquals(inputFile.length(), outputMessage.file.length())
        assertEquals(inputFile.readText(), outputMessage.file.readText())
        assertEquals(tag, outputMessage.dropboxTag)
    }

    private fun createInputFile(): File {
        val content = "content" + Random.nextBytes(10000).toString(Charsets.UTF_8)
        val inputFile = File.createTempFile(UUID.randomUUID().toString(), "")
        inputFile.mkdirs()
        inputFile.deleteOnExit()
        inputFile.writeText(content)
        return inputFile
    }

    private fun createOutputFolder(): File {
        val outputFolder = File(createInputFile().parent, "output")
        outputFolder.mkdir()
        return outputFolder
    }

    private data class Sockets(
        val server: AsynchronousServerSocketChannel,
        val serverSocket: AsynchronousByteChannel,
        val client: AsynchronousByteChannel,
    ) : Closeable {
        override fun close() {
            server.close()
            serverSocket.close()
            client.close()
        }
    }

    private suspend fun CoroutineScope.openSockets(testConfig: TestChannelConfig): Sockets {
        val server = AsynchronousServerSocketChannel.open()
        server.bind(InetSocketAddress(8877))
        val serverSocketDeferred = async { server.cAccept() }
        val client = AsynchronousSocketChannel.open()
        val clientDeferred = async { client.cConnect("127.0.0.1", 8877) }
        val serverSocket = serverSocketDeferred.await()
        clientDeferred.await()
        return Sockets(
            server = server,
            serverSocket = TestAsyncChannel(serverSocket, testConfig),
            client = TestAsyncChannel(client, testConfig),
        )
    }

    data class TestChannelConfig(
        val delayChunkSize: Int = 0,
        val delayFor: Duration = Duration.ZERO,
    )

    /**
     * [AsynchronousByteChannel] implementation which forwards to a delegate, with configurable chunking/delay
     * behaviour to exercise various bits of our code.
     */
    class TestAsyncChannel(private val delegate: AsynchronousByteChannel, private val testConfig: TestChannelConfig) :
        AsynchronousByteChannel by delegate {
        override fun <A : Any?> write(src: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
            val originalLimit = src.limit()
            if (testConfig.delayFor > Duration.ZERO &&
                testConfig.delayChunkSize > 0 &&
                testConfig.delayChunkSize < src.remaining()
            ) {
                // Limit will ensure that only this many bytes will be written.
                src.limit(src.position() + testConfig.delayChunkSize)
            }
            val delayingHandler = object : CompletionHandler<Int, A> {
                override fun completed(result: Int, attachment: A) {
                    // Delay sending the result back to caller, according to config.
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(testConfig.delayFor)
                        handler.completed(result, attachment)
                    }
                }

                override fun failed(exc: Throwable?, attachment: A) {
                    handler.failed(exc, attachment)
                }
            }
            delegate.write(src, attachment, delayingHandler)
            // Always leave the limit how we found it.
            src.limit(originalLimit)
        }

        override fun write(src: ByteBuffer): Future<Int> {
            TODO("Not yet implemented (implement if Bort ever uses this)")
        }
    }
}
