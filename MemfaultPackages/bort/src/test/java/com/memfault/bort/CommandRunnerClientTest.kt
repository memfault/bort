package com.memfault.bort

import android.os.ParcelFileDescriptor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
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
import java.io.FileInputStream
import java.io.InputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@ExperimentalCoroutinesApi
class CommandRunnerClientTest {
    lateinit var mockInputStream: FileInputStream
    lateinit var mockWriteFd: ParcelFileDescriptor

    lateinit var client: CommandRunnerClient

    lateinit var block: suspend (CommandRunnerClient.Invocation) -> StdResult<Boolean>
    lateinit var sendRequest:
        suspend (CommandRunnerOptions) -> StdResult<ServiceMessageReplyHandler<ReporterServiceMessage>>
    lateinit var replyHandler: ServiceMessageReplyHandler<ReporterServiceMessage>
    lateinit var gotInputStream: (StdResult<InputStream>) -> Unit

    var response: StdResult<RunCommandResponse>? = null

    @BeforeEach
    fun setUp() {
        mockInputStream = mockk(relaxed = true)
        mockWriteFd = mockk(relaxed = true)
        gotInputStream = mockk(relaxed = true)

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
        client = CommandRunnerClient(mockk(), mockInputStream, mockWriteFd)
    }

    private fun verifyCalls(runOk: Boolean) {
        if (runOk) {
            coVerifyOrder {
                // The writeFd must be closed after the continue message has been received, see comment in run()
                mockWriteFd.close()
                gotInputStream(Result.success(mockInputStream))
                mockInputStream.close()
            }
        } else {
            verify {
                mockWriteFd.close()
                mockInputStream.close()
            }
        }
    }

    @Test
    fun handleFailureFromBlock() {
        val failure = Result.failure(Exception(""))
        coEvery { block(any()) } returns failure
        runBlockingTest {
            assertEquals(failure, client.run(block, sendRequest))
        }
        verifyCalls(runOk = false)
    }

    @Test
    fun handleFailureFromSendRequest() {
        val failure = Result.failure(Exception(""))
        coEvery { sendRequest(any()) } answers { failure }
        runBlockingTest {
            assertEquals(failure, client.run(block, sendRequest))
        }
        verifyCalls(runOk = false)
    }

    @Test
    fun handleUnexpectedContinueMessage() {
        assertNotNull(replyHandler)
        replyHandler.replyChannel.trySend(ErrorResponse("Uh-oh"))
        runBlockingTest {
            assertEquals(
                Result.success(true),
                client.run(block, sendRequest)
            )
        }
        verifyCalls(runOk = false)
    }

    @Test
    fun handleUnexpectedResultResponse() {
        replyHandler.replyChannel.trySend(RunCommandContinue())
        replyHandler.replyChannel.trySend(ErrorResponse("Uh-oh"))
        runBlockingTest {
            assertEquals(
                Result.success(true),
                client.run(block, sendRequest)
            )
        }
        verifyCalls(runOk = true)
        assertTrue(response?.isFailure ?: false)
        assertThrows<TimeoutCancellationException> {
            throw response?.getError()!!
        }
    }

    @Test
    fun happyPath() {
        replyHandler.replyChannel.trySend(RunCommandContinue())
        replyHandler.replyChannel.trySend(RunCommandResponse(exitCode = 123, didTimeout = false))
        runBlockingTest {
            assertEquals(
                Result.success(true),
                client.run(block, sendRequest)
            )
        }
        verifyCalls(runOk = true)
        assertEquals(Result.success(RunCommandResponse(exitCode = 123, didTimeout = false)), response)
    }
}
