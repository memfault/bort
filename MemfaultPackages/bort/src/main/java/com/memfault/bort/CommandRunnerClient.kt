package com.memfault.bort

import android.os.ParcelFileDescriptor
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.runCatching
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.RunCommandContinue
import com.memfault.bort.shared.RunCommandResponse
import com.memfault.bort.shared.ServiceMessageReplyHandler
import com.memfault.bort.shared.result.StdResult
import java.io.Closeable
import java.io.FileInputStream
import kotlin.time.Duration
import kotlin.time.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private val DEFAULT_INPUT_STREAM_TIMEOUT = 5.seconds
private val DEFAULT_RESPONSE_TIMEOUT = 5.seconds

interface CommandRunnerClientFactory {
    fun create(
        mode: CommandRunnerClient.StdErrMode = CommandRunnerClient.StdErrMode.NULL,
        timeout: Duration = CommandRunnerOptions.DEFAULT_TIMEOUT
    ): CommandRunnerClient
}

class CommandRunnerClient(
    val options: CommandRunnerOptions,
    private val out: FileInputStream,
    private var writeFd: ParcelFileDescriptor?
) : Closeable {
    private val inputStream = CompletableDeferred<FileInputStream>()
    private val response = CompletableDeferred<RunCommandResponse>()

    inner class Invocation {
        suspend fun awaitInputStream(timeout: Duration = DEFAULT_INPUT_STREAM_TIMEOUT) =
            runCatching { withTimeout(timeout) { inputStream.await() } }
        suspend fun awaitResponse(timeout: Duration = DEFAULT_RESPONSE_TIMEOUT) =
            runCatching { withTimeout(timeout) { response.await() } }
    }

    companion object RealFactory : CommandRunnerClientFactory {
        override fun create(
            mode: StdErrMode,
            timeout: Duration
        ): CommandRunnerClient {
            val (readFd, writeFd) = ParcelFileDescriptor.createPipe()
            return CommandRunnerClient(
                CommandRunnerOptions(
                    writeFd, mode == StdErrMode.REDIRECT, timeout
                ),
                ParcelFileDescriptor.AutoCloseInputStream(readFd),
                writeFd
            )
        }
    }

    override fun close() {
        writeFd?.close()
        out.close()
    }

    private suspend fun consume(channel: Channel<ReporterServiceMessage>) {
        for (message in channel) {
            when (message) {
                is RunCommandContinue -> {
                    /**
                     * This deals with a peculiarity of ParcelFileDescriptor.createPipe:
                     * if the write end PFD of the pipe is closed before it is sent to the other process,
                     * an exception will happen when trying to send it over, while parcelling the PFD.
                     * If the PFD is *not* closed explicitly (or references to it still exist), reading
                     * from the input stream will block indefinitely!
                     */
                    writeFd?.close()
                    writeFd = null
                    inputStream.complete(out)
                }
                is RunCommandResponse -> response.complete(message)
                is ErrorResponse -> Logger.e("Error response: ${message.error}")
                else -> Logger.e("Unexpected response: $message")
            }
        }

        inputStream.completeExceptionally(CancellationException())
        response.completeExceptionally(CancellationException())
    }

    suspend fun <R> run(
        block: suspend (Invocation) -> StdResult<R>,
        sendRequest: suspend (CommandRunnerOptions) -> StdResult<ServiceMessageReplyHandler<ReporterServiceMessage>>
    ): StdResult<R> =
        use {
            sendRequest(options).andThen { replyHandler ->
                coroutineScope {
                    launch { consume(replyHandler.replyChannel) }
                    block(Invocation()).also { coroutineContext.cancelChildren() }
                }
            }
        }

    enum class StdErrMode {
        NULL,
        REDIRECT,
    }
}
