package com.memfault.usagereporter

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.shared.Command
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.RunCommandContinue
import com.memfault.bort.shared.RunCommandRequest
import com.memfault.bort.shared.RunCommandResponse
import com.memfault.bort.shared.ServiceMessage
import com.memfault.bort.shared.SetLogLevelRequest
import com.memfault.bort.shared.SetLogLevelResponse
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

data class UnknownMessage(override val messageId: Int = Int.MAX_VALUE) : ReporterServiceMessage() {
    override fun toBundle(): Bundle = Bundle()
}

interface Replier {
    fun sendReply(serviceMessage: ServiceMessage)
}

class ReporterServiceTest {
    lateinit var messageHandler: ReporterServiceMessageHandler
    lateinit var replier: Replier
    lateinit var enqueueCommand: (List<String>, CommandRunnerOptions, CommandRunnerReportResult) -> CommandRunner
    lateinit var reportResultSlot: CapturingSlot<CommandRunnerReportResult>
    lateinit var logLevel: LogLevel
    private val readFd: ParcelFileDescriptor = mockk(relaxed = true)
    private val writeFd: ParcelFileDescriptor = mockk()

    @Before
    fun setUp() {
        mockkConstructor()
        replier = mockk(name = "replier", relaxed = true)

        enqueueCommand = mockk(name = "enqueueCommand")
        reportResultSlot = slot<CommandRunnerReportResult>()
        every { enqueueCommand(any(), any(), capture(reportResultSlot)) } returns mockk()
        logLevel = LogLevel.NONE

        messageHandler = ReporterServiceMessageHandler(
            setLogLevel = { level -> logLevel = level },
            serviceMessageFromMessage = ReporterServiceMessage.Companion::fromMessage,
            getSendReply = { replier::sendReply },
            enqueueCommand = enqueueCommand,
            b2BClientServer = mockk(),
            reporterSettings = mockk(),
            createPipe = { arrayOf(readFd, writeFd) },
        )
    }

    @Test
    fun handlesVersionMessage() {
        messageHandler.handleServiceMessage(VersionRequest(), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(VersionResponse::class)) }
    }

    @Test
    fun unknownMessageRespondsWithError() {
        messageHandler.handleServiceMessage(UnknownMessage(), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(ErrorResponse::class)) }
    }

    @Test
    fun unexpectedMessageRespondsWithError() {
        // Using an ErrorResponse here -- clients should not be sending that to the service
        messageHandler.handleServiceMessage(ErrorResponse("Client sending error!?"), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(ErrorResponse::class)) }
    }

    @Test
    fun handlesSendReplyRemoteException() {
        every { replier.sendReply(any()) } throws RemoteException()
        // Should not throw:
        messageHandler.handleServiceMessage(SetLogLevelRequest(LogLevel.TEST), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(SetLogLevelResponse::class)) }
    }

    @Test
    fun setLogLevel() {
        messageHandler.handleServiceMessage(SetLogLevelRequest(LogLevel.TEST), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(SetLogLevelResponse::class)) }
        assertThat(logLevel).isEqualTo(LogLevel.TEST)
    }

    @Test
    fun commandRunnerRequest() {
        val message: Message = mockk()
        val replyToMessenger: Messenger = mockk()
        message.replyTo = replyToMessenger
        assertThat(messageHandler.handleServiceMessage(TestRunCommandRequest(), message)).isEqualTo(true)

        verify(exactly = 1) { replier.sendReply(RunCommandContinue(readFd)) }

        // Simulate message getting recycled after returning from handleMessage:
        message.replyTo = null

        reportResultSlot.captured(123, false)
        verify(exactly = 1) { replier.sendReply(RunCommandResponse(123, false)) }
    }
}

class TestCommand : Command {
    override fun toList(): List<String> = emptyList()
    override fun toBundle(): Bundle = mockk()
}

class TestRunCommandRequest() : RunCommandRequest<TestCommand>() {
    override val runnerOptions: CommandRunnerOptions = CommandRunnerOptions(mockk())
    override val command: TestCommand = TestCommand()
    override val messageId: Int = 999999
}
