package com.memfault.usagereporter.clientserver

import com.memfault.usagereporter.clientserver.BortMessage.Companion.readMessage
import com.memfault.usagereporter.clientserver.BortMessage.SendFileMessage
import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ProtocolTest {
    private lateinit var outputFolder: File

    @BeforeEach
    fun setup() {
        outputFolder = createOutputFolder()
    }

    @Test
    fun testFileTransfer() = runBlocking {
        val sockets = openSockets()
        sockets.use {
            assertSendFile(sockets.client, sockets.serverSocket)
        }
    }

    @Test
    fun testFileMultipleFileTransferBidirectional() = runBlocking {
        val sockets = openSockets()
        sockets.use {
            assertSendFile(sockets.client, sockets.serverSocket)
            assertSendFile(sockets.serverSocket, sockets.client)
            assertSendFile(sockets.client, sockets.serverSocket)
        }
    }

    private suspend fun assertSendFile(
        from: AsynchronousSocketChannel,
        to: AsynchronousSocketChannel,
    ) {
        val inputFile = createInputFile()
        from.writeMessage(SendFileMessage(inputFile))
        val outputMessage = to.readMessage(outputFolder)
        check(outputMessage is SendFileMessage)
        outputMessage.file.deleteOnExit()
        assertEquals(inputFile.name, outputMessage.file.name)
        assertEquals(inputFile.length(), outputMessage.file.length())
        assertEquals(inputFile.readText(), outputMessage.file.readText())
    }

    private fun createInputFile(): File {
        val content = "content" + UUID.randomUUID()
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
        val serverSocket: AsynchronousSocketChannel,
        val client: AsynchronousSocketChannel,
    ) : Closeable {
        override fun close() {
            server.close()
            serverSocket.close()
            client.close()
        }
    }

    private suspend fun CoroutineScope.openSockets(): Sockets {
        val server = AsynchronousServerSocketChannel.open()
        server.bind(InetSocketAddress(8877))
        val serverSocketDeferred = async { server.cAccept() }
        val client = AsynchronousSocketChannel.open()
        val clientDeferred = async { client.cConnect("127.0.0.1", 8877) }
        val serverSocket = serverSocketDeferred.await()
        clientDeferred.await()
        return Sockets(server = server, serverSocket = serverSocket, client = client)
    }
}
