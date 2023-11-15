package com.memfault.usagereporter.clientserver

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.CompletionHandler
import kotlin.math.min

internal class SocketsTest {
    private val channel: AsynchronousByteChannel = mockk {
        every { write(any(), Unit, any()) } answers {
            val buffer = firstArg<ByteBuffer>()
            val handler = thirdArg<CompletionHandler<Int, Unit>>()
            val writeNumBytes = min(buffer.remaining(), numBytes)
            buffer.position(buffer.position() + writeNumBytes)
            handler.completed(writeNumBytes, Unit)
        }

        every { read(any(), Unit, any()) } answers {
            val buffer = firstArg<ByteBuffer>()
            val handler = thirdArg<CompletionHandler<Int, Unit>>()
            val readNumBytes = min(buffer.remaining(), numBytes)
            buffer.position(buffer.position() + readNumBytes)
            handler.completed(readNumBytes, Unit)
        }
    }
    private var numBytes = 0

    @Test
    fun singleWrite() = runBlocking {
        numBytes = 50
        val buffer = ByteBuffer.allocate(50)
        assertEquals(50, channel.cWrite(buffer))
        coVerify(exactly = 1) { channel.write(buffer, Unit, any()) }
    }

    @Test
    fun multipleWrites() = runBlocking {
        numBytes = 50
        val buffer = ByteBuffer.allocate(101)
        assertEquals(101, channel.cWrite(buffer))
        coVerify(exactly = 3) { channel.write(buffer, Unit, any()) }
    }

    @Test
    fun singleRead() = runBlocking {
        numBytes = 500
        assertEquals(500, channel.cRead(500).remaining())
        coVerify(exactly = 1) { channel.read(any(), Unit, any()) }
    }

    @Test
    fun multipleReads() = runBlocking {
        numBytes = 1000
        assertEquals(5001, channel.cRead(5001).remaining())
        coVerify(exactly = 6) { channel.read(any(), Unit, any()) }
    }
}
