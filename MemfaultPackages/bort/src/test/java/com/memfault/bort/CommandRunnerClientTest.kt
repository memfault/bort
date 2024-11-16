package com.memfault.bort

import android.os.ParcelFileDescriptor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.memfault.bort.CommandRunnerMode.BortCreatesPipes
import com.memfault.bort.CommandRunnerMode.ReporterCreatesPipes
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.RunCommandContinue
import com.memfault.bort.shared.RunCommandResponse
import com.memfault.bort.shared.ServiceMessageReplyHandler
import com.memfault.bort.shared.result.StdResult
import com.memfault.bort.shared.result.failure
import com.memfault.bort.shared.result.isFailure
import com.memfault.bort.shared.result.success
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileInputStream
import java.io.InputStream
import java.lang.IllegalArgumentException
import kotlin.time.Duration.Companion.milliseconds

class CommandRunnerClientTest {
    lateinit var legacyMockInputStream: FileInputStream
    lateinit var mockInputStream: FileInputStream
    lateinit var legacyMockWriteFd: ParcelFileDescriptor
    lateinit var mockWriteFd: ParcelFileDescriptor

    lateinit var client: CommandRunnerClient

    lateinit var block: suspend (CommandRunnerClient.Invocation) -> StdResult<Boolean>
    lateinit var sendRequest:
        suspend (CommandRunnerOptions) -> StdResult<ServiceMessageReplyHandler<ReporterServiceMessage>>
    lateinit var replyHandler: ServiceMessageReplyHandler<ReporterServiceMessage>
    lateinit var gotInputStream: (StdResult<InputStream>) -> Unit
    lateinit var inputStreamFactory: (ParcelFileDescriptor) -> FileInputStream

    var response: StdResult<RunCommandResponse>? = null

    @BeforeEach
    fun setUp() {
        legacyMockInputStream = mockk(relaxed = true)
        mockInputStream = mockk(relaxed = true)
        legacyMockWriteFd = mockk(relaxed = true)
        mockWriteFd = mockk(relaxed = true)
        gotInputStream = mockk(relaxed = true)
        inputStreamFactory = { pfd ->
            if (pfd == mockWriteFd) {
                mockInputStream
            } else {
                throw IllegalArgumentException("invalid pfd")
            }
        }

        block = mockk {
            coEvery { this@mockk(any()) } coAnswers { call ->
                val invocation = (call.invocation.args[0] as CommandRunnerClient.Invocation)
                // Note: the verify() API cannot be used from the answering API, so call a mock to record that the
                // inputStream has been received:
                gotInputStream(invocation.awaitInputStream(1.milliseconds))
                response = invocation.awaitResponse(1.milliseconds)
                Result.success(true)
            }
        }

        replyHandler = TestReplyHandler()
        sendRequest = mockk {
            coEvery { this@mockk(any()) } answers { Result.success(replyHandler) }
        }
        client = CommandRunnerClient(mockk(), ReporterCreatesPipes, inputStreamFactory)
    }

    @Test
    fun handleFailureFromBlock() {
        val failure = Result.failure(Exception(""))
        coEvery { block(any()) } returns failure
        runTest {
            assertEquals(failure, client.run(block, sendRequest))
        }
    }

    @Test
    fun handleFailureFromSendRequest() {
        val failure = Result.failure(Exception(""))
        coEvery { sendRequest(any()) } answers { failure }
        runTest {
            assertEquals(failure, client.run(block, sendRequest))
        }
    }

    @Test
    fun handleUnexpectedContinueMessage() {
        assertNotNull(replyHandler)
        replyHandler.replyChannel.trySend(ErrorResponse("Uh-oh"))
        runTest {
            assertEquals(
                Result.success(true),
                client.run(block, sendRequest),
            )
        }
    }

    @Test
    fun handleUnexpectedResultResponse() {
        replyHandler.replyChannel.trySend(RunCommandContinue(pfd = mockWriteFd))
        replyHandler.replyChannel.trySend(ErrorResponse("Uh-oh"))
        runTest {
            assertEquals(
                Result.success(true),
                client.run(block, sendRequest),
            )
        }
        assertTrue(response?.isFailure ?: false)
        assertThrows<TimeoutCancellationException> {
            throw response?.getError()!!
        }
    }

    @Test
    fun handleNullPfdResponse() {
        replyHandler.replyChannel.trySend(RunCommandContinue(pfd = null))
        replyHandler.replyChannel.trySend(RunCommandResponse(exitCode = 123, didTimeout = false))
        runTest {
            assertEquals(
                Result.success(true),
                client.run(block, sendRequest),
            )
        }
        assertTrue(response?.isFailure ?: false)
        assertThrows<CancellationException> {
            throw response?.getError()!!
        }
    }

    @Test
    fun happyPath() {
        replyHandler.replyChannel.trySend(RunCommandContinue(pfd = mockWriteFd))
        replyHandler.replyChannel.trySend(RunCommandResponse(exitCode = 123, didTimeout = false))
        runTest {
            assertEquals(
                Result.success(true),
                client.run(block, sendRequest),
            )
        }
        verify { gotInputStream(Result.success(mockInputStream)) }
        assertEquals(Result.success(RunCommandResponse(exitCode = 123, didTimeout = false)), response)
    }

    @Test
    fun legacyHappyPath() {
        client = CommandRunnerClient(mockk(), BortCreatesPipes(legacyMockInputStream, legacyMockWriteFd))

        replyHandler.replyChannel.trySend(RunCommandContinue(pfd = null))
        replyHandler.replyChannel.trySend(RunCommandResponse(exitCode = 123, didTimeout = false))
        runTest {
            assertEquals(
                Result.success(true),
                client.run(block, sendRequest),
            )
        }
        assertEquals(Result.success(RunCommandResponse(exitCode = 123, didTimeout = false)), response)
        coVerifyOrder {
            // The writeFd must be closed after the continue message has been received, see comment in run()
            legacyMockWriteFd.close()
            gotInputStream(Result.success(legacyMockInputStream))
            legacyMockInputStream.close()
        }
    }
}
