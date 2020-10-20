package com.memfault.usagereporter

import android.os.ParcelFileDescriptor
import com.memfault.bort.shared.CommandRunnerOptions
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

val TRUE_COMMAND = listOf("true")
val ECHO_COMMAND = listOf("echo", "hello")
val NON_EXISTENT_COMMAND = listOf("fOoBAr12345")
val YES_COMMAND = listOf("yes")

class CommandRunnerTest {
    lateinit var outputStreamFactoryMock: (ParcelFileDescriptor) -> OutputStream
    lateinit var outputStreamMock: ByteArrayOutputStream

    @Before
    fun setUp() {
        outputStreamMock = spyk(ByteArrayOutputStream(1024))
        outputStreamFactoryMock = mockk()
        every { outputStreamFactoryMock(any()) } answers { outputStreamMock }
    }

    @Test
    fun nullOutFd() {
        val cmd = CommandRunner(
            TRUE_COMMAND,
            CommandRunnerOptions(outFd = null),
            outputStreamFactoryMock
        ).apply { run() }
        assertEquals(cmd.process, null)
        verify(exactly = 0) { outputStreamFactoryMock.invoke(any()) }
    }

    @Test
    fun happyPath() {
        val outFd: ParcelFileDescriptor = mockk()
        val cmd = CommandRunner(
            ECHO_COMMAND,
            CommandRunnerOptions(outFd = outFd),
            outputStreamFactoryMock
        ).apply { run() }
        assertNotNull(cmd.process)
        verify(exactly = 1) { outputStreamFactoryMock.invoke(outFd) }
        verify(exactly = 1) { outputStreamMock.close() }
        assertEquals("hello\n", outputStreamMock.toString("utf8"))
    }

    @Test
    fun badCommand() {
        val outFd: ParcelFileDescriptor = mockk()
        val cmd = CommandRunner(
            NON_EXISTENT_COMMAND,
            CommandRunnerOptions(outFd = outFd),
            outputStreamFactoryMock
        ).apply { run() }
        verify(exactly = 1) { outputStreamMock.close() }
        assertEquals(cmd.process, null)
        assertEquals("", outputStreamMock.toString("utf8"))
    }

    @Test
    fun exceptionDuringCopy() {
        outputStreamMock = mockk()
        every { outputStreamMock.write(any<ByteArray>()) } throws IOException()

        val outFd: ParcelFileDescriptor = mockk()
        val cmd = CommandRunner(
            YES_COMMAND,
            CommandRunnerOptions(outFd = outFd),
            outputStreamFactoryMock
        ).apply { run() }
        assertNotNull(cmd.process)
        cmd.process?.let {
            assert(!it.isAlive)
        }
        verify(exactly = 1) { outputStreamMock.close() }
    }
}
