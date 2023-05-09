package com.memfault.usagereporter

import android.os.Bundle
import android.os.DropBoxManager
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.memfault.bort.shared.Command
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.DropBoxGetNextEntryRequest
import com.memfault.bort.shared.DropBoxGetNextEntryResponse
import com.memfault.bort.shared.DropBoxSetTagFilterRequest
import com.memfault.bort.shared.DropBoxSetTagFilterResponse
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
import io.mockk.verifyOrder
import io.mockk.verifySequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

data class FakeDropBoxFilterSettingsProvider(
    override var includedTags: Set<String>
) : DropBoxFilterSettingsProvider

data class UnknownMessage(override val messageId: Int = Int.MAX_VALUE) : ReporterServiceMessage() {
    override fun toBundle(): Bundle = Bundle()
}

interface Replier {
    fun sendReply(serviceMessage: ServiceMessage)
}

class ReporterServiceTest {
    lateinit var messageHandler: ReporterServiceMessageHandler
    lateinit var filterSettingsProvider: FakeDropBoxFilterSettingsProvider
    lateinit var replier: Replier
    lateinit var enqueueCommand: (List<String>, CommandRunnerOptions, CommandRunnerReportResult) -> CommandRunner
    lateinit var reportResultSlot: CapturingSlot<CommandRunnerReportResult>
    lateinit var logLevel: LogLevel
    var dropBoxManager: DropBoxManager? = null

    @BeforeEach
    fun setUp() {
        mockkConstructor()
        dropBoxManager = mockk()
        replier = mockk(name = "replier", relaxed = true)

        enqueueCommand = mockk(name = "enqueueCommand")
        reportResultSlot = slot<CommandRunnerReportResult>()
        every { enqueueCommand(any(), any(), capture(reportResultSlot)) } returns mockk()
        logLevel = LogLevel.NONE

        filterSettingsProvider = FakeDropBoxFilterSettingsProvider(emptySet())
        messageHandler = ReporterServiceMessageHandler(
            dropBoxMessageHandler = DropBoxMessageHandler(
                getDropBoxManager = { dropBoxManager },
                filterSettingsProvider = filterSettingsProvider
            ),
            setLogLevel = { level -> logLevel = level },
            serviceMessageFromMessage = ReporterServiceMessage.Companion::fromMessage,
            getSendReply = { replier::sendReply },
            enqueueCommand = enqueueCommand,
            b2BClientServer = mockk(),
            reporterMetrics = mockk(),
            reporterSettings = mockk()
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
        messageHandler.handleServiceMessage(DropBoxSetTagFilterRequest(emptyList()), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(DropBoxSetTagFilterResponse::class)) }
    }

    @Test
    fun setLogLevel() {
        messageHandler.handleServiceMessage(SetLogLevelRequest(LogLevel.TEST), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(SetLogLevelResponse::class)) }
        assertEquals(LogLevel.TEST, logLevel)
    }

    @Test
    fun dropBoxSetTagFilter() {
        val includedTags = listOf("foo", "bar")
        messageHandler.handleServiceMessage(DropBoxSetTagFilterRequest(includedTags), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(DropBoxSetTagFilterResponse::class)) }
        assertEquals(includedTags.toSet(), filterSettingsProvider.includedTags)
    }

    @Test
    fun dropBoxManagerUnavailable() {
        dropBoxManager = null
        messageHandler.handleServiceMessage(DropBoxGetNextEntryRequest(0), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(ErrorResponse::class)) }
    }

    @Test
    fun dropBoxManagerException() {
        every {
            dropBoxManager?.getNextEntry(null, any())
        } throws RemoteException()
        messageHandler.handleServiceMessage(DropBoxGetNextEntryRequest(0), mockk())
        verify(exactly = 1) { replier.sendReply(ofType(ErrorResponse::class)) }
    }

    @Test
    fun dropBoxGetNextEntry() {
        filterSettingsProvider.includedTags = setOf("TEST")
        val filteredEntry = mockk<DropBoxManager.Entry> {
            every { tag } returns "FILTER_ME"
            every { timeMillis } returns 10
            every { close() } returns Unit
        }
        val testEntry = mockk<DropBoxManager.Entry> {
            every { tag } returns "TEST"
            every { timeMillis } returns 20
            every { close() } returns Unit
        }
        every {
            dropBoxManager?.getNextEntry(null, any())
        } returnsMany listOf(filteredEntry, testEntry, null)
        messageHandler.handleServiceMessage(DropBoxGetNextEntryRequest(0), mockk())
        verifySequence {
            dropBoxManager?.getNextEntry(null, 0)
            dropBoxManager?.getNextEntry(null, 10)
        }
        verify(exactly = 1) { replier.sendReply(DropBoxGetNextEntryResponse(testEntry)) }
        verify(exactly = 1) { filteredEntry.close() }
        verify(exactly = 1) { testEntry.close() }
        verifyOrder {
            replier.sendReply(DropBoxGetNextEntryResponse(testEntry))
            testEntry.close()
        }
    }

    @Test
    fun dropBoxGetNextEntryNull() {
        every {
            dropBoxManager?.getNextEntry(null, any())
        } returns null
        messageHandler.handleServiceMessage(DropBoxGetNextEntryRequest(10), mockk())
        verifySequence {
            dropBoxManager?.getNextEntry(null, 10)
        }
        verify(exactly = 1) { replier.sendReply(DropBoxGetNextEntryResponse(null)) }
    }

    @Test
    fun commandRunnerRequest() {
        val message: Message = mockk()
        val replyToMessenger: Messenger = mockk()
        message.replyTo = replyToMessenger
        assertEquals(true, messageHandler.handleServiceMessage(TestRunCommandRequest(), message))

        verify(exactly = 1) { replier.sendReply(ofType(RunCommandContinue::class)) }

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
