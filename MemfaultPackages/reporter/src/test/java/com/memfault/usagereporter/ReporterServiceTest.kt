package com.memfault.usagereporter

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.memfault.bort.shared.Command
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.RunCommandContinue
import com.memfault.bort.shared.RunCommandRequest
import com.memfault.bort.shared.RunCommandResponse
import com.memfault.bort.shared.SetLogLevelRequest
import com.memfault.bort.shared.SetLogLevelResponse
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

data class UnknownMessage(override val messageId: Int = Int.MAX_VALUE) : ReporterServiceMessage() {
    override fun toBundle(): Bundle = Bundle()
}

@RunWith(RobolectricTestRunner::class)
class ReporterServiceTest {
    private lateinit var messageHandler: ReporterServiceMessageHandler

    private val replyMessageSlots = mutableListOf<Message>()

    private val messenger = mockk<Messenger> {
        every { send(capture(replyMessageSlots)) } just Runs
    }
    private val message = Message()

    private val threadPoolExecutor = TimeoutThreadPoolExecutor(1)
    private val logLevelPreferenceProvider = FakeLogLevelPreferenceProvider()

    private val readFd: ParcelFileDescriptor = mockk(relaxed = true)
    private val writeFd: ParcelFileDescriptor = mockk()

    @Before
    fun setUp() {
        message.replyTo = messenger

        messageHandler = ReporterServiceMessageHandler(
            commandExecutor = threadPoolExecutor,
            serviceMessageFromMessage = ReporterServiceMessage.Companion::fromMessage,
            logLevelPreferenceProvider = logLevelPreferenceProvider,
            b2BClientServer = mockk(),
            reporterSettings = mockk(),
            createPipe = { arrayOf(readFd, writeFd) },
        )
    }

    @Test
    fun handlesVersionMessage() {
        messageHandler.handleServiceMessage(VersionRequest(), message)
        assertThat(ReporterServiceMessage.fromMessage(replyMessageSlots.first()))
            .isInstanceOf(VersionResponse::class)
    }

    @Test
    fun unknownMessageRespondsWithError() {
        messageHandler.handleServiceMessage(UnknownMessage(), message)
        assertThat(ReporterServiceMessage.fromMessage(replyMessageSlots.first()))
            .isInstanceOf(ErrorResponse::class)
    }

    @Test
    fun unexpectedMessageRespondsWithError() {
        // Using an ErrorResponse here -- clients should not be sending that to the service
        messageHandler.handleServiceMessage(ErrorResponse("Client sending error!?"), message)
        assertThat(ReporterServiceMessage.fromMessage(replyMessageSlots.first()))
            .isInstanceOf(ErrorResponse::class)
    }

    @Test
    fun setLogLevel() {
        messageHandler.handleServiceMessage(SetLogLevelRequest(LogLevel.TEST), message)
        assertThat(ReporterServiceMessage.fromMessage(replyMessageSlots.first()))
            .isInstanceOf(SetLogLevelResponse::class)
        assertThat(logLevelPreferenceProvider.getLogLevel()).isEqualTo(LogLevel.TEST)
    }

    @Test
    fun commandRunnerRequest() {
        assertThat(messageHandler.handleServiceMessage(TestRunCommandRequest(), message)).isEqualTo(true)
        val task = threadPoolExecutor.submitWithTimeout(
            object : TimeoutRunnable {
                override fun run() = Unit
                override fun handleTimeout() = Unit
            },
            1.minutes,
        )
        task.get()
        assertThat(replyMessageSlots).hasSize(2)

        val serviceMessage1 = ReporterServiceMessage.fromMessage(replyMessageSlots[0])
        assertThat(serviceMessage1)
            .isInstanceOf(RunCommandContinue::class)

        val serviceMessage2 = ReporterServiceMessage.fromMessage(replyMessageSlots[1])
        assertThat(serviceMessage2)
            .isEqualTo(RunCommandResponse(null, false))
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
