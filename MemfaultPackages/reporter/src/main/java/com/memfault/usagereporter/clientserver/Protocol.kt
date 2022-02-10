package com.memfault.usagereporter.clientserver

import androidx.annotation.VisibleForTesting
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.usagereporter.clientserver.BortMessage.SendFileMessage.Companion.readSendFileMessage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder.BIG_ENDIAN
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousSocketChannel
import kotlin.time.Duration
import kotlin.time.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Simple protocol for stream variable-length data (files) over a socket connection.
 *
 * A (potentially very large) file is sent in a single message: intended to be streamed directly to/from a file at both
 * ends (i.e. never loaded entirely into memory).
 *
 * On any errors sending/receiving, throw an exception or disconnect - the connection will be re-estalished/reset.
 *
 * Format: Big Endian
 *
 * Header
 * - Version: Int (4 bytes)
 * - Message type: Long (8 bytes)
 *
 * Content
 * - Varies (only SendFileMessage right now):
 *
 *   SendFileMessage:
 *   - Filename length: Int (4 bytes)
 *   - Filename string bytes
 *   - File length: Long (8 bytes)
 *   - File bytes
 *
 * Footer
 * - End marker: Long (8 bytes)
 *
 */
sealed class BortMessage {
    protected abstract val messageType: Long

    /**
     * Write this message to the channel.
     */
    suspend fun writeMessage(channel: AsynchronousSocketChannel, timeout: Duration = TIMEOUT) = channel.apply {
        // Timeout writing a single message is an error and causes a disconnection.
        withTimeout(timeout) {
            writeBytes(HEADER_SIZE) {
                putInt(VERSION)
                putLong(messageType)
            }

            // Delegate to subclass.
            writeInternal()

            writeBytes(FOOTER_SIZE) {
                putLong(END_MARKER)
            }
        }
    }

    protected abstract suspend fun AsynchronousByteChannel.writeInternal()

    companion object {
        @VisibleForTesting
        const val HEADER_SIZE = 8 + 4
        private const val FOOTER_SIZE = 8
        private val TIMEOUT = 10.seconds

        /**
         * Receive messages in a loop, until disconnection. Upon error, disconnect.
         */
        suspend fun AsynchronousByteChannel.readMessages(
            directory: File,
            scope: CoroutineScope,
        ): ReceiveChannel<BortMessage> {
            val channel = Channel<BortMessage>()
            scope.launch {
                try {
                    while (true) {
                        channel.send(readMessage(directory))
                    }
                } catch (e: Exception) {
                    // Closing the channel will cause the select clause reading this to throw, triggering a disconnect/
                    // reconnect cycle in the Connector.
                    channel.close()
                }
            }
            return channel
        }

        /**
         * Read a single message from the channel.
         */
        suspend fun AsynchronousByteChannel.readMessage(directory: File, timeout: Duration = TIMEOUT): BortMessage {
            val headerBuffer = readBuffer(HEADER_SIZE)
            val version = headerBuffer.getInt()
            check(version == VERSION)
            val messageType = headerBuffer.getLong()

            // Once we have received the message header, a timeout is an error and should cause disconnection.
            return withTimeout(timeout) {
                val message = when (messageType) {
                    SendFileMessage.MESSAGE_TYPE -> readSendFileMessage(directory)
                    else -> throw IllegalStateException("Unknown message type: $messageType")
                }
                val footerBuffer = readBuffer(FOOTER_SIZE).getLong()
                check(footerBuffer == END_MARKER)
                message
            }
        }
    }

    /**
     * Message which streams a file over the socket connection.
     */
    data class SendFileMessage(
        val file: File,
    ) : BortMessage() {
        override val messageType = MESSAGE_TYPE

        override suspend fun AsynchronousByteChannel.writeInternal() {
            writeFileWithName(file)
        }

        companion object {
            const val MESSAGE_TYPE: Long = 0xB087F14E

            suspend fun AsynchronousByteChannel.readSendFileMessage(directory: File): BortMessage {
                val file = readFileWithName(directory)
                return SendFileMessage(file)
            }
        }
    }
}

suspend fun AsynchronousSocketChannel.writeMessage(message: BortMessage) = message.writeMessage(this)

/**
 * Reading/writing a String to the channel.
 *
 * - 4 byte length Int
 * - String bytes
 */

private suspend fun AsynchronousByteChannel.readString(): String {
    val lenBuffer = readBuffer(4)
    val len = lenBuffer.getInt()
    return readBuffer(len).array().decodeToString()
}

private suspend fun AsynchronousByteChannel.writeString(string: String) {
    val nameBytes = string.encodeToByteArray()
    writeBytes(nameBytes.size + 4) {
        putInt(nameBytes.size)
        put(nameBytes)
    }
}

/**
 * Stream a file to/from the channel.
 *
 * - 8 byte Long: file length in bytes.
 * - File bytes
 */

private suspend fun AsynchronousByteChannel.readFileWithName(directory: File): File {
    val fileName = readString()
    val size = readBuffer(8).getLong()

    val tempFile = createTempFile(prefix = "transfer", suffix = null, directory = directory)
    // Stream file to channel in chunks of BUFFER_SIZE
    tempFile.outputStream().use { output ->
        var remaining = size
        while (remaining > 0) {
            val numBytes = BUFFER_SIZE.coerceAtMost(remaining.toInt())
            val bytes = readBuffer(numBytes).array()
            check(bytes.size == numBytes)
            output.write(bytes)
            remaining -= numBytes
        }
    }

    check(tempFile.length() == size)
    val destFile = File(directory, fileName)
    destFile.deleteSilently()
    tempFile.renameTo(destFile)
    return destFile
}

@VisibleForTesting
suspend fun AsynchronousByteChannel.writeFileWithName(file: File) {
    writeString(file.name)
    writeBytes(8) {
        putLong(file.length())
    }

    // Stream the file, so we don't load it all into memory.
    file.inputStream().use { input ->
        var remaining = file.length()
        while (remaining > 0) {
            // Stream file from channel in chunks of BUFFER_SIZE
            val numBytes = BUFFER_SIZE.coerceAtMost(remaining.toInt())
            val bytes = ByteArray(numBytes)
            val read = input.read(bytes)
            check(read == numBytes)
            val written = cWrite(bytes)
            check(written == numBytes)
            remaining -= read
        }
    }
}

private suspend fun AsynchronousByteChannel.readBuffer(len: Int) = cRead(len).apply { order(BIG_ENDIAN) }

/**
 * Write a fixed number of bytes ([size]) to the channel.
 *
 * Wraps [ByteBuffer] configuration.
 */
@VisibleForTesting
suspend fun AsynchronousByteChannel.writeBytes(size: Int, block: ByteBuffer.() -> Unit) {
    val buffer = ByteBuffer.allocate(size)
    buffer.order(BIG_ENDIAN)
    block(buffer)
    check(buffer.position() == size)
    buffer.rewind()
    cWrite(buffer)
}

private const val END_MARKER: Long = 0xB087E8D
@VisibleForTesting
const val VERSION: Int = 1
private const val BUFFER_SIZE = 1000
