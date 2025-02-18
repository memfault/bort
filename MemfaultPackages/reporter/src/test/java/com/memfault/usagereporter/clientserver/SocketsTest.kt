package com.memfault.usagereporter.clientserver

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
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
    fun singleWrite() = runTest {
        numBytes = 50
        val buffer = ByteBuffer.allocate(50)
        assertThat(channel.cWrite(buffer)).isEqualTo(50)
        coVerify(exactly = 1) { channel.write(buffer, Unit, any()) }
    }

    @Test
    fun multipleWrites() = runTest {
        numBytes = 50
        val buffer = ByteBuffer.allocate(101)
        assertThat(channel.cWrite(buffer)).isEqualTo(101)
        coVerify(exactly = 3) { channel.write(buffer, Unit, any()) }
    }

    @Test
    fun singleRead() = runTest {
        numBytes = 500
        assertThat(channel.cRead(500).remaining()).isEqualTo(500)
        coVerify(exactly = 1) { channel.read(any(), Unit, any()) }
    }

    @Test
    fun multipleReads() = runTest {
        numBytes = 1000
        assertThat(channel.cRead(5001).remaining()).isEqualTo(5001)
        coVerify(exactly = 6) { channel.read(any(), Unit, any()) }
    }
}
